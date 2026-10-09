package com.thuvstu.hayatemod.core.content;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.thuvstu.hayatemod.core.validate.DescriptionValidator;
import com.thuvstu.hayatemod.core.validate.Issue;
import com.thuvstu.hayatemod.core.validate.Validator;

/** Parse, validate references/rules, and generate descriptions before publication (§6.2). */
public final class ContentPipeline {
    private ContentPipeline() {
    }

    public record Result(ContentSet set, List<ContentError> loaderErrors, List<Issue> issues) {
        public Result {
            loaderErrors = List.copyOf(loaderErrors);
            issues = List.copyOf(issues);
        }

        public boolean ok() {
            return loaderErrors.isEmpty()
                    && issues.stream().noneMatch(i -> i.severity() == Issue.Severity.ERROR);
        }
    }

    public static Result load(Path root) {
        var pack = ContentPack.load(root);
        List<Issue> issues = new ArrayList<>();
        // Invalid shapes must not reach validators that expect a parsed model.
        if (pack.ok()) {
            issues.addAll(Validator.validate(pack.set()));
            issues.addAll(DescriptionValidator.validate(pack.set()));
        }
        return new Result(pack.set(), pack.errors(), issues);
    }
}

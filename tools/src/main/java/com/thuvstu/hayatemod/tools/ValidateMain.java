package com.thuvstu.hayatemod.tools;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.thuvstu.hayatemod.core.content.ContentPipeline;
import com.thuvstu.hayatemod.core.validate.Issue;

/**
 * {@code validateContent}: loader errors + validator issues + description-template
 * gaps (V10). Exits 1 when any ERROR exists. Usage: {@code validateContent <contentDir>}.
 */
public final class ValidateMain {
    private ValidateMain() {
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("usage: validateContent <contentDir>");
            System.exit(2);
        }
        ContentPipeline.Result pack = ContentPipeline.load(Path.of(args[0]));
        List<String> loaderErrors = new ArrayList<>();
        for (var e : pack.loaderErrors()) {
            loaderErrors.add("[LOAD-ERROR] " + e);
        }
        List<Issue> issues = pack.issues();
        for (String s : loaderErrors) {
            System.out.println(s);
        }
        for (Issue i : issues) {
            System.out.println(i);
        }
        long errors = loaderErrors.size()
                + issues.stream().filter(i -> i.severity() == Issue.Severity.ERROR).count();
        System.out.println("loaderErrors=" + loaderErrors.size() + " issues=" + issues.size()
                + " errors=" + errors);
        if (errors > 0) {
            System.exit(1);
        }
    }

}

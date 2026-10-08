package com.thuvstu.hayatemod.tools;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.thuvstu.hayatemod.core.content.ContentPack;
import com.thuvstu.hayatemod.core.content.ContentSet;
import com.thuvstu.hayatemod.core.describe.Describer;
import com.thuvstu.hayatemod.core.describe.MissingTemplateException;
import com.thuvstu.hayatemod.core.validate.Issue;
import com.thuvstu.hayatemod.core.validate.Validator;

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
        ContentPack.LoadedPack pack = ContentPack.load(Path.of(args[0]));
        List<String> loaderErrors = new ArrayList<>();
        for (var e : pack.errors()) {
            loaderErrors.add("[LOAD-ERROR] " + e);
        }
        List<Issue> issues = new ArrayList<>();
        if (pack.ok()) {
            issues.addAll(Validator.validate(pack.set()));
            issues.addAll(describeDryRun(pack.set()));
        }
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

    static List<Issue> describeDryRun(ContentSet set) {
        List<Issue> out = new ArrayList<>();
        for (var w : set.weapons().values()) {
            try {
                Describer.describeWeapon(w);
            } catch (MissingTemplateException e) {
                out.add(new Issue(Issue.Severity.ERROR, "V10", "weapons:" + w.id(), e.getMessage()));
            }
        }
        for (var r : set.runes().values()) {
            try {
                Describer.describeRune(r);
            } catch (MissingTemplateException e) {
                out.add(new Issue(Issue.Severity.ERROR, "V10", "runes:" + r.id(), e.getMessage()));
            }
        }
        for (var k : set.keystones().values()) {
            try {
                Describer.describeKeystone(k);
            } catch (MissingTemplateException e) {
                out.add(new Issue(Issue.Severity.ERROR, "V10", "keystones:" + k.id(), e.getMessage()));
            }
        }
        return out;
    }
}

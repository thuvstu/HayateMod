package com.thuvstu.hayatemod.core.validate;

import java.util.ArrayList;
import java.util.List;

import com.thuvstu.hayatemod.core.content.ContentSet;
import com.thuvstu.hayatemod.core.describe.Describer;
import com.thuvstu.hayatemod.core.describe.MissingTemplateException;

/** Shared V10 gate for the CLI and game; never publish undescribable content. */
public final class DescriptionValidator {
    private DescriptionValidator() {
    }

    public static List<Issue> validate(ContentSet set) {
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

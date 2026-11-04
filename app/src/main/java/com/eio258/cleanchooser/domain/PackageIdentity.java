package com.eio258.cleanchooser.domain;

import java.util.regex.Pattern;

/** Shared validation for package identities persisted by manager/runtime configuration. */
public final class PackageIdentity {
    private static final Pattern PACKAGE = Pattern.compile(
        "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*"
    );

    /** The framework package "android" has no dot but is referenced by name in configs. */
    private static final String FRAMEWORK_PACKAGE = "android";

    private PackageIdentity() {}

    public static boolean valid(String value) {
        return value != null && !value.isEmpty() && value.length() <= 255 &&
            PACKAGE.matcher(value).matches() &&
            (value.equals(FRAMEWORK_PACKAGE) || value.indexOf('.') >= 0);
    }
}

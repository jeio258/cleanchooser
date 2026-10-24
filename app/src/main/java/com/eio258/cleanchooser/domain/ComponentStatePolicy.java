package com.eio258.cleanchooser.domain;

/** No stored blacklist: system override + manifest default determine the displayed state. */
public final class ComponentStatePolicy {
    private ComponentStatePolicy() {}
    public static Boolean enabled(int override, boolean manifestEnabled) {
        switch (override) {
            case 0: return manifestEnabled;
            case 1: return true;
            case 2: case 3: case 4: return false;
            default: return null;
        }
    }
    public static boolean valid(String pkg, String cls, int user) {
        return user >= 0 && user <= 21474 && pkg != null && cls != null &&
            pkg.length() <= 255 && cls.length() <= 512 &&
            pkg.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+") &&
            cls.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+");
    }
    public static String commandLine(String pkg, String cls, int user, boolean enable) {
        if (!valid(pkg, cls, user)) throw new IllegalArgumentException("Invalid component identity");
        // Always a fully-qualified component, never a package-wide enable/disable.
        return "/system/bin/pm " + (enable ? "enable" : "disable") +
            " --user " + user + " '" + pkg + "/" + cls + "'";
    }
    public static String command(String pkg, String cls, int user, boolean enable) {
        return "test \"$(id -u)\" = 0 || exit 77\nexec " + commandLine(pkg, cls, user, enable);
    }

    /**
     * Clears the enabled-state override so the manifest/system-declared state applies again.
     * Unlike {@code pm enable} this never forces a component on: components that shipped disabled
     * stay disabled, which is exactly what "restore the original configuration" means.
     */
    public static String defaultStateCommand(String pkg, String cls, int user) {
        if (!valid(pkg, cls, user)) throw new IllegalArgumentException("Invalid component identity");
        return "test \"$(id -u)\" = 0 || exit 77\nexec cmd package default-state --user " + user +
            " '" + pkg + "/" + cls + "'";
    }

    /**
     * Restores a specific prior override (the value PackageManager reported before this app
     * touched the component), so a restore rolls back to what actually was, instead of forcing
     * everything to the manifest default.
     */
    public static String stateCommand(String pkg, String cls, int user, int override) {
        if (!valid(pkg, cls, user)) throw new IllegalArgumentException("Invalid component identity");
        final String verb;
        if (override == 1) verb = "enable";
        else if (override == 2) verb = "disable";
        else if (override == 3) verb = "disable-user";
        else if (override == 4) verb = "disable-until-used";
        else throw new IllegalArgumentException("Unsupported prior override: " + override);
        return "test \"$(id -u)\" = 0 || exit 77\nexec pm " + verb + " --user " + user +
            " '" + pkg + "/" + cls + "'";
    }
}

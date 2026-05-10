package dev.asante.matheaufgabenmod.generator;

public enum CarryMode {
    YES, NO, MIXED;

    public static CarryMode parse(String raw) {
        return switch (raw) {
            case "yes" -> YES;
            case "no" -> NO;
            case "mixed" -> MIXED;
            default -> throw new ConfigException(
                    "plus: carry must be yes|no|mixed, got '" + raw + "'");
        };
    }
}

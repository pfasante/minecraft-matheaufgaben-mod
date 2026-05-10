package dev.asante.matheaufgabenmod.generator;

public enum BorrowMode {
    YES, NO, MIXED;

    public static BorrowMode parse(String raw) {
        return switch (raw) {
            case "yes" -> YES;
            case "no" -> NO;
            case "mixed" -> MIXED;
            default -> throw new ConfigException(
                    "minus: borrow must be yes|no|mixed, got '" + raw + "'");
        };
    }
}

package dev.asante.matheaufgabenmod.config;

import java.util.List;

public record ModConfig(int intervalMinutes, List<String> sectionSpecs) {

    public static final ModConfig DEFAULT = new ModConfig(
            5,
            List.of(
                    "plus:range=100,count=1,carry=mixed",
                    "minus:range=100,count=1,borrow=mixed",
                    "einmaleins:rows=2-9,count=1",
                    "division:divisor=2-9,count=1"
            )
    );
}

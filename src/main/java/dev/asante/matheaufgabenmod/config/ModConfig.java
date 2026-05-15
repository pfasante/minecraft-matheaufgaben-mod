package dev.asante.matheaufgabenmod.config;

import java.util.List;

/**
 * Top-level mod configuration loaded from {@code <minecraft>/config/matheaufgabenmod.json}.
 *
 * @param intervalMinutes   minutes of active play between prompt interruptions.
 * @param tasksPerIteration how many problems the kid must solve in a row when a prompt
 *                          fires before play resumes. Each task picks a fresh random
 *                          section spec, so the sequence is a mix of problem types.
 * @param sectionSpecs      the {@code type:k=v,...} strings each prompt picks from.
 */
public record ModConfig(int intervalMinutes, int tasksPerIteration, List<String> sectionSpecs) {

    public static final ModConfig DEFAULT = new ModConfig(
            5,
            1,
            List.of(
                    "plus:range=100,count=1,carry=mixed",
                    "minus:range=100,count=1,borrow=mixed",
                    "einmaleins:rows=2-9,count=1",
                    "division:divisor=2-9,count=1"
            )
    );
}

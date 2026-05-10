// src/main/java/dev/asante/matheaufgabenmod/generator/Problem.java
package dev.asante.matheaufgabenmod.generator;

/**
 * Immutable math problem with its prompt and the canonical answer string.
 *
 * <p>Both fields are exactly what the player sees / types — no metadata, no
 * formatting indirection. {@code answer} is a string (not int) to handle
 * "3 R 1" remainder-form division answers and any future formats uniformly.
 */
public record Problem(String prompt, String answer) {}

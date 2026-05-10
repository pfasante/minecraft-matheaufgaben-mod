package dev.asante.matheaufgabenmod.timer;

import dev.asante.matheaufgabenmod.config.ModConfig;
import dev.asante.matheaufgabenmod.generator.Problem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class PromptSchedulerTest {

    /** Test double that records every prompt opened. */
    private static final class FakeClient implements ClientSurface {
        boolean hasWorld = true;
        boolean isPaused = false;
        boolean currentIsPrompt = false;
        final List<Problem> opened = new ArrayList<>();
        @Override public boolean hasWorld() { return hasWorld; }
        @Override public boolean isPaused() { return isPaused; }
        @Override public boolean currentScreenIsPrompt() { return currentIsPrompt; }
        @Override public void openPromptScreen(Problem p) { opened.add(p); currentIsPrompt = true; }
    }

    private static ModConfig oneMinute(String... specs) {
        return new ModConfig(1, List.of(specs));
    }

    private static void tickN(PromptScheduler sched, FakeClient client, int n) {
        for (int i = 0; i < n; i++) sched.onTick(client);
    }

    @Test
    void firesAfterIntervalElapsed() {
        FakeClient client = new FakeClient();
        ModConfig cfg = oneMinute("plus:range=10,count=1");
        PromptScheduler sched = new PromptScheduler(cfg, new Random(42));
        // 1 minute = 60 * 20 = 1200 ticks
        tickN(sched, client, 1200);
        assertEquals(1, client.opened.size());
    }

    @Test
    void doesNotFireBeforeInterval() {
        FakeClient client = new FakeClient();
        ModConfig cfg = oneMinute("plus:range=10,count=1");
        PromptScheduler sched = new PromptScheduler(cfg, new Random(42));
        tickN(sched, client, 1199);
        assertEquals(0, client.opened.size());
    }

    @Test
    void doesNotAdvanceWhilePaused() {
        FakeClient client = new FakeClient();
        client.isPaused = true;
        ModConfig cfg = oneMinute("plus:range=10,count=1");
        PromptScheduler sched = new PromptScheduler(cfg, new Random(42));
        tickN(sched, client, 5000);
        assertEquals(0, client.opened.size());
    }

    @Test
    void resetsTimerWhenWorldUnloads() {
        FakeClient client = new FakeClient();
        ModConfig cfg = oneMinute("plus:range=10,count=1");
        PromptScheduler sched = new PromptScheduler(cfg, new Random(42));
        tickN(sched, client, 600);   // halfway
        client.hasWorld = false;
        sched.onTick(client);        // world gone — counter reset
        client.hasWorld = true;
        tickN(sched, client, 1199);  // not enough to fire from a fresh start
        assertEquals(0, client.opened.size());
    }

    @Test
    void doesNotDoubleOpenWhilePromptUp() {
        FakeClient client = new FakeClient();
        ModConfig cfg = oneMinute("plus:range=10,count=1");
        PromptScheduler sched = new PromptScheduler(cfg, new Random(42));
        client.currentIsPrompt = true;  // a prompt is already up
        tickN(sched, client, 5000);
        assertEquals(0, client.opened.size());
    }

    @Test
    void picksFromConfiguredSpecs() {
        FakeClient client = new FakeClient();
        ModConfig cfg = oneMinute("plus:range=10,count=1");
        PromptScheduler sched = new PromptScheduler(cfg, new Random(42));
        tickN(sched, client, 1200);
        Problem p = client.opened.get(0);
        assertTrue(p.prompt().contains(" + "), "expected a plus problem, got " + p.prompt());
    }

    @Test
    void firesAgainAfterAnotherInterval() {
        FakeClient client = new FakeClient();
        ModConfig cfg = oneMinute("plus:range=10,count=1");
        PromptScheduler sched = new PromptScheduler(cfg, new Random(42));
        tickN(sched, client, 1200);
        client.currentIsPrompt = false;  // user closed the prompt
        tickN(sched, client, 1200);
        assertEquals(2, client.opened.size());
    }
}

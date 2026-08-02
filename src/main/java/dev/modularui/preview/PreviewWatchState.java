package dev.modularui.preview;

import java.time.Duration;

final class PreviewWatchState {

    private final long debounceNanos;
    private PreviewInputSnapshot observed;
    private long rebuildAtNanos = Long.MAX_VALUE;

    PreviewWatchState(PreviewInputSnapshot initial, Duration debounce) {
        if (debounce.isNegative()) throw new IllegalArgumentException("Preview watch debounce cannot be negative");
        observed = initial;
        debounceNanos = debounce.toNanos();
    }

    void observe(PreviewInputSnapshot current, long nowNanos) {
        if (current.equals(observed)) return;
        observed = current;
        rebuildAtNanos = nowNanos + debounceNanos;
    }

    boolean rebuildReady(long nowNanos) {
        if (nowNanos < rebuildAtNanos) return false;
        rebuildAtNanos = Long.MAX_VALUE;
        return true;
    }
}

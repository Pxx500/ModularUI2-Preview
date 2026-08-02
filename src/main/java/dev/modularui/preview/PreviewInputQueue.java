package dev.modularui.preview;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

final class PreviewInputQueue {

    private final Deque<PreviewInput> inputs = new ArrayDeque<>();

    synchronized void move(int x, int y) {
        if (inputs.peekLast() instanceof PreviewInput.Move) inputs.removeLast();
        add(new PreviewInput.Move(x, y));
    }

    synchronized void press(MouseButton button) {
        add(new PreviewInput.Press(button));
    }

    synchronized void release(MouseButton button) {
        add(new PreviewInput.Release(button));
    }

    synchronized void scroll(ScrollDirection direction, int amount) {
        add(new PreviewInput.Scroll(direction, amount));
    }

    synchronized void stop() {
        add(PreviewInput.Stop.INSTANCE);
    }

    synchronized PreviewInput take() throws InterruptedException {
        while (inputs.isEmpty()) wait();
        return inputs.removeFirst();
    }

    synchronized PreviewInput poll(long timeoutMillis) throws InterruptedException {
        if (timeoutMillis < 0) throw new IllegalArgumentException("Input poll timeout cannot be negative");
        long remainingNanos = timeoutMillis * 1_000_000;
        long deadline = System.nanoTime() + remainingNanos;
        while (inputs.isEmpty() && remainingNanos > 0) {
            long millis = remainingNanos / 1_000_000;
            int nanos = (int) (remainingNanos % 1_000_000);
            wait(millis, nanos);
            remainingNanos = deadline - System.nanoTime();
        }
        return inputs.isEmpty() ? null : inputs.removeFirst();
    }

    synchronized List<PreviewInput> drain() {
        List<PreviewInput> drained = new ArrayList<>(inputs);
        inputs.clear();
        return drained;
    }

    private void add(PreviewInput input) {
        inputs.addLast(input);
        notifyAll();
    }
}

package com.cleanroommc.modularui.widgets;

import java.util.function.IntSupplier;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.widget.Widget;
import dev.modularui.preview.PreviewDrawContext;

public class TextWidget<W extends TextWidget<W>> extends Widget<W> {

    private static final int DEFAULT_COLOR = 0xFFFFFFFF;
    private static final int LINE_HEIGHT = 9;

    private final IKey key;
    private IntSupplier color;
    private Boolean shadow;

    public TextWidget(String text) {
        this(IKey.str(text));
    }

    public TextWidget(IKey key) {
        this.key = key;
    }

    public String previewText() {
        return key.getFormatted();
    }

    public W color(int value) {
        color = () -> value;
        return getThis();
    }

    public W color(IntSupplier value) {
        color = value;
        return getThis();
    }

    public W shadow(Boolean value) {
        shadow = value;
        return getThis();
    }

    public int previewColor() {
        return color == null ? DEFAULT_COLOR : color.getAsInt();
    }

    public boolean previewShadow() {
        return Boolean.TRUE.equals(shadow);
    }

    @Override
    protected int previewDefaultWidth() {
        return Math.max(1, PreviewDrawContext.stringWidth(previewText()));
    }

    @Override
    protected int previewDefaultHeight() {
        return LINE_HEIGHT;
    }
}

package com.cleanroommc.modularui.widget;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.IPositioned;
import com.cleanroommc.modularui.api.widget.IWidget;

/**
 * Preview-side subset of ModularUI2's fluent widget contract.
 */
public class Widget<W extends Widget<W>> implements IWidget, IPositioned<W> {

    private Integer left;
    private Integer right;
    private Integer top;
    private Integer bottom;
    private Integer width;
    private Integer height;
    private Float relativeWidth;
    private Float relativeHeight;
    private int relativeWidthOffset;
    private int relativeHeightOffset;
    private List<IDrawable> backgrounds = List.of();
    private List<IDrawable> overlays = List.of();

    @SuppressWarnings("unchecked")
    public final W getThis() {
        return (W) this;
    }

    @Override
    public W pos(int x, int y) {
        return left(x).top(y);
    }

    @Override
    public W size(int width, int height) {
        return width(width).height(height);
    }

    @Override
    public W left(int value) {
        left = value;
        right = null;
        return getThis();
    }

    @Override
    public W right(int value) {
        right = value;
        left = null;
        return getThis();
    }

    @Override
    public W top(int value) {
        top = value;
        bottom = null;
        return getThis();
    }

    @Override
    public W bottom(int value) {
        bottom = value;
        top = null;
        return getThis();
    }

    @Override
    public W width(int value) {
        width = value;
        relativeWidth = null;
        return getThis();
    }

    @Override
    public W height(int value) {
        height = value;
        relativeHeight = null;
        return getThis();
    }

    @Override
    public W widthRel(float value) {
        return widthRelOffset(value, 0);
    }

    @Override
    public W widthRelOffset(float value, int offset) {
        relativeWidth = value;
        relativeWidthOffset = offset;
        width = null;
        return getThis();
    }

    @Override
    public W heightRel(float value) {
        return heightRelOffset(value, 0);
    }

    @Override
    public W heightRelOffset(float value, int offset) {
        relativeHeight = value;
        relativeHeightOffset = offset;
        height = null;
        return getThis();
    }

    public W background(IDrawable... drawables) {
        backgrounds = copyDrawables(drawables);
        return getThis();
    }

    public W overlay(IDrawable... drawables) {
        overlays = copyDrawables(drawables);
        return getThis();
    }

    public W setEnabledIf(Predicate<W> enabled) {
        return getThis();
    }

    public int previewX(int parentWidth, int resolvedWidth) {
        if (right != null) return parentWidth - right - resolvedWidth;
        return left == null ? 0 : left;
    }

    public int previewY(int parentHeight, int resolvedHeight) {
        if (bottom != null) return parentHeight - bottom - resolvedHeight;
        return top == null ? 0 : top;
    }

    public int previewWidth(int parentWidth) {
        if (relativeWidth != null) return Math.max(0, Math.round(parentWidth * relativeWidth) + relativeWidthOffset);
        return width == null ? previewDefaultWidth() : width;
    }

    public int previewHeight(int parentHeight) {
        if (relativeHeight != null)
            return Math.max(0, Math.round(parentHeight * relativeHeight) + relativeHeightOffset);
        return height == null ? previewDefaultHeight() : height;
    }

    protected int previewDefaultWidth() {
        return 0;
    }

    protected int previewDefaultHeight() {
        return 0;
    }

    public List<IDrawable> previewBackgrounds() {
        return backgrounds;
    }

    public List<IDrawable> previewOverlays() {
        return overlays;
    }

    private List<IDrawable> copyDrawables(IDrawable[] drawables) {
        return drawables == null || drawables.length == 0 ? List.of() : List.copyOf(Arrays.asList(drawables));
    }
}

package com.cleanroommc.modularui.screen;

import com.cleanroommc.modularui.widget.ParentWidget;

public class ModularPanel extends ParentWidget<ModularPanel> {

    private static final int DEFAULT_WIDTH = 176;
    private static final int DEFAULT_HEIGHT = 166;

    private final String name;

    public ModularPanel(String name) {
        this.name = name;
    }

    public static ModularPanel defaultPanel(String name) {
        return defaultPanel(name, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public static ModularPanel defaultPanel(String name, int width, int height) {
        return new ModularPanel(name).size(width, height);
    }

    public String getName() {
        return name;
    }
}

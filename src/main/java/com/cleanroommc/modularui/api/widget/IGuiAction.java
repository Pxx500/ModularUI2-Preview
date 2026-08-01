package com.cleanroommc.modularui.api.widget;

public interface IGuiAction {

    @FunctionalInterface
    interface MousePressed extends IGuiAction {

        boolean press(int mouseButton);
    }
}

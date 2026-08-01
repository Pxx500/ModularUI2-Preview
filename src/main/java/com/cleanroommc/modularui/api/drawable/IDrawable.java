package com.cleanroommc.modularui.api.drawable;

import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.widget.Widget;

@FunctionalInterface
public interface IDrawable {

    IDrawable EMPTY = (context, x, y, width, height, theme) -> {};

    void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme theme);

    default Widget<?> asWidget() {
        return new DrawableWidget(this);
    }

    final class DrawableWidget extends Widget<DrawableWidget> {

        private final IDrawable drawable;

        public DrawableWidget(IDrawable drawable) {
            this.drawable = drawable;
        }

        public IDrawable previewDrawable() {
            return drawable;
        }
    }
}

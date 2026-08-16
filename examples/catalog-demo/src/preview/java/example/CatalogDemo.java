package example;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import dev.modularui.preview.PreviewCatalog;
import dev.modularui.preview.PreviewEntrypoint;
import dev.modularui.preview.PreviewScenario;
import java.util.List;

public final class CatalogDemo implements PreviewCatalog {

    @Override
    public List<PreviewScenario> scenarios() {
        return List.of(PreviewScenario.define(
            "demo/default",
            "clickable catalog example",
            "demo",
            CatalogDemo.class,
            DemoEntrypoint::new).tags("default", "interaction"));
    }

    private static final class DemoEntrypoint implements PreviewEntrypoint {

        @Override
        public Class<?> previewedClass() {
            return CatalogDemo.class;
        }

        @Override
        public Object createPanel(Context context) {
            return ModularPanel.defaultPanel("catalog_demo", 176, 90)
                .child(new ButtonWidget<>()
                    .pos(58, 32)
                    .size(60, 24)
                    .onMousePressed(button -> true)
                    .child(new TextWidget<>("Click me").coverChildren()));
        }
    }
}

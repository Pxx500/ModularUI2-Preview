package dev.modularui.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreviewRenderLimitationsTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void reportsSkippedItemIconsAndUnsupportedOpenGlWithoutDuplicatingWarnings() throws Exception {
        Path project = project("""
            for (int index = 0; index < 3; index++) {
                ItemStack stack = new ItemStack(new Item());
                RenderItem.getInstance().renderItemAndEffectIntoGUI(null, null, stack, x, y);
                RenderItem.getInstance().renderItemOverlayIntoGUI(null, null, stack, x, y, "2");
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, 1);
                OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 0, 1);
                GL11.glRotatef(30, 1, 0, 0);
                GL11.glFrustum(-1, 1, -1, 1, 1, 100);
            }
            """);

        try (PreviewSession session = PreviewEngine.open(project, "example.LimitationsPreview",
            new PreviewScreen(800, 600, 2))) {
            PreviewResult result = session.render();

            assertEquals(Set.of("unsupported.item-rendering", "unsupported.blend-function",
                "unsupported.rotation-axis", "unsupported.perspective-projection"), result.warnings().stream()
                    .map(warning -> warning.split(":", 2)[0])
                    .collect(Collectors.toSet()));
            assertEquals(4, result.warnings().size());
            assertEquals(result.warnings(), session.render().warnings());
        }
    }

    @Test
    void keepsOrdinaryGuiBlendingAndEmptyItemSlotsQuiet() throws Exception {
        Path project = project("""
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                1, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glRotatef(15, 0, 0, 1);
            GL11.glRotatef(0, 1, 0, 0);
            RenderItem.getInstance().renderItemAndEffectIntoGUI(null, null, null, x, y);
            RenderItem.getInstance().renderItemOverlayIntoGUI(null, null, new ItemStack(), x, y, null);
            """);

        try (PreviewSession session = PreviewEngine.open(project, "example.LimitationsPreview",
            new PreviewScreen(800, 600, 2))) {
            assertTrue(session.render().warnings().isEmpty());
        }
    }

    private Path project(String drawing) throws Exception {
        Path project = temporaryDirectory.resolve("preview");
        Path source = project.resolve("src/preview/java/example/LimitationsPreview.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package example;

            import com.cleanroommc.modularui.api.drawable.IDrawable;
            import com.cleanroommc.modularui.screen.ModularPanel;
            import com.cleanroommc.modularui.widget.Widget;
            import dev.modularui.preview.PreviewEntrypoint;
            import net.minecraft.client.renderer.entity.RenderItem;
            import net.minecraft.client.renderer.OpenGlHelper;
            import net.minecraft.item.Item;
            import net.minecraft.item.ItemStack;
            import org.lwjgl.opengl.GL11;

            public class LimitationsPreview implements PreviewEntrypoint {
                public Object createPanel(PreviewEntrypoint.Context context) {
                    IDrawable drawing = (gui, x, y, width, height, theme) -> {
                        %s
                    };
                    return ModularPanel.defaultPanel("limitations", 100, 80)
                        .child(new Widget<>().size(20).background(drawing));
                }
            }
            """.formatted(drawing));
        return project;
    }
}

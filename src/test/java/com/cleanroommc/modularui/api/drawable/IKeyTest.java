package com.cleanroommc.modularui.api.drawable;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import net.minecraft.util.StatCollector;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class IKeyTest {

    @AfterEach
    void clearTranslations() {
        StatCollector.clearTranslations();
    }

    @Test
    void resolvesLanguageKeysFromTheActivePreviewTranslations() {
        StatCollector.installTranslations(Map.of("example.preview.label", "Translated %s"));

        assertEquals("Translated panel", IKey.lang("example.preview.label", "panel").get());
    }
}

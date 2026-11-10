package lab01.slangdict._24127430.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.swing.KeyStroke;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import lab01.slangdict._24127430.i18n.Messages;

/** Không tạo cửa sổ Swing nào, nên chạy được cả trên máy không có màn hình (CI). */
class FeatureTest {

    @AfterEach
    void restoreLocale() {
        Messages.setLocale(Messages.DEFAULT_LOCALE);
    }

    @Test
    void tenFeaturesNumberedLikeTheAssignment() {
        Feature[] features = Feature.values();
        assertEquals(10, features.length);
        for (int i = 0; i < features.length; i++) {
            assertEquals(i + 1, features[i].number());
        }
    }

    @Test
    void everyCardHasAFeatureAndActionsAreTheDialogFeatures() {
        EnumSet<Feature.Card> used = EnumSet.noneOf(Feature.Card.class);
        for (Feature feature : Feature.values()) {
            if (feature.isScreen()) {
                used.add(feature.card());
            }
        }
        assertEquals(EnumSet.allOf(Feature.Card.class), used);
        for (Feature action : EnumSet.of(Feature.ADD, Feature.EDIT, Feature.DELETE, Feature.RESET)) {
            assertNull(action.card());
        }
        assertEquals(Feature.SEARCH_SLANG.card(), Feature.SEARCH_DEFINITION.card());
    }

    @Test
    void acceleratorsAreUniqueCtrlDigits() {
        Set<KeyStroke> strokes = new HashSet<>();
        for (Feature feature : Feature.values()) {
            assertTrue(strokes.add(feature.accelerator()), "duplicate shortcut for " + feature);
        }
        assertEquals("Ctrl+1", Sidebar.acceleratorText(Feature.SEARCH_SLANG.accelerator()));
        assertEquals("Ctrl+0", Sidebar.acceleratorText(Feature.QUIZ_DEFINITION.accelerator()));
    }

    @Test
    void everyFeatureAndSectionHasTextsInBothLanguages() {
        for (Locale locale : Messages.supportedLocales()) {
            Messages.setLocale(locale);
            for (Feature feature : Feature.values()) {
                assertFalse(Messages.get(feature.titleKey()).startsWith("!"), locale + " " + feature.titleKey());
                assertFalse(Messages.get(feature.descriptionKey()).startsWith("!"), locale + " " + feature.descriptionKey());
            }
            for (Feature.Section section : Feature.Section.values()) {
                assertFalse(Messages.get(section.titleKey()).startsWith("!"), locale + " " + section.titleKey());
            }
        }
    }
}

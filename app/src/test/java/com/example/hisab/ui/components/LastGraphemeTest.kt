package com.example.hisab.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The add-category dialog stores whatever the emoji keyboard inserts, so the one piece of logic
 * between the keyboard and the database is [lastGrapheme]. If it slices mid-emoji the category
 * renders as a replacement box on the dashboard, in history, and in the SMS notification.
 */
class LastGraphemeTest {

    @Test
    fun secondKeyboardTapReplacesTheFirstEmoji() {
        assertEquals("🍕", lastGrapheme("🛒🍕"))
    }

    @Test
    fun aSurrogatePairIsNeverSplit() {
        // takeLast(1) would return the low surrogate alone — an unrenderable half-character.
        assertEquals("🛒", lastGrapheme("🛒"))
        assertEquals(2, lastGrapheme("🛒").length)
    }

    @Test
    fun aVariationSelectorStaysWithItsBaseEmoji() {
        assertEquals("🍽️", lastGrapheme("🍽️"))
    }

    @Test
    fun emptyInputStaysEmpty() {
        assertEquals("", lastGrapheme(""))
    }
}

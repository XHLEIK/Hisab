package com.example.hisab.ui.components.emoji

import org.junit.Assert.*
import org.junit.Test

class EmojiCatalogTest {

    @Test
    fun catalogIsComprehensive_notJustFinanceEmojis() {
        assertTrue("Catalog should have broad coverage", EmojiCatalog.allEmojis.size >= 400)
        val categoriesPresent = EmojiCatalog.byCategory.keys
        assertTrue(categoriesPresent.contains(EmojiCategory.SMILEYS))
        assertTrue(categoriesPresent.contains(EmojiCategory.FOOD))
        assertTrue(categoriesPresent.contains(EmojiCategory.TRAVEL))
        assertTrue(categoriesPresent.contains(EmojiCategory.SYMBOLS))
        assertTrue(categoriesPresent.contains(EmojiCategory.FLAGS))
        // Ensure finance-related emojis are present but not the only ones
        val financeCount = EmojiCatalog.allEmojis.count { it.keywords.any { k -> k in listOf("money", "finance", "bank", "shopping") } }
        assertTrue(financeCount in 10..100)
        assertTrue(financeCount < EmojiCatalog.allEmojis.size / 4)
    }

    @Test
    fun searchFindsExpectedEmojisForCommonTerms() {
        fun assertSearchFinds(term: String, expectedEmoji: String) {
            val results = EmojiCatalog.search(term)
            assertTrue("Search '$term' should find $expectedEmoji", results.any { it.emoji == expectedEmoji })
        }

        assertSearchFinds("food", "🍔")
        assertSearchFinds("money", "💰")
        assertSearchFinds("car", "🚗")
        assertSearchFinds("travel", "✈️")
        assertSearchFinds("heart", "❤️")
        assertSearchFinds("smile", "😊")
        assertSearchFinds("dog", "🐶")
        assertSearchFinds("shopping", "🛒")
        assertSearchFinds("house", "🏠")
        assertSearchFinds("bank", "🏦")
        assertSearchFinds("bill", "🧾")
        assertSearchFinds("game", "🎮")
        assertSearchFinds("music", "🎵")
    }

    @Test
    fun searchIsCaseInsensitiveAndTrimsWhitespace() {
        val lower = EmojiCatalog.search("money")
        val upper = EmojiCatalog.search("MONEY")
        val padded = EmojiCatalog.search("  money  ")
        assertEquals(lower.map { it.emoji }.toSet(), upper.map { it.emoji }.toSet())
        assertEquals(lower.map { it.emoji }.toSet(), padded.map { it.emoji }.toSet())
    }

    @Test
    fun emptyOrBlankSearchReturnsEmptyListForCallerToHandle() {
        assertTrue(EmojiCatalog.search("").isEmpty())
        assertTrue(EmojiCatalog.search("   ").isEmpty())
    }

    @Test
    fun noResultsForNonsenseQuery() {
        val results = EmojiCatalog.search("xyzabc123noemoji")
        assertTrue(results.isEmpty())
    }

    @Test
    fun searchSupportsMultiTermAndRequiresAllTerms() {
        val results = EmojiCatalog.search("red heart")
        // Should find red heart but not every heart
        assertTrue(results.any { it.emoji == "❤️" })
        // Single term broader
        val single = EmojiCatalog.search("heart")
        assertTrue(single.size >= results.size)
    }

    @Test
    fun byCategoryGroupsAreCorrectAndNonEmpty() {
        EmojiCategory.all.forEach { cat ->
            val list = EmojiCatalog.byCategory[cat]
            assertNotNull("Category $cat should have list", list)
            assertTrue("Category ${cat.name} should not be empty", list!!.isNotEmpty())
            list.forEach { entry ->
                assertEquals(cat, entry.category)
            }
        }
    }

    @Test
    fun emojisAreSingleGraphemeAndRenderable() {
        val plate = EmojiCatalog.allEmojis.find { it.emoji == "🍽️" }
        assertNotNull(plate)
        assertEquals("🍽️", plate!!.emoji)

        val flag = EmojiCatalog.allEmojis.find { it.emoji == "🇮🇳" }
        assertNotNull(flag)
        // Flags are 2 regional indicators (2 codepoints) but 1 grapheme cluster
        assertEquals(2, flag!!.emoji.codePointCount(0, flag.emoji.length))
        assertTrue(flag.emoji.length >= 4)
    }

    @Test
    fun searchUsesRealKeywordMappingNotJustEmojiChar() {
        // searching by keyword should find emoji, not requiring user to type emoji char
        val byKeyword = EmojiCatalog.search("shopping")
        assertTrue(byKeyword.any { it.emoji == "🛒" })
        val byChar = EmojiCatalog.search("🛒")
        assertTrue(byChar.any { it.emoji == "🛒" })
        // Both should work
    }

    @Test
    fun recentCategoryIsNotInMainCatalogButHandledSeparately() {
        // Recent is dynamic, not a fixed category in catalog
        assertFalse(EmojiCategory.all.any { it.name == "RECENT" })
    }
}

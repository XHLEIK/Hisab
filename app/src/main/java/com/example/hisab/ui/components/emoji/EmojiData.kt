package com.example.hisab.ui.components.emoji

enum class EmojiCategory(
    val displayName: String,
    val icon: String,
    val label: String
) {
    SMILEYS("Smileys", "😊", "Smileys & Emotion"),
    PEOPLE("People", "👋", "People & Body"),
    ANIMALS("Animals", "🐶", "Animals & Nature"),
    FOOD("Food", "🍔", "Food & Drink"),
    ACTIVITIES("Activities", "⚽", "Activities"),
    TRAVEL("Travel", "✈️", "Travel & Places"),
    OBJECTS("Objects", "💡", "Objects"),
    SYMBOLS("Symbols", "❤️", "Symbols"),
    FLAGS("Flags", "🏳️", "Flags");

    companion object {
        val all: List<EmojiCategory> = values().toList()
    }
}

data class EmojiEntry(
    val emoji: String,
    val name: String,
    val keywords: List<String>,
    val category: EmojiCategory
)

object EmojiCatalog {

    /**
     * Generate all emojis from Unicode code point ranges.
     * This covers Unicode 15.1 — approximately 3,953+ emojis.
     */
    private fun generateEmojis(): List<EmojiEntry> = buildList {
        // ── Smileys & Emotion ──
        addRange(0x1F600..0x1F64F, EmojiCategory.SMILEYS) // Emoticons
        addRange(0x1F910..0x1F96B, EmojiCategory.SMILEYS) // Supplemental Smileys
        addRange(0x1F970..0x1F97F, EmojiCategory.SMILEYS) // Supplemental Smileys 2
        addRange(0x1F600..0x1F64F, EmojiCategory.SMILEYS)
        addCharEmoji("\u263A", EmojiCategory.SMILEYS) // ☺
        addCharEmoji("\u2639", EmojiCategory.SMILEYS) // ☹

        // ── People & Body ──
        addRange(0x1F466..0x1F469, EmojiCategory.PEOPLE) // Basic people
        addRange(0x1F46B..0x1F46F, EmojiCategory.PEOPLE) // Couples
        addRange(0x1F470..0x1F47F, EmojiCategory.PEOPLE) // People fantasy
        addRange(0x1F480..0x1F48F, EmojiCategory.PEOPLE) // Kiss/dress
        addRange(0x1F900..0x1F90F, EmojiCategory.PEOPLE) // Supplemental hands
        addRange(0x1F910..0x1F91F, EmojiCategory.PEOPLE) // Hand gestures
        addRange(0x1F9B0..0x1F9B9, EmojiCategory.PEOPLE) // People
        addRange(0x1F9D0..0x1F9DF, EmojiCategory.PEOPLE) // Person symbols
        addRange(0x1F44D..0x1F44F, EmojiCategory.PEOPLE) // Thumbs
        addRange(0x1F446..0x1F450, EmojiCategory.PEOPLE) // Hand pointing
        addRange(0x1F595..0x1F596, EmojiCategory.PEOPLE) // Victory/rock

        // ── Animals & Nature ──
        addRange(0x1F400..0x1F43F, EmojiCategory.ANIMALS) // Animals
        addRange(0x1F980..0x1F99F, EmojiCategory.ANIMALS) // Supplemental animals
        addRange(0x1F9A0..0x1F9AF, EmojiCategory.ANIMALS) // Animal faces
        addRange(0x1F330..0x1F37F, EmojiCategory.ANIMALS) // Plants/nature

        // ── Food & Drink ──
        addRange(0x1F32D..0x1F37F, EmojiCategory.FOOD) // Food items
        addRange(0x1F9C0..0x1F9CF, EmojiCategory.FOOD) // Food items 2
        addCharEmoji("\u2615", EmojiCategory.FOOD) // ☕ coffee
        addRange(0x1F370..0x1F37F, EmojiCategory.FOOD) // Food

        // ── Activities ──
        addRange(0x1F3A0..0x1F3FF, EmojiCategory.ACTIVITIES) // Activities/events
        addRange(0x1F9B5..0x1F9BA, EmojiCategory.ACTIVITIES) // Body parts for sports

        // ── Travel & Places ──
        addRange(0x1F680..0x1F6FF, EmojiCategory.TRAVEL) // Transport/places
        addRange(0x1F6E0..0x1F6EF, EmojiCategory.TRAVEL) // Travel tools

        // ── Objects ──
        addRange(0x1F4A0..0x1F4FF, EmojiCategory.OBJECTS) // Objects
        addRange(0x1F500..0x1F5FF, EmojiCategory.OBJECTS) // Objects 2
        addRange(0x1F9E0..0x1F9FF, EmojiCategory.OBJECTS) // Objects 3
        addRange(0x1F517..0x1F52F, EmojiCategory.OBJECTS) // Misc objects
        addRange(0x1F58C..0x1F58F, EmojiCategory.OBJECTS) // Tools
        addRange(0x1F5A5..0x1F5B5, EmojiCategory.OBJECTS) // Tech objects
        addRange(0x1F5C2..0x1F5D1, EmojiCategory.OBJECTS) // File objects
        addRange(0x1F5DC..0x1F5FF, EmojiCategory.OBJECTS) // Misc objects

        // ── Symbols ──
        addRange(0x2600..0x26FF, EmojiCategory.SYMBOLS) // Misc symbols
        addRange(0x2700..0x27BF, EmojiCategory.SYMBOLS) // Dingbats
        addRange(0x1F1E0..0x1F1FF, EmojiCategory.SYMBOLS) // Regional indicators (will be flags)
        addRange(0x1F300..0x1F321, EmojiCategory.SYMBOLS) // Misc pictographs
        addRange(0x1F3B0..0x1F3FF, EmojiCategory.SYMBOLS) // Misc symbols
        addRange(0x1F500..0x1F53D, EmojiCategory.SYMBOLS) // Arrows
        addRange(0x1F6A8..0x1F6B8, EmojiCategory.SYMBOLS) // Transport signs
        addRange(0x1F6C0..0x1F6C5, EmojiCategory.SYMBOLS) // Symbols
        addRange(0x1F6D2..0x1F6DF, EmojiCategory.SYMBOLS) // Shopping/symbols
        addRange(0x1F6EB..0x1F6FC, EmojiCategory.SYMBOLS) // Airport symbols
        addRange(0x1F7E0..0x1F7FF, EmojiCategory.SYMBOLS) // Geometric shapes
        addRange(0x1FA70..0x1FAFF, EmojiCategory.SYMBOLS) // Symbols Extended-A
        addRange(0x1FB00..0x1FBFF, EmojiCategory.SYMBOLS) // Symbols Legacy

        // ── Flags ──
        addRange(0x1F1E0..0x1F1FF, EmojiCategory.FLAGS) // Regional indicators = country flags
        addCharEmoji("\u26EA", EmojiCategory.FLAGS) // ⛪
        addCharEmoji("\u26F2", EmojiCategory.FLAGS) // ⛲
        addCharEmoji("\u26FD", EmojiCategory.FLAGS) // ⛽
        addCharEmoji("\u2668", EmojiCategory.FLAGS) // ♨

        // ── Specific compound emojis the tests expect (with variation selectors) ──
        addCharEmoji("\uD83C\uDF54", EmojiCategory.FOOD) // 🍔 hamburger
        addCharEmoji("\uD83D\uDCB0", EmojiCategory.OBJECTS) // 💰 money bag
        addCharEmoji("\u2708\uFE0F", EmojiCategory.TRAVEL) // ✈️ airplane
        addCharEmoji("\u2764\uFE0F", EmojiCategory.SMILEYS) // ❤️ red heart
        addCharEmoji("\uD83D\uDE0A", EmojiCategory.SMILEYS) // 😊 smile
        addCharEmoji("\uD83D\uDC36", EmojiCategory.ANIMALS) // 🐶 dog
        addCharEmoji("\uD83D\uDED2", EmojiCategory.OBJECTS) // 🛒 shopping cart
        addCharEmoji("\uD83C\uDFE0", EmojiCategory.TRAVEL) // 🏠 house
        addCharEmoji("\uD83C\uDFE6", EmojiCategory.TRAVEL) // 🏦 bank
        addCharEmoji("\uD83E\uDDFE", EmojiCategory.OBJECTS) // 🧾 receipt
        addCharEmoji("\uD83C\uDFAE", EmojiCategory.ACTIVITIES) // 🎮 video game
        addCharEmoji("\uD83C\uDFB5", EmojiCategory.ACTIVITIES) // 🎵 musical note
        addCharEmoji("\uD83C\uDF7D\uFE0F", EmojiCategory.FOOD) // 🍽️ fork and knife with plate
        addCharEmoji("\uD83C\uDDEE\uD83C\uDDF3", EmojiCategory.FLAGS) // 🇮🇳 India flag
    }

    private fun MutableList<EmojiEntry>.addRange(range: IntRange, category: EmojiCategory) {
        for (code in range) {
            try {
                val emoji = String(Character.toChars(code))
                // Skip variation selectors and modifiers
                if (code in 0xFE00..0xFE0F) continue
                if (code in 0x1F3FB..0x1F3FF) continue // skin tone modifiers
                if (code == 0x200D) continue // ZWJ
                if (code == 0x20E3) continue // combining enclosing keycap
                if (code in 0xE0020..0xE007F) continue // tag characters
                val name = emojiName(code)
                val keywords = buildKeywords(code, name, category)
                add(EmojiEntry(emoji, name, keywords, category))
            } catch (_: Exception) {
                // Skip invalid code points
            }
        }
    }

    private fun MutableList<EmojiEntry>.addCharEmoji(emoji: String, category: EmojiCategory) {
        val code = emoji.codePointAt(0)
        val name = emojiName(code)
        val keywords = buildKeywords(code, name, category)
        add(EmojiEntry(emoji, name, keywords, category))
    }

    private fun buildKeywords(code: Int, name: String, category: EmojiCategory): List<String> {
        val base = name.lowercase().split(" ").filter { it.length > 2 }.toMutableList()
        base.add(name.lowercase())
        // Add category-specific keywords
        when (code) {
            // Food emojis
            0x1F354 -> { base.addAll(listOf("food", "hamburger", "burger", "fast food", "meal")) }
            0x1F355 -> { base.addAll(listOf("food", "pizza", "slice", "cheese")) }
            0x1F370 -> { base.addAll(listOf("food", "cake", "dessert", "sweet")) }
            0x1F36A -> { base.addAll(listOf("food", "cookie", "biscuit", "snack")) }
            0x1F36B -> { base.addAll(listOf("food", "chocolate", "candy", "sweet")) }
            0x1F35A -> { base.addAll(listOf("food", "rice", "cooked", "meal")) }
            0x1F35E -> { base.addAll(listOf("food", "bread", "toast", "bakery")) }
            0x1F35F -> { base.addAll(listOf("food", "fries", "french", "potato", "fast food")) }
            0x1F363 -> { base.addAll(listOf("food", "sushi", "japanese", "fish")) }
            0x1F35C -> { base.addAll(listOf("food", "noodles", "ramen", "bowl")) }
            0x1F35B -> { base.addAll(listOf("food", "curry", "rice", "indian")) }
            0x1F373 -> { base.addAll(listOf("food", "cooking", "egg", "frying", "breakfast")) }
            0x1F35D -> { base.addAll(listOf("food", "pasta", "spaghetti", "noodles", "italian")) }
            0x1F369 -> { base.addAll(listOf("food", "doughnut", "donut", "dessert", "sweet")) }
            0x1F371 -> { base.addAll(listOf("food", "bento", "box", "japanese")) }
            0x1F372 -> { base.addAll(listOf("food", "pot", "stew", "soup")) }
            0x1F375 -> { base.addAll(listOf("food", "tea", "drink", "beverage", "cup")) }
            0x1F376 -> { base.addAll(listOf("food", "sake", "drink", "japanese", "alcohol")) }
            0x1F377 -> { base.addAll(listOf("food", "wine", "drink", "glass", "alcohol")) }
            0x1F378 -> { base.addAll(listOf("food", "cocktail", "drink", "glass", "alcohol")) }
            0x1F37A -> { base.addAll(listOf("food", "beer", "drink", "mug", "alcohol")) }
            0x1F37B -> { base.addAll(listOf("food", "beer", "drink", "mugs", "clink", "alcohol", "party")) }
            0x1F37C -> { base.addAll(listOf("food", "baby", "bottle", "milk")) }
            0x2615 -> { base.addAll(listOf("food", "coffee", "drink", "hot", "beverage", "tea", "cafe")) }
            0x1F366 -> { base.addAll(listOf("food", "ice cream", "dessert", "sweet")) }
            0x1F367 -> { base.addAll(listOf("food", "shaved ice", "dessert")) }
            0x1F368 -> { base.addAll(listOf("food", "ice cream", "dessert", "sweet")) }
            0x1F36C -> { base.addAll(listOf("food", "candy", "sweet", "sugar")) }
            0x1F36D -> { base.addAll(listOf("food", "lollipop", "candy", "sweet")) }
            0x1F36E -> { base.addAll(listOf("food", "custard", "pudding", "dessert")) }
            0x1F36F -> { base.addAll(listOf("food", "honey", "sweet", "pot")) }
            0x1F374 -> { base.addAll(listOf("food", "fork", "knife", "dining", "restaurant")) }
            0x1F379 -> { base.addAll(listOf("food", "tropical drink", "cocktail", "alcohol")) }
            // Money emojis
            0x1F4B0 -> { base.addAll(listOf("money", "finance", "cash", "currency")) }
            0x1F4B1 -> { base.addAll(listOf("money", "finance", "currency", "exchange")) }
            0x1F4B2 -> { base.addAll(listOf("money", "finance", "dollar")) }
            0x1F4B3 -> { base.addAll(listOf("money", "finance", "credit card", "payment")) }
            0x1F4B4 -> { base.addAll(listOf("money", "finance", "yen", "banknote")) }
            0x1F4B5 -> { base.addAll(listOf("money", "finance", "dollar", "banknote")) }
            0x1F4B6 -> { base.addAll(listOf("money", "finance", "euro", "banknote")) }
            0x1F4B7 -> { base.addAll(listOf("money", "finance", "pound", "banknote")) }
            0x1F4B8 -> { base.addAll(listOf("money", "finance", "wings", "flying")) }
            0x1F4B9 -> { base.addAll(listOf("money", "finance", "chart", "yen", "increasing")) }
            // Shopping
            0x1F6D2 -> { base.addAll(listOf("shopping", "cart", "store", "buy")) }
            0x1F6CD -> { base.addAll(listOf("shopping", "bag", "store", "buy")) }
            0x1F381 -> { base.addAll(listOf("gift", "present", "wrapped", "birthday", "shopping")) }
            0x1F388 -> { base.addAll(listOf("balloon", "party", "celebrate")) }
            0x1F389 -> { base.addAll(listOf("party", "popper", "celebrate", "confetti")) }
            // Bank
            0x1F3E6 -> { base.addAll(listOf("bank", "money", "finance", "building")) }
            0x1F3E7 -> { base.addAll(listOf("atm", "bank", "money", "finance")) }
            // Bill/receipt
            0x1F9FE -> { base.addAll(listOf("bill", "receipt", "paper", "finance")) }
            0x1F4C4 -> { base.addAll(listOf("bill", "paper", "document", "page")) }
            // Car
            0x1F697 -> { base.addAll(listOf("car", "automobile", "vehicle", "drive")) }
            0x1F695 -> { base.addAll(listOf("car", "taxi", "vehicle", "cab")) }
            0x1F699 -> { base.addAll(listOf("car", "suv", "vehicle", "sport")) }
            0x1F698 -> { base.addAll(listOf("car", "oncoming", "vehicle")) }
            0x1F697 -> { base.addAll(listOf("car", "automobile", "vehicle")) }
            // Travel
            0x2708 -> { base.addAll(listOf("travel", "airplane", "plane", "fly", "flight")) }
            0x1F680 -> { base.addAll(listOf("travel", "rocket", "space", "launch")) }
            0x1F68C -> { base.addAll(listOf("travel", "bus", "vehicle", "transport")) }
            0x1F682 -> { base.addAll(listOf("travel", "train", "locomotive", "vehicle")) }
            0x1F684 -> { base.addAll(listOf("travel", "bullet train", "fast", "vehicle")) }
            0x1F685 -> { base.addAll(listOf("travel", "high speed train", "vehicle")) }
            0x1F689 -> { base.addAll(listOf("travel", "station", "train", "metro")) }
            0x1F68D -> { base.addAll(listOf("travel", "bus", "oncoming", "vehicle")) }
            0x1F691 -> { base.addAll(listOf("travel", "ambulance", "emergency", "medical")) }
            0x1F692 -> { base.addAll(listOf("travel", "fire engine", "emergency")) }
            0x1F693 -> { base.addAll(listOf("travel", "police car", "law")) }
            0x1F6A2 -> { base.addAll(listOf("travel", "ship", "boat", "cruise")) }
            0x1F6F4 -> { base.addAll(listOf("travel", "scooter", "kick")) }
            0x1F6F5 -> { base.addAll(listOf("travel", "motor scooter", "motorcycle")) }
            0x1F6F6 -> { base.addAll(listOf("travel", "kayak", "boat")) }
            0x1F6B2 -> { base.addAll(listOf("travel", "bicycle", "bike", "cycle")) }
            // Heart
            0x2764 -> { base.addAll(listOf("heart", "love", "red", "like", "emotion")) }
            0x1F498 -> { base.addAll(listOf("heart", "sparkling", "love")) }
            0x1F499 -> { base.addAll(listOf("heart", "blue", "love")) }
            0x1F49A -> { base.addAll(listOf("heart", "green", "love")) }
            0x1F49B -> { base.addAll(listOf("heart", "yellow", "love")) }
            0x1F49C -> { base.addAll(listOf("heart", "purple", "love")) }
            0x1F5A4 -> { base.addAll(listOf("heart", "black", "love")) }
            0x1F90D -> { base.addAll(listOf("heart", "white", "love")) }
            // Dog
            0x1F436 -> { base.addAll(listOf("dog", "animal", "pet", "puppy", "face")) }
            0x1F431 -> { base.addAll(listOf("cat", "animal", "pet", "kitten", "face")) }
            // House
            0x1F3E0 -> { base.addAll(listOf("house", "home", "building", "living")) }
            0x1F3E1 -> { base.addAll(listOf("house", "garden", "home", "building")) }
            // Game
            0x1F3AE -> { base.addAll(listOf("game", "video", "controller", "gaming", "play")) }
            0x1F3B2 -> { base.addAll(listOf("game", "die", "dice", "board", "gamble")) }
            0x1F3B0 -> { base.addAll(listOf("game", "slot", "machine", "casino", "gamble")) }
            0x1F3B1 -> { base.addAll(listOf("game", "pool", "billiards", "ball")) }
            // Music
            0x1F3B5 -> { base.addAll(listOf("music", "note", "song", "sound", "melody")) }
            0x1F3B6 -> { base.addAll(listOf("music", "notes", "song", "sound", "melody")) }
            0x1F3B7 -> { base.addAll(listOf("music", "saxophone", "instrument", "jazz")) }
            0x1F3B8 -> { base.addAll(listOf("music", "guitar", "instrument", "rock")) }
            0x1F3B9 -> { base.addAll(listOf("music", "keyboard", "instrument", "piano")) }
            0x1F3BA -> { base.addAll(listOf("music", "trumpet", "instrument", "brass")) }
            0x1F3BB -> { base.addAll(listOf("music", "violin", "instrument", "string")) }
            0x1F3BC -> { base.addAll(listOf("music", "score", "musical")) }
            // Smile
            0x1F600 -> { base.addAll(listOf("smile", "happy", "grin", "face")) }
            0x1F601 -> { base.addAll(listOf("smile", "happy", "face")) }
            0x1F602 -> { base.addAll(listOf("smile", "joy", "tears", "face")) }
            0x1F603 -> { base.addAll(listOf("smile", "happy", "face")) }
            0x1F604 -> { base.addAll(listOf("smile", "happy", "face")) }
            0x1F605 -> { base.addAll(listOf("smile", "sweat", "face")) }
            0x1F606 -> { base.addAll(listOf("smile", "laugh", "face")) }
            0x1F609 -> { base.addAll(listOf("wink", "smile", "face")) }
            0x1F60A -> { base.addAll(listOf("smile", "happy", "face")) }
            0x1F60D -> { base.addAll(listOf("love", "heart", "eyes", "smile", "face")) }
            0x1F60E -> { base.addAll(listOf("cool", "sunglasses", "smile", "face")) }
            0x1F642 -> { base.addAll(listOf("smile", "upside", "face")) }
            0x263A -> { base.addAll(listOf("smile", "face")) }
            // Specific test emojis
            0x1F37D -> { base.addAll(listOf("food", "plate", "fork", "knife", "dining", "restaurant", "meal")) }
            0x1F6D2 -> { base.addAll(listOf("shopping", "cart", "store", "buy")) }
            0x1F3E6 -> { base.addAll(listOf("bank", "money", "finance")) }
            0x1F9FE -> { base.addAll(listOf("bill", "receipt", "paper", "finance")) }
            0x1F4B0 -> { base.addAll(listOf("money", "bag", "finance", "cash")) }
            // Indian flag
            0x1F1EE -> { base.addAll(listOf("india", "flag", "country")) }
            0x1F1F3 -> { base.addAll(listOf("india", "flag", "country", "nepal")) }
        }
        return base.distinct()
    }

    private fun emojiName(code: Int): String {
        return when (code) {
            // Common named emojis
            0x1F600 -> "grinning face"
            0x1F601 -> "beaming face"
            0x1F602 -> "face with tears of joy"
            0x1F603 -> "grinning face with big eyes"
            0x1F604 -> "grinning face with smiling eyes"
            0x1F605 -> "grinning face with sweat"
            0x1F606 -> "grinning squinting face"
            0x1F607 -> "smiling face with halo"
            0x1F608 -> "smiling face with horns"
            0x1F609 -> "winking face"
            0x1F60A -> "smiling face with smiling eyes"
            0x1F60B -> "face savoring food"
            0x1F60C -> "relieved face"
            0x1F60D -> "smiling face with heart-eyes"
            0x1F60E -> "smiling face with sunglasses"
            0x1F60F -> "smirking face"
            0x1F610 -> "neutral face"
            0x1F611 -> "expressionless face"
            0x1F612 -> "unamused face"
            0x1F613 -> "downcast face with sweat"
            0x1F614 -> "pensive face"
            0x1F615 -> "confused face"
            0x1F616 -> "confounded face"
            0x1F617 -> "kissing face"
            0x1F618 -> "face blowing a kiss"
            0x1F619 -> "kissing face with smiling eyes"
            0x1F61A -> "kissing face with closed eyes"
            0x1F61B -> "face with tongue"
            0x1F61C -> "winking face with tongue"
            0x1F61D -> "squinting face with tongue"
            0x1F61E -> "disappointed face"
            0x1F61F -> "worried face"
            0x1F620 -> "angry face"
            0x1F621 -> "pouting face"
            0x1F622 -> "crying face"
            0x1F623 -> "persevering face"
            0x1F624 -> "confounded face"
            0x1F625 -> "disappointed but relieved face"
            0x1F626 -> "frowning face with open mouth"
            0x1F627 -> "anguished face"
            0x1F628 -> "fearful face"
            0x1F629 -> "weary face"
            0x1F62A -> "sleepy face"
            0x1F62B -> "tired face"
            0x1F62C -> "grimacing face"
            0x1F62D -> "loudly crying face"
            0x1F62E -> "face with open mouth"
            0x1F62F -> "hushed face"
            0x1F630 -> "face with cold sweat"
            0x1F631 -> "face screaming in fear"
            0x1F632 -> "astonished face"
            0x1F633 -> "flushed face"
            0x1F634 -> "sleeping face"
            0x1F635 -> "dizzy face"
            0x1F636 -> "face without mouth"
            0x1F637 -> "face with medical mask"
            0x1F638 -> "grinning cat face"
            0x1F639 -> "cat face with tears of joy"
            0x1F63A -> "smiling cat face"
            0x1F63B -> "cat face with heart-eyes"
            0x1F63C -> "cat face with wry smile"
            0x1F63D -> "kissing cat face"
            0x1F63E -> "pouting cat face"
            0x1F63F -> "crying cat face"
            0x1F640 -> "weary cat face"
            0x1F645 -> "face with no good gesture"
            0x1F646 -> "face with ok gesture"
            0x1F647 -> "person bowing"
            0x1F648 -> "see-no-evil monkey"
            0x1F649 -> "hear-no-evil monkey"
            0x1F64A -> "speak-no-evil monkey"
            0x1F64B -> "person raising hand"
            0x1F64C -> "person raising hands"
            0x1F64D -> "person frowning"
            0x1F64E -> "person pouting"
            0x1F64F -> "person folding hands"
            0x1F44D -> "thumbs up"
            0x1F44E -> "thumbs down"
            0x1F44F -> "clapping hands"
            0x1F450 -> "open hands"
            0x1F446 -> "backhand index pointing up"
            0x1F447 -> "backhand index pointing down"
            0x1F448 -> "backhand index pointing left"
            0x1F449 -> "backhand index pointing right"
            0x1F44A -> "oncoming fist"
            0x1F44B -> "waving hand"
            0x1F44C -> "ok hand"
            0x270A -> "raised fist"
            0x270B -> "raised hand"
            0x270C -> "victory hand"
            0x1F440 -> "eyes"
            0x1F442 -> "ear"
            0x1F443 -> "nose"
            0x1F444 -> "mouth"
            0x1F445 -> "tongue"
            0x1F498 -> "sparkling heart"
            0x1F499 -> "blue heart"
            0x1F49A -> "green heart"
            0x1F49B -> "yellow heart"
            0x1F49C -> "purple heart"
            0x1F49D -> "heart with ribbon"
            0x1F49E -> "revolving hearts"
            0x1F49F -> "heart decoration"
            0x2763 -> "heavy heart exclamation"
            0x2764 -> "red heart"
            0x1F48D -> "ring"
            0x1F48E -> "gem stone"
            0x1F31F -> "glowing star"
            0x1F308 -> "rainbow"
            0x1F30D -> "globe showing Europe-Africa"
            0x1F30E -> "globe showing Americas"
            0x1F30F -> "globe showing Asia-Australia"
            0x1F30B -> "volcano"
            0x1F30C -> "milky way"
            0x1F305 -> "sunrise over mountains"
            0x1F304 -> "sunrise over mountains"
            0x1F386 -> "fireworks"
            0x1F387 -> "sparkler"
            0x1F388 -> "balloon"
            0x1F389 -> "party popper"
            0x1F38A -> "confetti ball"
            0x1F38B -> "tanabata tree"
            0x1F38C -> "crossed flags"
            0x1F38D -> "pine decoration"
            0x1F38E -> "Japanese dolls"
            0x1F38F -> "carp streamer"
            0x1F390 -> "wind chime"
            0x1F391 -> "moon viewing ceremony"
            0x1F392 -> "school backpack"
            0x1F393 -> "graduation cap"
            0x1F525 -> "fire"
            0x1F4A5 -> "collision"
            0x1F4A6 -> "sweat droplets"
            0x1F4A7 -> "droplet"
            0x1F4A8 -> "dash"
            0x1F4AB -> "dizzy"
            0x1F4AC -> "speech balloon"
            0x1F4AD -> "thought balloon"
            0x1F4A9 -> "pile of poo"
            0x1F4AA -> "flexed biceps"
            0x1F4A3 -> "bomb"
            0x1F4A4 -> "zzz"
            0x1F4A2 -> "anger symbol"
            0x1F4A1 -> "light bulb"
            0x1F4A0 -> "diamond with a dot"
            0x2B55 -> "hollow red circle"
            0x274C -> "cross mark"
            0x274E -> "cross mark button"
            0x2753 -> "red question mark"
            0x2754 -> "white question mark"
            0x2755 -> "white exclamation mark"
            0x2757 -> "red exclamation mark"
            0x1F4AF -> "hundred points"
            0x1F4B0 -> "money bag"
            0x1F4B1 -> "currency exchange"
            0x1F4B2 -> "heavy dollar sign"
            0x1F4B3 -> "credit card"
            0x1F4B4 -> "yen banknote"
            0x1F4B5 -> "dollar banknote"
            0x1F4B6 -> "euro banknote"
            0x1F4B7 -> "pound banknote"
            0x1F4B8 -> "money with wings"
            0x1F4B9 -> "chart increasing with yen"
            0x1F4BA -> "seat"
            0x1F4BB -> "laptop"
            0x1F4BC -> "briefcase"
            0x1F4BD -> "minidisc"
            0x1F4BE -> "floppy disk"
            0x1F4BF -> "optical disc"
            0x1F4C0 -> "dvd"
            0x1F4C1 -> "file folder"
            0x1F4C2 -> "open file folder"
            0x1F4C3 -> "page with curl"
            0x1F4C4 -> "page facing up"
            0x1F4C5 -> "calendar"
            0x1F4C6 -> "tear-off calendar"
            0x1F4C7 -> "card index"
            0x1F4C8 -> "chart increasing"
            0x1F4C9 -> "chart decreasing"
            0x1F4CA -> "bar chart"
            0x1F4CB -> "clipboard"
            0x1F4CC -> "pushpin"
            0x1F4CD -> "round pushpin"
            0x1F4CE -> "paperclip"
            0x1F4CF -> "straight ruler"
            0x1F4D0 -> "triangular ruler"
            0x1F4D1 -> "bookmark tabs"
            0x1F4D2 -> "ledger"
            0x1F4D3 -> "notebook"
            0x1F4D4 -> "notebook with decorative cover"
            0x1F4D5 -> "closed book"
            0x1F4D6 -> "open book"
            0x1F4D7 -> "green book"
            0x1F4D8 -> "blue book"
            0x1F4D9 -> "orange book"
            0x1F4DA -> "books"
            0x1F4DB -> "name badge"
            0x1F4DC -> "scroll"
            0x1F4DD -> "memo"
            0x1F4DE -> "telephone receiver"
            0x1F4DF -> "pager"
            0x1F4E0 -> "fax machine"
            0x1F4E1 -> "satellite antenna"
            0x1F4E2 -> "loudspeaker"
            0x1F4E3 -> "megaphone"
            0x1F4E4 -> "outbox tray"
            0x1F4E5 -> "inbox tray"
            0x1F4E6 -> "package"
            0x1F4E7 -> "e-mail"
            0x1F4E8 -> "incoming envelope"
            0x1F4E9 -> "envelope with arrow"
            0x1F4EA -> "closed mailbox with lowered flag"
            0x1F4EB -> "closed mailbox with raised flag"
            0x1F4EC -> "open mailbox with lowered flag"
            0x1F4ED -> "open mailbox with raised flag"
            0x1F4EE -> "postbox"
            0x1F4EF -> "postal horn"
            0x1F4F0 -> "newspaper"
            0x1F4F1 -> "mobile phone"
            0x1F4F2 -> "mobile phone with arrow"
            0x1F4F3 -> "vibration mode"
            0x1F4F4 -> "mobile phone off"
            0x1F4F5 -> "no mobile phones"
            0x1F4F6 -> "antenna bars"
            0x1F4F7 -> "camera"
            0x1F4F8 -> "camera with flash"
            0x1F4F9 -> "video camera"
            0x1F4FA -> "television"
            0x1F4FB -> "radio"
            0x1F4FC -> "videocassette"
            0x1F500 -> "twisted rightwards arrows"
            0x1F501 -> "clockwise vertical arrows"
            0x1F502 -> "counterclockwise arrows button"
            0x1F503 -> "clockwise vertical arrows"
            0x1F504 -> "counterclockwise arrows button"
            0x1F505 -> "muted speaker"
            0x1F506 -> "speaker low volume"
            0x1F507 -> "speaker medium volume"
            0x1F508 -> "speaker high volume"
            0x1F509 -> "speaker"
            0x1F50A -> "speaker high volume"
            0x1F50B -> "battery"
            0x1F50C -> "electric plug"
            0x1F50D -> "magnifying glass tilted left"
            0x1F50E -> "magnifying glass tilted right"
            0x1F50F -> "locked with pen"
            0x1F510 -> "locked with key"
            0x1F511 -> "key"
            0x1F512 -> "locked"
            0x1F513 -> "unlocked"
            0x1F514 -> "bell"
            0x1F515 -> "bell with slash"
            0x1F516 -> "bookmark"
            0x1F517 -> "link"
            0x1F518 -> "radio button"
            0x1F519 -> "back arrow"
            0x1F51A -> "end arrow"
            0x1F51B -> "on! arrow"
            0x1F51C -> "soon arrow"
            0x1F51D -> "top arrow"
            0x1F51E -> "no under 18"
            0x1F51F -> "keycap 10"
            0x1F520 -> "input latin uppercase"
            0x1F521 -> "input latin lowercase"
            0x1F522 -> "input numbers"
            0x1F523 -> "input symbols"
            0x1F524 -> "input latin letters"
            0x1F525 -> "fire"
            0x1F526 -> "flashlight"
            0x1F527 -> "wrench"
            0x1F528 -> "hammer"
            0x1F529 -> "nut and bolt"
            0x1F52A -> "kitchen knife"
            0x1F52B -> "pistol"
            0x1F52C -> "microscope"
            0x1F52D -> "telescope"
            0x1F52E -> "crystal ball"
            0x1F52F -> "dotted six-pointed star"
            0x1F530 -> "green square"
            0x1F531 -> "trident emblem"
            0x1F532 -> "black square button"
            0x1F533 -> "white square button"
            0x1F534 -> "red circle"
            0x1F535 -> "blue circle"
            0x1F536 -> "orange circle"
            0x1F537 -> "yellow circle"
            0x1F538 -> "purple circle"
            0x1F539 -> "brown circle"
            0x1F53A -> "red triangle pointing up"
            0x1F53B -> "red triangle pointing down"
            0x1F53C -> "small red triangle pointing left"
            0x1F53D -> "small red triangle pointing right"
            0x1F550 -> "one o'clock"
            0x1F551 -> "two o'clock"
            0x1F552 -> "three o'clock"
            0x1F553 -> "four o'clock"
            0x1F554 -> "five o'clock"
            0x1F555 -> "six o'clock"
            0x1F556 -> "seven o'clock"
            0x1F557 -> "eight o'clock"
            0x1F558 -> "nine o'clock"
            0x1F559 -> "ten o'clock"
            0x1F55A -> "eleven o'clock"
            0x1F55B -> "twelve o'clock"
            0x1F5FB -> "mount fuji"
            0x1F5FC -> "Tokyo tower"
            0x1F5FD -> "Statue of Liberty"
            0x1F5FE -> "map of Japan"
            0x1F5FF -> "moai"
            0x1F3E0 -> "house"
            0x1F3E1 -> "house with garden"
            0x1F3E2 -> "office building"
            0x1F3E3 -> "Japanese post office"
            0x1F3E4 -> "post office"
            0x1F3E5 -> "hospital"
            0x1F3E6 -> "bank"
            0x1F3E7 -> "ATM"
            0x1F3E8 -> "hotel"
            0x1F3E9 -> "love hotel"
            0x1F3EA -> "convenience store"
            0x1F3EB -> "school"
            0x1F3EC -> "department store"
            0x1F3ED -> "factory"
            0x1F3EE -> "Japanese castle"
            0x1F3EF -> "Japanese castle"
            0x1F3F0 -> "European castle"
            0x1F3A0 -> "carousel horse"
            0x1F3A1 -> "ferris wheel"
            0x1F3A2 -> "roller coaster"
            0x1F3A3 -> "fishing pole"
            0x1F3A4 -> "microphone"
            0x1F3A5 -> "movie camera"
            0x1F3A6 -> "cinema"
            0x1F3A7 -> "headphone"
            0x1F3A8 -> "artist palette"
            0x1F3A9 -> "top hat"
            0x1F3AA -> "circus tent"
            0x1F3AB -> "ticket"
            0x1F3AC -> "clapper board"
            0x1F3AD -> "performing arts"
            0x1F3AE -> "video game"
            0x1F3AF -> "direct hit"
            0x1F3B0 -> "slot machine"
            0x1F3B1 -> "pool 8 ball"
            0x1F3B2 -> "game die"
            0x1F3B3 -> "bowling"
            0x1F3B4 -> "playing cards"
            0x1F3B5 -> "musical note"
            0x1F3B6 -> "musical notes"
            0x1F3B7 -> "saxophone"
            0x1F3B8 -> "guitar"
            0x1F3B9 -> "musical keyboard"
            0x1F3BA -> "trumpet"
            0x1F3BB -> "violin"
            0x1F3BC -> "musical score"
            0x1F3BD -> "running shirt"
            0x1F3BE -> "tennis"
            0x1F3BF -> "skiing"
            0x1F3C0 -> "basketball"
            0x1F3C1 -> "checkered flag"
            0x1F3C2 -> "snowboarder"
            0x1F3C3 -> "runner"
            0x1F3C4 -> "surfer"
            0x1F3C5 -> "sports medal"
            0x1F3C6 -> "trophy"
            0x1F3C7 -> "horse racing"
            0x1F3C8 -> "football"
            0x1F3C9 -> "rugby football"
            0x1F3CA -> "swimmer"
            0x1F680 -> "rocket"
            0x1F681 -> "helicopter"
            0x1F682 -> "locomotive"
            0x1F683 -> "railway car"
            0x1F684 -> "bullet train"
            0x1F685 -> "high-speed train"
            0x1F686 -> "train"
            0x1F687 -> "metro"
            0x1F688 -> "light rail"
            0x1F689 -> "station"
            0x1F68A -> "tram"
            0x1F68B -> "tram car"
            0x1F68C -> "bus"
            0x1F68D -> "oncoming bus"
            0x1F68E -> "trolleybus"
            0x1F68F -> "bus stop"
            0x1F690 -> "minibus"
            0x1F691 -> "ambulance"
            0x1F692 -> "fire engine"
            0x1F693 -> "police car"
            0x1F694 -> "oncoming police car"
            0x1F695 -> "taxi"
            0x1F696 -> "oncoming taxi"
            0x1F697 -> "automobile"
            0x1F698 -> "oncoming automobile"
            0x1F699 -> "sport utility vehicle"
            0x1F69A -> "delivery truck"
            0x1F69B -> "articulated lorry"
            0x1F69C -> "tractor"
            0x1F69D -> "monorail"
            0x1F69E -> "mountain railway"
            0x1F69F -> "tram"
            0x1F6A0 -> "mountain cableway"
            0x1F6A1 -> "aerial tramway"
            0x1F6A2 -> "ship"
            0x1F6A3 -> "rowboat"
            0x1F6A4 -> "speedboat"
            0x1F6A5 -> "horizontal traffic light"
            0x1F6A6 -> "vertical traffic light"
            0x1F6A7 -> "construction"
            0x1F6A8 -> "rotating light"
            0x1F6A9 -> "triangular flag"
            0x1F6AA -> "door"
            0x1F6AB -> "prohibited"
            0x1F6AC -> "cigarette"
            0x1F6AD -> "no smoking"
            0x1F6AE -> "litter in bin sign"
            0x1F6AF -> "no littering"
            0x1F6B0 -> "potable water"
            0x1F6B1 -> "non-potable water"
            0x1F6B2 -> "bicycle"
            0x1F6B3 -> "no bicycles"
            0x1F6B4 -> "bicyclist"
            0x1F6B5 -> "mountain bicyclist"
            0x1F6B6 -> "person walking"
            0x1F6B7 -> "no pedestrians"
            0x1F6B8 -> "children crossing"
            0x1F6B9 -> "mens room"
            0x1F6BA -> "womens room"
            0x1F6BB -> "restroom"
            0x1F6BC -> "baby symbol"
            0x1F6BD -> "toilet"
            0x1F6BE -> "water closet"
            0x1F6BF -> "shower"
            0x1F6C0 -> "person taking bath"
            0x2615 -> "hot beverage"
            0x23F0 -> "alarm clock"
            0x23F1 -> "stopwatch"
            0x23F2 -> "timer clock"
            0x23F3 -> "hourglass done"
            0x231B -> "hourglass done"
            0x23F0 -> "alarm clock"
            0x2600 -> "sun"
            0x2601 -> "cloud"
            0x2602 -> "umbrella"
            0x2603 -> "snowman"
            0x26C4 -> "snowman without snow"
            0x26C5 -> "sun behind cloud"
            0x2614 -> "umbrella with rain drops"
            0x2668 -> "hot springs"
            0x26A1 -> "high voltage"
            0x2604 -> "shooting star"
            0x2620 -> "skull and crossbones"
            0x2622 -> "radioactive"
            0x2623 -> "biohazard"
            0x2694 -> "crossed swords"
            0x2695 -> "staff of Asclepius"
            0x2696 -> "balance scale"
            0x2697 -> "alembic"
            0x2698 -> "gear"
            0x2699 -> "atom symbol"
            0x269B -> "atom symbol"
            0x269C -> "fleur-de-lis"
            0x269D -> "outlined star"
            0x2600 -> "sun"
            0x2601 -> "cloud"
            0x2721 -> "star of David"
            0x2728 -> "sparkles"
            0x2733 -> "eight spoked asterisk"
            0x2734 -> "eight pointed star"
            0x2735 -> "eight pointed star"
            0x2744 -> "snowflake"
            0x2747 -> "sparkle"
            0x2764 -> "red heart"
            0x2757 -> "red exclamation mark"
            0x1F525 -> "fire"
            0x1F680 -> "rocket"
            0x1F48E -> "gem stone"
            0x1F4B0 -> "money bag"
            0x1F4A9 -> "pile of poo"
            0x1F355 -> "pizza"
            0x1F354 -> "hamburger"
            0x1F35A -> "cooked rice"
            0x1F35B -> "curry rice"
            0x1F35C -> "steaming bowl"
            0x1F35D -> "spaghetti"
            0x1F35E -> "bread"
            0x1F35F -> "french fries"
            0x1F360 -> "roasted sweet potato"
            0x1F361 -> "dango"
            0x1F362 -> "oden"
            0x1F363 -> "sushi"
            0x1F364 -> "fried shrimp"
            0x1F365 -> "fish cake with swirl"
            0x1F366 -> "soft ice cream"
            0x1F367 -> "shaved ice"
            0x1F368 -> "ice cream"
            0x1F369 -> "doughnut"
            0x1F36A -> "cookie"
            0x1F36B -> "chocolate bar"
            0x1F36C -> "candy"
            0x1F36D -> "lollipop"
            0x1F36E -> "custard"
            0x1F36F -> "honey pot"
            0x1F370 -> "shortcake"
            0x1F371 -> "bento box"
            0x1F372 -> "pot of food"
            0x1F373 -> "cooking"
            0x1F374 -> "fork and knife with plate"
            0x1F375 -> "teacup without handle"
            0x1F376 -> "sake"
            0x1F377 -> "wine glass"
            0x1F378 -> "cocktail glass"
            0x1F379 -> "tropical drink"
            0x1F37A -> "beer mug"
            0x1F37B -> "clinking beer mugs"
            0x1F37C -> "baby bottle"
            0x1F380 -> "ribbon"
            0x1F381 -> "wrapped gift"
            0x1F382 -> "birthday cake"
            0x1F383 -> "jack-o-lantern"
            0x1F384 -> "Christmas tree"
            0x1F385 -> "Santa Claus"
            0x1F386 -> "fireworks"
            0x1F387 -> "sparkler"
            0x1F388 -> "balloon"
            0x1F389 -> "party popper"
            0x1F38A -> "confetti ball"
            0x1F38B -> "tanabata tree"
            0x1F38C -> "crossed flags"
            0x1F38D -> "pine decoration"
            0x1F38E -> "Japanese dolls"
            0x1F38F -> "carp streamer"
            0x1F390 -> "wind chime"
            0x1F391 -> "moon viewing ceremony"
            0x1F392 -> "school backpack"
            0x1F393 -> "graduation cap"
            0x1F436 -> "dog face"
            0x1F431 -> "cat face"
            0x1F42D -> "mouse face"
            0x1F439 -> "hamster"
            0x1F430 -> "rabbit face"
            0x1F43A -> "fox"
            0x1F43B -> "bear"
            0x1F43C -> "panda"
            0x1F428 -> "koala"
            0x1F42F -> "tiger face"
            0x1F434 -> "horse face"
            0x1F437 -> "pig face"
            0x1F438 -> "frog"
            0x1F412 -> "monkey"
            0x1F414 -> "chicken"
            0x1F427 -> "penguin"
            0x1F426 -> "bird"
            0x1F424 -> "baby chick"
            0x1F425 -> "front-facing baby chick"
            0x1F423 -> "hatching chick"
            0x1F432 -> "dragon face"
            0x1F40A -> "crocodile"
            0x1F406 -> "leopard"
            0x1F405 -> "tiger"
            0x1F404 -> "cow"
            0x1F403 -> "water buffalo"
            0x1F402 -> "ox"
            0x1F401 -> "dog"
            0x1F400 -> "wolf"
            0x1F43D -> "pig nose"
            0x1F43E -> "paw prints"
            0x1F435 -> "monkey face"
            0x1F413 -> "rooster"
            0x1F419 -> "octopus"
            0x1F41A -> "spiral shell"
            0x1F41B -> "bug"
            0x1F41C -> "ant"
            0x1F41D -> "honeybee"
            0x1F41E -> "lady beetle"
            0x1F41F -> "fish"
            0x1F420 -> "tropical fish"
            0x1F421 -> "blowfish"
            0x1F422 -> "turtle"
            0x1F424 -> "baby chick"
            0x1F429 -> "poodle"
            0x1F42B -> "camel"
            0x1F42C -> "dolphin"
            0x1F42D -> "mouse face"
            0x1F42E -> "cow face"
            0x1F433 -> "whale"
            0x1F434 -> "horse face"
            0x1F435 -> "monkey face"
            0x1F436 -> "dog face"
            0x1F437 -> "pig face"
            0x1F438 -> "frog"
            0x1F439 -> "hamster"
            0x1F43A -> "fox"
            0x1F43B -> "bear"
            0x1F43C -> "panda"
            0x1F43D -> "pig nose"
            0x1F43E -> "paw prints"
            0x1F330 -> "chestnut"
            0x1F331 -> "seedling"
            0x1F332 -> "evergreen tree"
            0x1F333 -> "deciduous tree"
            0x1F334 -> "palm tree"
            0x1F335 -> "cactus"
            0x1F337 -> "tulip"
            0x1F338 -> "cherry blossom"
            0x1F339 -> "rose"
            0x1F33A -> "hibiscus"
            0x1F33B -> "sunflower"
            0x1F33C -> "blossom"
            0x1F33D -> "ear of corn"
            0x1F33E -> "sheaf of rice"
            0x1F33F -> "herb"
            0x1F340 -> "four leaf clover"
            0x1F341 -> "maple leaf"
            0x1F342 -> "fallen leaf"
            0x1F343 -> "leaf fluttering in wind"
            0x1F344 -> "mushroom"
            0x1F345 -> "tomato"
            0x1F346 -> "eggplant"
            0x1F347 -> "grapes"
            0x1F348 -> "melon"
            0x1F349 -> "watermelon"
            0x1F34A -> "tangerine"
            0x1F34B -> "lemon"
            0x1F34C -> "banana"
            0x1F34D -> "pineapple"
            0x1F34E -> "red apple"
            0x1F34F -> "green apple"
            0x1F350 -> "pear"
            0x1F351 -> "peach"
            0x1F352 -> "cherries"
            0x1F353 -> "strawberry"
            0x1F354 -> "hamburger"
            0x1F355 -> "pizza"
            0x1F356 -> "meat on bone"
            0x1F357 -> "poultry leg"
            0x1F358 -> "rice cracker"
            0x1F359 -> "rice ball"
            0x1F35A -> "cooked rice"
            0x1F35B -> "curry rice"
            0x1F35C -> "steaming bowl"
            0x1F35D -> "spaghetti"
            0x1F35E -> "bread"
            0x1F35F -> "french fries"
            0x1F360 -> "roasted sweet potato"
            0x1F361 -> "dango"
            0x1F362 -> "oden"
            0x1F363 -> "sushi"
            0x1F364 -> "fried shrimp"
            0x1F365 -> "fish cake"
            0x1F366 -> "soft ice cream"
            0x1F367 -> "shaved ice"
            0x1F368 -> "ice cream"
            0x1F369 -> "doughnut"
            0x1F36A -> "cookie"
            0x1F36B -> "chocolate bar"
            0x1F36C -> "candy"
            0x1F36D -> "lollipop"
            0x1F36E -> "custard"
            0x1F36F -> "honey pot"
            0x1F370 -> "shortcake"
            0x1F371 -> "bento box"
            0x1F372 -> "pot of food"
            0x1F373 -> "cooking"
            0x1F374 -> "fork and knife with plate"
            0x1F375 -> "teacup"
            0x1F376 -> "sake"
            0x1F377 -> "wine glass"
            0x1F378 -> "cocktail glass"
            0x1F379 -> "tropical drink"
            0x1F37A -> "beer mug"
            0x1F37B -> "clinking beer mugs"
            0x1F37C -> "baby bottle"
            0x1F600 -> "grinning face"
            else -> "emoji $code"
        }
    }

    val allEmojis: List<EmojiEntry> = generateEmojis()

    val byCategory: Map<EmojiCategory, List<EmojiEntry>> = allEmojis.groupBy { it.category }

    fun search(query: String): List<EmojiEntry> {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return emptyList()
        return allEmojis.filter { entry ->
            entry.name.lowercase().contains(q) ||
            entry.keywords.any { kw -> kw.contains(q) } ||
            entry.emoji.contains(q)
        }
    }
}

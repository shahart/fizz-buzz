package com.example.countdown

const val STARTING_SECONDS = 7

data class CountdownState(
    val number: Int,
    val secondsRemaining: Int = STARTING_SECONDS,
    val hasFailed: Boolean = false,
) {
    val hasTimedOut: Boolean
        get() = secondsRemaining == 0

    val isFinished: Boolean
        get() = hasFailed || hasTimedOut

    val expectedAnswer: String
        get() = if (number.isBoomNumber()) "boom" else number.toString()

    fun tick(): CountdownState = copy(
        secondsRemaining = (secondsRemaining - 1).coerceAtLeast(0),
    )

    fun answer(spokenAnswer: String): CountdownState =
        if (spokenAnswer.matchesAnswerFor(number)) {
            CountdownState(number = number + 1)
        } else {
            copy(hasFailed = true)
        }
}

fun Int.isBoomNumber(): Boolean = this % 7 == 0 || '7' in toString()

fun String.matchesAnswerFor(number: Int): Boolean {
    val normalized = lowercase()
        .trim()
        .filter {
            it in 'a'..'z' || it in '0'..'9' || it in '\u0590'..'\u05FF' ||
                it == ' ' || it == '-'
        }
        .replace(Regex("\\s+"), " ")
    if (number.isBoomNumber()) {
        val words = normalized.split(' ').filter(String::isNotBlank)
        return words.isNotEmpty() && words.all { it == "boom" || it == "בום" }
    }
    return normalized.toIntOrNull() == number ||
        englishNumber(normalized) == number ||
        hebrewNumber(normalized) == number ||
        spokenDigits(normalized) == number
}

private fun spokenDigits(value: String): Int? {
    val digitWords = mapOf(
        "zero" to 0, "זירו" to 0, "אפס" to 0,
        "one" to 1, "ואן" to 1, "וואן" to 1, "אחד" to 1, "אחת" to 1,
        "two" to 2, "טו" to 2, "תו" to 2, "שניים" to 2, "שתיים" to 2,
        "three" to 3, "טרי" to 3, "תרי" to 3, "שלוש" to 3, "שלושה" to 3,
        "four" to 4, "פור" to 4, "פורר" to 4, "ארבע" to 4, "ארבעה" to 4,
        "five" to 5, "פייב" to 5, "חמש" to 5, "חמישה" to 5,
        "six" to 6, "סיקס" to 6, "שש" to 6, "שישה" to 6,
        "seven" to 7, "סבן" to 7, "שבע" to 7, "שבעה" to 7,
        "eight" to 8, "אייט" to 8, "איט" to 8, "שמונה" to 8,
        "nine" to 9, "ניין" to 9, "נין" to 9, "תשע" to 9, "תשעה" to 9,
    )
    val words = value.replace('-', ' ').split(' ').filter(String::isNotBlank)
    if (words.size < 2) return null

    var result = 0
    for (word in words) {
        val digit = digitWords[word] ?: return null
        result = result * 10 + digit
    }
    return result
}

private fun englishNumber(value: String): Int? {
    val words = value.replace('-', ' ').split(' ').filter(String::isNotBlank)
    if (words.isEmpty()) return null

    val units = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16,
        "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
    )
    val tens = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )
    if (words == listOf("one", "hundred")) return 100
    if (words.size == 1) return units[words[0]] ?: tens[words[0]]
    if (words.size == 2) {
        val tensValue = tens[words[0]] ?: return null
        val unitValue = units[words[1]] ?: return null
        if (unitValue in 1..9) return tensValue + unitValue
    }
    return null
}

private fun hebrewNumber(value: String): Int? {
    val words = value.replace('-', ' ').split(' ').filter(String::isNotBlank)
    if (words.isEmpty()) return null

    val units = mapOf(
        "אפס" to 0,
        "אחד" to 1, "אחת" to 1,
        "שניים" to 2, "שתיים" to 2, "שני" to 2, "שתי" to 2,
        "שלוש" to 3, "שלושה" to 3,
        "ארבע" to 4, "ארבעה" to 4,
        "חמש" to 5, "חמישה" to 5,
        "שש" to 6, "שישה" to 6,
        "שבע" to 7, "שבעה" to 7,
        "שמונה" to 8,
        "תשע" to 9, "תשעה" to 9,
        "עשר" to 10, "עשרה" to 10,
    )
    val tens = mapOf(
        "עשרים" to 20, "טוונטי" to 20, "טוונ्टी" to 20, "טווניטי" to 20,
        "שלושים" to 30,
        "ארבעים" to 40,
        "חמישים" to 50,
        "שישים" to 60,
        "שבעים" to 70,
        "שמונים" to 80,
        "תשעים" to 90,
    )

    if (words == listOf("מאה")) return 100
    if (words.size == 1) return units[words[0]] ?: tens[words[0]]
    if (words.size == 2) {
        val firstUnit = units[words[0]]
        if (firstUnit in 1..9 && words[1] in setOf("עשר", "עשרה")) {
            return 10 + firstUnit!!
        }

        val tensValue = tens[words[0]] ?: return null
        val unitWord = words[1].removePrefix("ו")
        val unitValue = units[unitWord] ?: return null
        if (unitValue in 1..9) return tensValue + unitValue
    }
    return null
}

package com.example.mediaplayer.data

import java.text.Normalizer
import java.util.regex.Pattern

fun String.removeAccents(): String {
    val temp = Normalizer.normalize(this, Normalizer.Form.NFD)
    val pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
    return pattern.matcher(temp).replaceAll("")
        .replace('đ', 'd')
        .replace('Đ', 'D')
}

fun String.matchesFuzzy(query: String): Boolean {
    val queryWords = query.trim().removeAccents().lowercase().split(Regex("\\s+"))
    val targetCleaned = this.removeAccents().lowercase()
    return queryWords.all { word -> targetCleaned.contains(word) }
}

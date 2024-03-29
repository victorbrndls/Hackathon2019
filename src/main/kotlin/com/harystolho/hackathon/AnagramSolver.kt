package com.harystolho.hackathon

import com.harystolho.hackathon.data.WordRepository
import kotlinx.coroutines.*
import mu.KotlinLogging
import java.util.*
import java.util.concurrent.*
import kotlin.Comparator
import kotlin.coroutines.coroutineContext

private val logger = KotlinLogging.logger { }

class AnagramSolver(private val wordRepository: WordRepository) {

    /**
     * @throws IllegalArgumentException if the given [phrase] is not accepted by this solver
     */
    fun findAnagrams(phrase: String): Set<String> {
        if (phrase.isEmpty()) return emptySet()

        val phraseToProcess = formatPhrase(phrase)

        verifyPhrase(phraseToProcess)

        val validWords = wordRepository.readWords()
        logger.info { "${validWords.size} valid words" }

        val possibleWords = removeInvalidWords(phraseToProcess, validWords)
        logger.info { "${possibleWords.size} possible words" }

        val noDuplicateChars = removeDuplicateChars(phraseToProcess, possibleWords)
        logger.info { "${noDuplicateChars.size} possible words" }

        val result = runAnagramBuilder(phraseToProcess, noDuplicateChars)
        logger.info { "${result.size} possible words" }

        return result
    }

    /**
     * Removes words that have characters not present in [phrase]. If the phrase is 'car', the word
     * 'house' is removed because 'car' doesn't contain 'h'
     */
    private fun removeInvalidWords(phrase: String, validWords: List<String>): List<String> {
        val distinctPhrase = phrase.toCharArray().distinct() // Keep only distinct chars

        return validWords.filter { word ->
            word.toCharArray().distinct().forEach { char ->
                if (!distinctPhrase.contains(char)) return@filter false
            }

            true
        }
    }

    /**
     * Removes words that have the same chars as [phrase] but in greater amount. If the phrase is
     * 'maracuja azedo', the word 'armour' is removed because even though 'maracuja azedo' has all
     * chars 'armour' has, 'armour' has 2 'r' and 'maracuja azedo' has only 1
     */
    private fun removeDuplicateChars(phrase: String, validWords: List<String>): List<String> {
        return validWords.filter { word ->
            // if the phrase is 'helo', the word 'hello' would be removed in here
            val equalChars = phrase.filter { word.contains(it) }
            if (equalChars.length < word.length) return@filter false

            val phraseChars = phrase.map { char -> char }.toMutableList()
            word.forEach { char -> if (!phraseChars.remove(char)) return@filter false }

            true
        }
    }

    /**
     * Executes [AnagramBuilder] using concurrency to improve the time it takes to find all anagrams
     */
    private fun runAnagramBuilder(phrase: String, orderedWords: List<String>): Set<String> {
        val anagramBuilder = AnagramBuilder(phrase)

        runBlocking {
            withContext(Dispatchers.Default) {
                orderedWords.forEach { word ->
                    launch { anagramBuilder.build(mutableListOf(word), orderedWords) }
                }
            }
        }

        return anagramBuilder.result
    }

    private fun formatPhrase(phrase: String): String {
        return phrase
                .replace(" ", "")
                .toUpperCase()
    }

    /**
     * @throws IllegalArgumentException if the given [phrase] contains an invalid character
     */
    private fun verifyPhrase(phrase: String) {
        val chars = phrase.toByteArray()

        chars.forEach { char ->
            // 'A' is 65 and 'Z' is 90 in the ASCII table
            if (char < 65 || char > 90) throw IllegalArgumentException("Invalid character")
        }
    }

}

/**
 * This class is Thread safe
 */
private class AnagramBuilder(phrase: String) {

    val result: MutableSet<String> = Collections.synchronizedSet(TreeSet<String>())

    private val phraseCharCount = phrase.groupBy { it }.entries.associate { it.key to it.value.size }
    private val phraseLength = phrase.length

    fun build(builtSoFar: MutableList<String>, dictionary: List<String>) {
        val builtSoFarString = builtSoFar.joinToString(separator = "")

        val possibleWords = removeInvalidWords(dictionary, builtSoFarString)

        for (i in (possibleWords.size - 1) downTo 0) {
            val nextWord = possibleWords[i]

            if (builtSoFar.contains(nextWord)) continue

            val clone = builtSoFar.toMutableList().apply { add(nextWord) }

            if (clone.sumBy { it.length } >= phraseLength) {
                clone.sortWith(Comparator { a, b -> a.compareTo(b) })
                result.add(clone.joinToString(separator = " "))
                continue
            }

            build(clone, possibleWords)
        }
    }

    /**
     * Remove words that can't be joined to build [phrase]. If the phrase is 'vermelho' and [builtSoFarString]
     * is 'elm', all words that contain 'l' or 'm' are removed because 'vermelho' has only 1 of these
     * letters but 'e' is not because 'vermelho' has 2 'e'
     */
    private fun removeInvalidWords(dictionary: List<String>, builtSoFarString: String): List<String> {
        val builtSoFarCharCount = builtSoFarString.groupBy { it }.entries
                .associate { it.key to it.value.size }
        val builtSoFarLength = builtSoFarString.length

        return dictionary.filter { word ->
            if (builtSoFarLength + word.length > phraseLength) return@filter false

            for (char in word) {
                if (builtSoFarString.contains(char)) {
                    if ((phraseCharCount[char] ?: 0) - (builtSoFarCharCount[char]
                                    ?: 0) < word.count { it == char }) return@filter false
                }
            }
            true
        }
    }

}
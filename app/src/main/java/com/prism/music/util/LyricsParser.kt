package com.prism.music.util

import java.util.UUID
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

data class LyricsWord(
    val time: Double,
    val text: String
)

data class LyricsLine(
    val id: String = UUID.randomUUID().toString(),
    val time: Double,
    val endTime: Double?,
    val text: String,
    val words: List<LyricsWord>?,
    val isPause: Boolean = false
) {
    val hasWords: Boolean
        get() = !words.isNullOrEmpty()

    val duration: Double?
        get() = if (endTime != null && endTime > time) endTime - time else null
}

data class ParsedLyrics(
    val lines: List<LyricsLine>
) {
    val isSynced: Boolean
        get() = lines.any { it.time >= 0 }
}

object LyricsParser {

    fun parse(raw: String, duration: Double? = null): ParsedLyrics {
        val offset = extractOffset(raw)
        
        val tsPattern = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]")
        val wordPattern = Pattern.compile("<(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?>")
        val metaPattern = Pattern.compile("^\\[(ti|ar|al|au|by|re|ve|length|offset|id|tool|hash):", Pattern.CASE_INSENSITIVE)

        val lines = mutableListOf<LyricsLine>()
        val rawLines = raw.split("\n")

        for (rawLine in rawLines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (metaPattern.matcher(line).find()) continue

            val tsMatcher = tsPattern.matcher(line)
            val tsMatches = mutableListOf<Pair<Int, Int>>() // list of match ranges
            while (tsMatcher.find()) {
                tsMatches.add(Pair(tsMatcher.start(), tsMatcher.end()))
            }
            if (tsMatches.isEmpty()) continue

            // The lyric body follows the last line-level timestamp
            val lastMatch = tsMatches.last()
            var body = line.substring(lastMatch.second).trim()

            // Clean up empty parens and brackets
            body = body.replace(Regex("\\(\\s*\\)"), "")
                .replace(Regex("\\[\\s*\\]"), "")
                .trim()

            // Decode first timestamp to use for word-level calculations
            val firstMatcher = tsPattern.matcher(line)
            firstMatcher.find()
            val baseTimeWithoutOffset = decodeTimestamp(firstMatcher, 0.0) ?: 0.0

            // Optionally extract word-level timestamps
            val (cleanText, words) = parseWords(body, wordPattern, baseTimeWithoutOffset)

            val baseTime = decodeTimestamp(firstMatcher, offset) ?: 0.0
            
            // Generate lines for each timestamp in tsMatches
            val tsMatcher2 = tsPattern.matcher(line)
            var tsIdx = 0
            while (tsMatcher2.find()) {
                val t = decodeTimestamp(tsMatcher2, offset) ?: continue
                val baseShift = t - baseTime
                val adjustedWords = if (words.isEmpty()) null else words.map { word ->
                    LyricsWord(time = word.time + offset + baseShift, text = word.text)
                }
                lines.add(
                    LyricsLine(
                        time = t,
                        endTime = null,
                        text = cleanText,
                        words = adjustedWords
                    )
                )
                tsIdx++
            }
        }

        // Sort by time
        val sortedLines = lines.sortedBy { it.time }.toMutableList()
        for (i in sortedLines.indices) {
            val endTime = if (i + 1 < sortedLines.size) sortedLines[i + 1].time else null
            val line = sortedLines[i]
            sortedLines[i] = LyricsLine(
                id = line.id,
                time = line.time,
                endTime = endTime,
                text = line.text,
                words = line.words ?: synthesizeWords(
                    text = line.text,
                    start = line.time,
                    end = endTime ?: (line.time + 4.0)
                )
            )
        }

        val linesWithPauses = mutableListOf<LyricsLine>()
        val pauseThreshold = 4.0

        val syncedLines = sortedLines.filter { it.time >= 0 }
        val unsyncedLines = sortedLines.filter { it.time < 0 }

        if (syncedLines.isNotEmpty()) {
            // 1. Intro pause
            val firstLine = syncedLines[0]
            if (firstLine.time > 5.0) {
                linesWithPauses.add(
                    LyricsLine(
                        time = 0.0,
                        endTime = firstLine.time - 1.0,
                        text = "...",
                        words = null,
                        isPause = true
                    )
                )
            }

            // 2. Middle pauses
            for (i in syncedLines.indices) {
                val line = syncedLines[i]
                linesWithPauses.add(line)

                if (i + 1 < syncedLines.size) {
                    val nextLine = syncedLines[i + 1]
                    var singingEndTime = line.time
                    val words = line.words
                    if (!words.isNullOrEmpty()) {
                        val lastWord = words.last()
                        val lastWordDur = min(1.5, (line.endTime ?: (lastWord.time + 1.5)) - lastWord.time)
                        singingEndTime = lastWord.time + lastWordDur
                    } else {
                        singingEndTime = line.endTime ?: (line.time + 4.0)
                    }

                    val gap = nextLine.time - singingEndTime
                    if (gap >= pauseThreshold) {
                        linesWithPauses.add(
                            LyricsLine(
                                time = singingEndTime + 0.5,
                                endTime = nextLine.time - 1.0,
                                text = "...",
                                words = null,
                                isPause = true
                            )
                        )
                    }
                }
            }

            // 3. Outro pause
            if (duration != null && duration > 0) {
                val lastLine = syncedLines.last()
                var singingEndTime = lastLine.time
                val words = lastLine.words
                if (!words.isNullOrEmpty()) {
                    val lastWord = words.last()
                    val lastWordDur = min(1.5, (lastLine.endTime ?: (lastWord.time + 1.5)) - lastWord.time)
                    singingEndTime = lastWord.time + lastWordDur
                } else {
                    singingEndTime = lastLine.endTime ?: (lastLine.time + 4.0)
                }

                if (duration - singingEndTime >= 5.0) {
                    linesWithPauses.add(
                        LyricsLine(
                            time = singingEndTime + 0.5,
                            endTime = duration - 1.0,
                            text = "...",
                            words = null,
                            isPause = true
                        )
                    )
                }
            }
        } else {
            linesWithPauses.addAll(unsyncedLines)
        }

        return ParsedLyrics(lines = linesWithPauses.sortedBy { it.time })
    }

    private fun extractOffset(raw: String): Double {
        val pattern = Pattern.compile("\\[offset:\\s*(-?\\d+)\\s*\\]", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(raw)
        if (matcher.find()) {
            val value = matcher.group(1) ?: return 0.0
            return (value.toDoubleOrNull() ?: 0.0) / 1000.0
        }
        return 0.0
    }

    private fun decodeTimestamp(matcher: java.util.regex.Matcher, offset: Double): Double? {
        try {
            val minutes = matcher.group(1)?.toDoubleOrNull() ?: 0.0
            val seconds = matcher.group(2)?.toDoubleOrNull() ?: 0.0
            var fraction = 0.0
            if (matcher.groupCount() >= 3) {
                val raw = matcher.group(3)
                if (raw != null) {
                    val padded = raw.padEnd(3, '0').substring(0, 3)
                    fraction = (padded.toDoubleOrNull() ?: 0.0) / 1000.0
                }
            }
            return minutes * 60.0 + seconds + fraction + offset
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseWords(body: String, pattern: Pattern, firstLineTimestampWithoutOffset: Double): Pair<String, List<LyricsWord>> {
        val words = mutableListOf<LyricsWord>()
        val matcher = pattern.matcher(body)
        var cleanText = body

        if (body.contains("<")) {
            val matches = mutableListOf<Triple<Int, Int, Double>>()
            val matcher2 = pattern.matcher(body)
            while (matcher2.find()) {
                val minutes = matcher2.group(1)?.toDoubleOrNull() ?: 0.0
                val seconds = matcher2.group(2)?.toDoubleOrNull() ?: 0.0
                var fraction = 0.0
                if (matcher2.groupCount() >= 3) {
                    val raw = matcher2.group(3)
                    if (raw != null) {
                        val padded = raw.padEnd(3, '0').substring(0, 3)
                        fraction = (padded.toDoubleOrNull() ?: 0.0) / 1000.0
                    }
                }
                val time = minutes * 60.0 + seconds + fraction
                matches.add(Triple(matcher2.start(), matcher2.end(), time))
            }

            if (matches.isNotEmpty()) {
                val firstMatchStart = matches.first().first
                if (firstMatchStart > 0) {
                    val prefix = body.substring(0, firstMatchStart).trim()
                    if (prefix.isNotEmpty()) {
                        val cleanPrefix = prefix.replace(Regex("\\(\\s*\\)"), "").trim()
                        if (cleanPrefix.isNotEmpty()) {
                            words.add(LyricsWord(time = firstLineTimestampWithoutOffset, text = cleanPrefix))
                        }
                    }
                }

                for (i in matches.indices) {
                    val match = matches[i]
                    val time = match.third
                    val textStart = match.second
                    val textEnd = if (i + 1 < matches.size) matches[i + 1].first else body.length
                    val text = body.substring(textStart, textEnd).trim()
                    val cleanWText = text.replace(Regex("\\(\\s*\\)"), "").trim()
                    if (cleanWText.isNotEmpty()) {
                        words.add(LyricsWord(time = time, text = cleanWText))
                    }
                }
            }
        }

        // Strip <...> tags
        val m = pattern.matcher(body)
        cleanText = m.replaceAll("").replace("  ", " ").trim()
        
        return Pair(cleanText, words)
    }

    private fun synthesizeWords(text: String, start: Double, end: Double): List<LyricsWord>? {
        val rawWords = text.split(" ").filter { it.isNotEmpty() }
        if (rawWords.isEmpty()) return null

        val weights = rawWords.map { w ->
            val lettersCount = w.filter { c ->
                (c in 'a'..'z') || (c in 'A'..'Z') || (c in 'а'..'я') || (c in 'А'..'Я') || c == 'ё' || c == 'Ё'
            }.length
            val letters = if (lettersCount > 0) lettersCount.toDouble() else 1.0
            var weight = sqrt(letters)
            
            if (w.isNotEmpty()) {
                val lastChar = w.last()
                if (lastChar == '.' || lastChar == '!' || lastChar == '?' || lastChar == '…') {
                    weight += 0.6
                } else if (lastChar == ',' || lastChar == ';' || lastChar == ':' || lastChar == '—' || lastChar == '–' || lastChar == '-') {
                    weight += 0.3
                }
            }
            weight
        }

        val totalWeight = weights.sum()
        val gap = end - start
        
        val baseSingingDur = rawWords.size * 0.45
        val maxSingingDur = rawWords.size * 0.55 + 1.2
        val singingGap = min(
            maxSingingDur,
            min(gap * 0.75, maxOf(gap * 0.4, baseSingingDur))
        )

        val durPerUnitWeight = if (totalWeight > 0.0) singingGap / totalWeight else 0.0

        val synthesizedWords = mutableListOf<LyricsWord>()
        var currentStartTime = start

        for (wIdx in rawWords.indices) {
            val wText = rawWords[wIdx]
            val baseDur = weights[wIdx] * durPerUnitWeight
            
            val timeMs = if (start.isFinite()) (start * 1000.0).toInt() else 0
            val seed = abs((wIdx * 131 + timeMs) % 1000)
            val jitter = 1.0 + (((seed.toDouble() / 1000.0) * 0.3) - 0.15)
            val finalDur = baseDur * jitter

            synthesizedWords.add(LyricsWord(time = currentStartTime, text = wText))
            currentStartTime += finalDur
        }

        return synthesizedWords
    }
}

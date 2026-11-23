package com.ashok.tamilsubvoice

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.common.model.DownloadConditions
import kotlinx.coroutines.tasks.await

class Translator {

    private val options = TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.ENGLISH)
        .setTargetLanguage(TranslateLanguage.TAMIL)
        .build()

    private val client = Translation.getClient(options)

    suspend fun prepare() {
        val conditions = DownloadConditions.Builder().build()
        client.downloadModelIfNeeded(conditions).await()
    }

    suspend fun translateAll(cues: List<Cue>, onProgress: (Int, Int) -> Unit) {
        for ((i, cue) in cues.withIndex()) {
            cue.tamil = try {
                client.translate(cue.text).await()
            } catch (e: Exception) {
                cue.text
            }
            onProgress(i + 1, cues.size)
        }
    }

    fun close() = client.close()
}

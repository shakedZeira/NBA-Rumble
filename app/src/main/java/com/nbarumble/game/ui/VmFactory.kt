package com.nbarumble.game.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Friendly ViewModel factory for manual DI (no framework). */
inline fun <reified VM : ViewModel> vmFactory(crossinline create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }

/** 6-char room codes from an unambiguous alphabet. */
object RoomCode {
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    fun generate(length: Int = 6): String =
        buildString { repeat(length) { append(ALPHABET.random()) } }

    fun sanitize(input: String): String =
        input.trim().uppercase().filter { it in ALPHABET }.take(6)
}
package com.nova.browser

import androidx.compose.runtime.mutableStateListOf

object Groups {
    const val ALL = -1
    const val NONE = 0
    const val PRIVATE = 99

    val names = mutableStateListOf<Pair<Int, String>>()

    fun add(name: String): Int {
        val id = (names.maxOfOrNull { it.first } ?: 0) + 1
        names.add(id to name)
        return id
    }

    fun rename(id: Int, name: String) {
        val i = names.indexOfFirst { it.first == id }
        if (i >= 0) names[i] = id to name
    }

    fun remove(id: Int) {
        names.removeAll { it.first == id }
    }

    fun nameOf(id: Int): String = names.firstOrNull { it.first == id }?.second ?: ""
}

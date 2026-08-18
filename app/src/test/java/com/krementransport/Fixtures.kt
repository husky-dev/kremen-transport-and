package com.krementransport

/** Real payloads captured from the live API, shared with the iOS app's test suite. */
object Fixtures {
    fun read(name: String): String = checkNotNull(
        Fixtures::class.java.classLoader?.getResourceAsStream("$name.json"),
    ) { "missing fixture $name.json" }.bufferedReader().use { it.readText() }
}

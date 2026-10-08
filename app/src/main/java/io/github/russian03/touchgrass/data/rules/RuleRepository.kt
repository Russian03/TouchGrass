package io.github.russian03.touchgrass.data.rules

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.russian03.touchgrass.core.rules.RuleSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class RuleRepository @Inject constructor(@param:ApplicationContext private val context: Context) {
    fun load(): List<RuleSet> = context.assets.list(RULES_DIR).orEmpty()
        .filter { it.endsWith(".json") }
        .map { file ->
            context.assets.open("$RULES_DIR/$file").bufferedReader().use { parse(it.readText()) }
        }

    companion object {
        private const val RULES_DIR = "rules"

        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): RuleSet = json.decodeFromString(text)
    }
}

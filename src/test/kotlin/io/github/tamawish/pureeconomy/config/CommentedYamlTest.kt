package io.github.tamawish.pureeconomy.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentedYamlTest {
    // BoostedYAML may keep the YAML file open on Windows; NEVER avoids TempDir cleanup failures.
    @TempDir(cleanup = CleanupMode.NEVER)
    lateinit var tempDir: File

    @Test
    fun keepAllPreservesCustomDiskKeys() {
        val file = File(tempDir, "config.yml")
        Files.writeString(
            file.toPath(),
            """
      |language: en
      |custom-extra: kept
      |
            """.trimMargin(),
            StandardCharsets.UTF_8,
        )
        val jarDefaults =
            """
      |language: en
      |autosave-seconds: 60
      |
            """.trimMargin()

        val document =
            CommentedYaml.load(
                file,
                ByteArrayInputStream(jarDefaults.toByteArray(StandardCharsets.UTF_8)),
            )

        assertEquals("kept", document.getString("custom-extra"))
        assertTrue(document.contains("custom-extra"))
    }

    @Test
    fun missingJarKeyIsInserted() {
        val file = File(tempDir, "config.yml")
        Files.writeString(
            file.toPath(),
            """
      |language: fr
      |
            """.trimMargin(),
            StandardCharsets.UTF_8,
        )
        val jarDefaults =
            """
      |language: en
      |autosave-seconds: 60
      |
            """.trimMargin()

        val document =
            CommentedYaml.load(
                file,
                ByteArrayInputStream(jarDefaults.toByteArray(StandardCharsets.UTF_8)),
            )

        assertEquals("fr", document.getString("language"))
        assertEquals(60L, document.getLong("autosave-seconds"))
    }

    @Test
    fun permissionDefaultTrueIsABooleanNotAString() {
        val file = File(tempDir, "permissions.yml")
        val yaml =
            """
      |permissions:
      |  balance:
      |    default: true
      |  bank:
      |    default: everyone
      |  eco:
      |    default: op
      |
            """.trimMargin()
        Files.writeString(file.toPath(), yaml, StandardCharsets.UTF_8)

        val document =
            CommentedYaml.load(
                file,
                ByteArrayInputStream(yaml.toByteArray(StandardCharsets.UTF_8)),
            )

        assertTrue(document.get("permissions.balance.default") is Boolean)
        assertEquals(true, document.getBoolean("permissions.balance.default"))
        assertEquals("everyone", document.getString("permissions.bank.default"))
        assertEquals("op", document.getString("permissions.eco.default"))
    }
}

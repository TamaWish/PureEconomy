package io.github.tamawish.pureeconomy.config

import dev.dejvokep.boostedyaml.YamlDocument
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings
import java.io.File
import java.io.InputStream

/**
 * Creates a BoostedYAML [YamlDocument] with comment-preserving merge defaults.
 *
 * - No [dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning] — every load/reload merges missing
 *   jar keys.
 * - [UpdaterSettings.setKeepAll] is mandatory so disk-only keys (extra currencies, lang strings)
 *   survive.
 */
object CommentedYaml {
    /**
     * Loads or creates [file] using [jarStream] as the defaults template.
     *
     * @param file on-disk YAML path
     * @param jarStream jar resource stream (defaults); caller should close if needed after create
     * @param separator route separator; default `.` matches Bukkit-style paths
     */
    fun load(
        file: File,
        jarStream: InputStream,
        separator: Char = '.',
    ): YamlDocument =
        YamlDocument.create(
            file,
            jarStream,
            GeneralSettings.builder().setRouteSeparator(separator).build(),
            LoaderSettings.builder().setAutoUpdate(true).build(),
            DumperSettings.DEFAULT,
            UpdaterSettings
                .builder()
                .setKeepAll(true)
                .setOptionSorting(UpdaterSettings.OptionSorting.SORT_BY_DEFAULTS)
                .build(),
        )
}

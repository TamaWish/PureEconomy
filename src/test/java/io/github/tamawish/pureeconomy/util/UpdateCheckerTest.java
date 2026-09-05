package io.github.tamawish.pureeconomy.util;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {

    @Test
    void detectsNewerRemoteVersions() {
        assertTrue(UpdateChecker.compareVersions("1.0.1", "1.0.2") < 0);
        assertTrue(UpdateChecker.compareVersions("1.0.1", "v1.1.0") < 0);
        assertEquals(0, UpdateChecker.compareVersions("1.0.1", "v1.0.1"));
        assertTrue(UpdateChecker.compareVersions("1.1.0", "1.0.1") > 0);
    }

    @Test
    void parsesLatestReleaseFields() {
        String json = "{\"tag_name\":\"v1.1.0\","
                + "\"html_url\":\"https://github.com/TamaWish/PureEconomy/releases/tag/v1.1.0\"}";

        assertEquals("v1.1.0", UpdateChecker.firstJsonString(
                json, Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"")));
        assertEquals(
                "https://github.com/TamaWish/PureEconomy/releases/tag/v1.1.0",
                UpdateChecker.firstJsonString(
                        json, Pattern.compile("\"html_url\"\\s*:\\s*\"([^\"]+)\"")));
    }
}

package io.github.tamawish.pureeconomy.permission

import org.bukkit.permissions.PermissionDefault
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PermissionsTest {
    @Test
    fun parseDefaultAcceptsBukkitTrueOpFalse() {
        assertEquals(PermissionDefault.TRUE, Permissions.parseDefault(true, PermissionDefault.OP))
        assertEquals(PermissionDefault.FALSE, Permissions.parseDefault(false, PermissionDefault.OP))
        assertEquals(PermissionDefault.TRUE, Permissions.parseDefault("true", PermissionDefault.OP))
        assertEquals(PermissionDefault.TRUE, Permissions.parseDefault("TRUE", PermissionDefault.OP))
        assertEquals(PermissionDefault.OP, Permissions.parseDefault("op", PermissionDefault.TRUE))
        assertEquals(PermissionDefault.FALSE, Permissions.parseDefault("false", PermissionDefault.TRUE))
    }

    @Test
    fun parseDefaultMapsLegacyEveryoneAndNobody() {
        assertEquals(
            PermissionDefault.TRUE,
            Permissions.parseDefault("everyone", PermissionDefault.OP),
        )
        assertEquals(
            PermissionDefault.FALSE,
            Permissions.parseDefault("nobody", PermissionDefault.OP),
        )
    }

    @Test
    fun parseDefaultFallsBackWhenBlankOrUnknown() {
        assertEquals(PermissionDefault.TRUE, Permissions.parseDefault(null, PermissionDefault.TRUE))
        assertEquals(PermissionDefault.TRUE, Permissions.parseDefault("", PermissionDefault.TRUE))
        assertEquals(PermissionDefault.OP, Permissions.parseDefault("maybe", PermissionDefault.OP))
    }
}

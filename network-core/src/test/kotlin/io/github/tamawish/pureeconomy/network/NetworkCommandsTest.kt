package io.github.tamawish.pureeconomy.network

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkCommandsTest {
    @Test
    fun publicCommandsDoNotRequireAnExplicitProxyGrant() {
        listOf(
            "pureeconomy.balance",
            "pureeconomy.bank",
            "pureeconomy.bank.transfer",
            "pureeconomy.bank.withdraw",
            "pureeconomy.pay",
            "pureeconomy.baltop",
            "pureeconomy.currency",
        ).forEach { assertFalse(NetworkPermissionPolicy.requiresExplicitGrant(it), it) }
    }

    @Test
    fun restrictedCommandsStillRequireAnExplicitProxyGrant() {
        assertTrue(NetworkPermissionPolicy.requiresExplicitGrant("pureeconomy.balance.others"))
        assertTrue(NetworkPermissionPolicy.requiresExplicitGrant("pureeconomy.eco.give"))
        assertTrue(NetworkPermissionPolicy.requiresExplicitGrant("pureeconomy.eco.reload"))
    }

    @Test
    fun proxyParentNodesGrantTheirChildren() {
        assertTrue("pureeconomy.admin" in NetworkPermissionPolicy.grantsFor("pureeconomy.balance.others"))
        assertTrue("pureeconomy.eco" in NetworkPermissionPolicy.grantsFor("pureeconomy.eco.give"))
        assertTrue("pureeconomy.eco.bank" in NetworkPermissionPolicy.grantsFor("pureeconomy.eco.bank.set"))
        assertTrue("pureeconomy.admin" in NetworkPermissionPolicy.grantsFor("pureeconomy.eco.bank.set"))
    }
}

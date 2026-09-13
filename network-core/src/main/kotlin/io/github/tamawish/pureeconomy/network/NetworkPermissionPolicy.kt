package io.github.tamawish.pureeconomy.network

/** Permission defaults and parent grants shared by the Velocity and Bungee command front ends. */
object NetworkPermissionPolicy {
    private val publicPermissions =
        setOf(
            "pureeconomy.balance",
            "pureeconomy.bank",
            "pureeconomy.bank.transfer",
            "pureeconomy.bank.withdraw",
            "pureeconomy.pay",
            "pureeconomy.baltop",
            "pureeconomy.currency",
        )

    fun requiresExplicitGrant(permission: String): Boolean = permission !in publicPermissions

    fun grantsFor(permission: String): List<String> =
        buildList {
            add(permission)
            when {
                permission == "pureeconomy.balance.others" -> add("pureeconomy.admin")
                permission.startsWith("pureeconomy.eco.bank.") -> {
                    add("pureeconomy.eco.bank")
                    add("pureeconomy.eco")
                    add("pureeconomy.admin")
                }
                permission.startsWith("pureeconomy.eco.") -> {
                    add("pureeconomy.eco")
                    add("pureeconomy.admin")
                }
                permission == "pureeconomy.eco" -> add("pureeconomy.admin")
            }
        }
}

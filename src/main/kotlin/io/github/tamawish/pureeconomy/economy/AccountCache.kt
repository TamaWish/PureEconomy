package io.github.tamawish.pureeconomy.economy

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Caffeine hot-account cache with dirty eviction for local storage. */
class AccountCache(
    maxAccounts: Long,
    expireMinutes: Long,
    private val onDirtyEvicted: () -> Unit,
) {
    private val evictedDirty = ConcurrentLinkedQueue<PlayerAccount>()
    private val dirtySaveQueued = AtomicBoolean()
    private val accounts: Cache<UUID, PlayerAccount> =
        Caffeine
            .newBuilder()
            .maximumSize(maxAccounts)
            .expireAfterAccess(expireMinutes, TimeUnit.MINUTES)
            // Queue evicted dirty accounts before the invalidating call returns. Persistence itself
            // is still dispatched by onDirtyEvicted and never runs on Caffeine's maintenance path.
            .executor { command -> command.run() }
            .removalListener<UUID, PlayerAccount> { _, account, _ ->
                if (account != null && account.dirty()) {
                    evictedDirty.add(account)
                    onDirtyEvicted()
                }
            }.build()

    fun getOrLoad(
        uuid: UUID,
        load: (UUID) -> PlayerAccount,
    ): PlayerAccount = accounts.get(uuid, load)!!

    fun getIfPresent(uuid: UUID): PlayerAccount? = accounts.getIfPresent(uuid)

    fun asMap(): MutableMap<UUID, PlayerAccount> = accounts.asMap()

    fun invalidate(uuid: UUID) {
        accounts.invalidate(uuid)
    }

    internal fun cleanUp() {
        accounts.cleanUp()
    }

    fun tryQueueDirtySave(): Boolean = dirtySaveQueued.compareAndSet(false, true)

    fun finishDirtySave() {
        dirtySaveQueued.set(false)
    }

    fun collectDirty(): List<PlayerAccount> {
        val dirty = ArrayList<PlayerAccount>()
        for (account in accounts.asMap().values) {
            if (account.dirty()) {
                dirty.add(account)
            }
        }
        while (true) {
            val evicted = evictedDirty.poll() ?: break
            if (evicted.dirty()) {
                dirty.add(evicted)
            }
        }
        return dirty
    }

    /** Non-destructive check used when deciding whether another save pass is necessary. */
    fun hasDirty(): Boolean = evictedDirty.isNotEmpty() || accounts.asMap().values.any(PlayerAccount::dirty)

    fun allCachedAndEvicted(): List<PlayerAccount> {
        val cached = accounts.asMap().values.toList()
        val evicted = ArrayList<PlayerAccount>()
        while (true) {
            val item = evictedDirty.poll() ?: break
            evicted.add(item)
        }
        val all = ArrayList<PlayerAccount>(cached.size + evicted.size)
        all.addAll(cached)
        all.addAll(evicted)
        return all
    }
}

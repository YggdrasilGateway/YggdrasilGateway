package com.kasukusakura.yggdrasilgateway.yggdrasil.impl.codesnippet

import com.kasukusakura.yggdrasilgateway.api.eventbus.EventSubscriber
import com.kasukusakura.yggdrasilgateway.core.module.codesnippets.CodeSnippetModule
import com.kasukusakura.yggdrasilgateway.yggdrasil.impl.codesnippet.YggdrasilCodeSnippets.NameConflictResolver
import com.kasukusakura.yggdrasilgateway.yggdrasil.impl.codesnippet.YggdrasilCodeSnippets.UUIDConflictResolver
import com.kasukusakura.yggdrasilgateway.yggdrasil.impl.sys.YggdrasilServicesHolder
import java.util.*

@EventSubscriber
internal object YggdrasilCodeSnippets {
    fun interface UUIDConflictResolver {
        fun next(upstreamName: String, upstreamUUID: UUID, prevTried: MutableSet<UUID>): UUID
    }

    fun interface NameConflictResolver {
        fun next(upstreamName: String, upstreamUUID: UUID, prevTried: MutableSet<String>): String
    }


    val uuidConflictResolver = CodeSnippetModule.registerSnippet(
        UUIDConflictResolver::class.java,
        "yggdrasil.uuid-conflict-resolver",
        "Yggdrasil UUID Conflict Resolver",
        """
        import java.util.Set;
        import java.util.UUID;
        public class YggdrasilUuidConflictResolver {
            public static UUID resolve(String upstreamName, UUID upstreamUUID, Set<String> prevTried) {
                return UUID.randomUUID();
            }
        }
        """.trimIndent(),
        UUIDConflictResolver { _, _, _ -> UUID.randomUUID() },
    )
    val nameConflictResolver = CodeSnippetModule.registerSnippet(
        NameConflictResolver::class.java,
        "yggdrasil.name-conflict-resolver",
        "Yggdrasil Username Conflict Resolver",
        """
        import java.util.Set;
        import java.util.UUID;
        public class YggdrasilNameConflictResolver {
            public static String resolve(String upstreamName, UUID upstreamUUID, Set<String> prevTried) {
                return upstreamName + prevTried.size();
            }
        }
        """.trimIndent(),
        NameConflictResolver { upstreamName, _, _ ->
            "$upstreamName${YggdrasilServicesHolder.generateEntryId(size = 4)}"
        },
    )
}
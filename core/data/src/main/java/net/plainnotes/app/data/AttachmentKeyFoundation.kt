package net.plainnotes.app.data

import java.util.UUID

/** Roles are deliberately separate from existing DatabaseAccess encryption keys.
 * No production wrapper, portable encoding or key creation is supplied in B1.
 */
internal object AttachmentKeyFoundation {
    enum class Role { DATABASE, ATTACHMENT_VAULT_KEK, OBJECT_VERSION_DEK, DEVICE_WRAP, PORTABLE_BACKUP }
    data class Scope(val localDataset:UUID, val logicalVault:UUID, val recordEpoch:UUID)
    data class VersionKey(val scope:Scope, val version:UUID, val dekHandle:UUID)
    data class DeviceEnvelope(val key:VersionKey, val wrapEpoch:Long, val deviceAlias:String, val wrapped:ByteArray)
    class MissingKey : IllegalStateException("Attachment key unavailable; recovery required")
    interface DeviceKeyProvider {
        /** Must fail closed on missing alias. Never create/replace an alias in response to unwrap. */
        fun unwrapExisting(envelope:DeviceEnvelope):ByteArray
    }
    interface PortableKeyProvider {
        /** Future independently-reviewed backup format. No implementation in B1. */
        fun recover(logicalVault:UUID, version:UUID, dekHandle:UUID):ByteArray
    }
    /** PRIVATE/DECOY never receive an attachment namespace, alias, directory or dedup index. */
    fun primaryScope(space:Space, scope:Scope):Scope { check(space==Space.PRIMARY);return scope }
    fun existing(provider:DeviceKeyProvider,envelope:DeviceEnvelope):ByteArray {
        require(envelope.wrapEpoch>=1 && envelope.deviceAlias.startsWith("hrt.attachment.primary."))
        return provider.unwrapExisting(envelope).also { if(it.size!=32){it.fill(0);throw MissingKey()} }
    }
}

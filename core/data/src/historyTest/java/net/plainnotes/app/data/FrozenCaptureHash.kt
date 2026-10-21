package net.plainnotes.app.data
import org.json.JSONObject
internal object FrozenCaptureHash {
    fun read(capture:JSONObject):String {
        val copy=JSONObject(capture.toString());copy.remove("payload_sha256")
        return ReferencePayload.sha(ReferencePayload.canonical(copy))
    }
}

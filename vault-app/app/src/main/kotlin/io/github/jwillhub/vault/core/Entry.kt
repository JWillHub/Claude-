package io.github.jwillhub.vault.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One saved login. [apps] are the Android package names this login may be filled into. */
data class Entry(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val apps: List<String> = emptyList(),
    val notes: String = "",
    val updated: Long = System.currentTimeMillis(),
)

/** The decrypted vault is a small JSON document. */
object VaultCodec {
    fun encode(entries: List<Entry>): ByteArray {
        val array = JSONArray()
        for (e in entries) {
            array.put(
                JSONObject()
                    .put("id", e.id)
                    .put("title", e.title)
                    .put("username", e.username)
                    .put("password", e.password)
                    .put("url", e.url)
                    .put("apps", JSONArray(e.apps))
                    .put("notes", e.notes)
                    .put("updated", e.updated)
            )
        }
        return JSONObject().put("version", 1).put("entries", array).toString().toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): List<Entry> {
        val array = JSONObject(String(bytes, Charsets.UTF_8)).getJSONArray("entries")
        return List(array.length()) { i ->
            val o = array.getJSONObject(i)
            val apps = o.optJSONArray("apps")
            Entry(
                id = o.getString("id"),
                title = o.optString("title"),
                username = o.optString("username"),
                password = o.optString("password"),
                url = o.optString("url"),
                apps = if (apps == null) emptyList() else List(apps.length()) { apps.getString(it) },
                notes = o.optString("notes"),
                updated = o.optLong("updated"),
            )
        }
    }

    /** Combines two vaults: an entry present in both keeps whichever copy was edited last. */
    fun merge(current: List<Entry>, incoming: List<Entry>): List<Entry> {
        val byId = LinkedHashMap<String, Entry>()
        for (e in current) byId[e.id] = e
        for (e in incoming) {
            val mine = byId[e.id]
            if (mine == null || e.updated > mine.updated) byId[e.id] = e
        }
        return byId.values.toList()
    }
}

// Local-first storage: one JSON file in filesDir, same shape as the web app's localStorage
// value, so export/import moves data between them unchanged.
package app.greenforrest.recur.data

import android.content.Context
import app.greenforrest.recur.core.Store
import app.greenforrest.recur.core.Subscription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File

class Repository(context: Context) {
  private val file = File(context.filesDir, "recur.json")
  private val _store = MutableStateFlow(load())
  val store: StateFlow<Store> = _store

  private fun load(): Store = try {
    if (file.exists()) Store.fromJson(JSONObject(file.readText())) else Store()
  } catch (_: Exception) {
    // Keep the unreadable file for recovery instead of overwriting it on the next save.
    file.copyTo(File(file.parentFile, "recur.corrupt-${System.currentTimeMillis()}.json"), overwrite = true)
    Store()
  }

  @Synchronized
  fun update(f: (Store) -> Store) {
    val next = f(_store.value)
    val tmp = File(file.parentFile, "recur.json.tmp")
    tmp.writeText(next.toJson().toString(2))
    tmp.renameTo(file) || run { file.writeText(tmp.readText()); tmp.delete() }
    _store.value = next
  }

  fun upsert(subs: List<Subscription>) = update { s ->
    val map = s.subscriptions.associateBy { it.id }.toMutableMap()
    val now = System.currentTimeMillis()
    for (x in subs) map[x.id] = x.copy(updatedAt = now)
    s.copy(subscriptions = map.values.toList())
  }

  fun softDelete(ids: Collection<String>, deleted: Boolean) = update { s ->
    val now = System.currentTimeMillis()
    s.copy(subscriptions = s.subscriptions.map {
      if (it.id in ids) it.copy(deletedAt = if (deleted) now else null, updatedAt = now) else it
    })
  }

  /** Merge an export: newer updatedAt wins per id. Returns how many came in. */
  fun import(json: String): Int {
    val incoming = Store.fromJson(JSONObject(json))
    update { s ->
      val map = s.subscriptions.associateBy { it.id }.toMutableMap()
      for (x in incoming.subscriptions) {
        val cur = map[x.id]
        if (cur == null || x.updatedAt >= cur.updatedAt) map[x.id] = x
      }
      s.copy(subscriptions = map.values.toList())
    }
    return incoming.subscriptions.size
  }

  fun exportJson(): String = _store.value.toJson().toString(2)
}

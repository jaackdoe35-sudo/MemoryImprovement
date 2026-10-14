package com.memoryimprovement.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local persistence; core study data needs no account or network connection. */
class MemoryRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("memory_improvement_local", Context.MODE_PRIVATE)

    fun loadItems(): List<MemoryItem> {
        val raw = preferences.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.getJSONObject(index)
                    add(MemoryItem(
                        id = value.getString("id"),
                        prompt = value.getString("prompt"),
                        answer = value.getString("answer"),
                        palace = value.optString("palace"),
                        locus = value.optString("locus"),
                        createdAt = value.optLong("createdAt"),
                        nextReviewAt = value.optLong("nextReviewAt"),
                        lastScore = if (value.isNull("lastScore")) null else value.optInt("lastScore"),
                        reviewCount = value.optInt("reviewCount"),
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveItems(items: List<MemoryItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject()
                .put("id", item.id)
                .put("prompt", item.prompt)
                .put("answer", item.answer)
                .put("palace", item.palace)
                .put("locus", item.locus)
                .put("createdAt", item.createdAt)
                .put("nextReviewAt", item.nextReviewAt)
                .put("lastScore", item.lastScore ?: JSONObject.NULL)
                .put("reviewCount", item.reviewCount))
        }
        preferences.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private companion object { const val KEY_ITEMS = "items_json" }
}

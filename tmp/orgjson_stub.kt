package org.json

class JSONObject {
    private val map = LinkedHashMap<String, Any?>()

    constructor()
    constructor(json: String) { /* ignore */ }

    fun put(key: String, value: Any?): JSONObject {
        map[key] = value
        return this
    }
    fun opt(key: String): Any? = map[key]
    fun optString(key: String): String = map[key]?.toString() ?: ""
    fun optString(key: String, def: String): String = map[key]?.toString() ?: def
    fun optDouble(key: String): Double = (map[key] as? Number)?.toDouble() ?: 0.0
    fun optDouble(key: String, def: Double): Double = (map[key] as? Number)?.toDouble() ?: def
    fun optInt(key: String): Int = (map[key] as? Number)?.toInt() ?: 0
    fun optBoolean(key: String): Boolean = map[key] as? Boolean ?: false
    fun optJSONObject(key: String): JSONObject? = map[key] as? JSONObject
    fun optJSONArray(key: String): JSONArray? = map[key] as? JSONArray
    fun has(key: String): Boolean = map.containsKey(key)
    fun isNull(key: String): Boolean = map[key] == null
    fun keys(): Iterator<String> = map.keys.iterator()
    override fun toString(): String = "{}"
    companion object { val NULL: Any? = null }
}

class JSONArray {
    private val list = mutableListOf<Any?>()

    constructor()
    constructor(json: String) { /* ignore */ }

    fun put(value: Any?): JSONArray { list.add(value); return this }
    fun length(): Int = list.size
    fun opt(i: Int): Any? = if (i in list.indices) list[i] else null
    fun optString(i: Int): String = opt(i)?.toString() ?: ""
    fun optDouble(i: Int, def: Double): Double = (opt(i) as? Number)?.toDouble() ?: def
    fun optJSONObject(i: Int): JSONObject? = opt(i) as? JSONObject
    fun getJSONObject(i: Int): JSONObject = opt(i) as JSONObject
    fun getDouble(i: Int): Double = (opt(i) as Number).toDouble()
    fun getString(i: Int): String = opt(i).toString()
    override fun toString(): String = "[]"
}

class JSONException(message: String) : Exception(message)

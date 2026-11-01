package ru.sagu4ka.rngai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Результат импорта. */
class ImportResult(val profiles: Int, val spins: Int, val lastProfileId: Long?)

class Repo(ctx: Context) {
    private val dao = AppDb.get(ctx).dao()
    private val prefs = ctx.getSharedPreferences("saguka", Context.MODE_PRIVATE)

    fun profiles() = dao.profiles()
    fun spins(profileId: Long) = dao.spins(profileId)

    var activeProfileId: Long
        get() = prefs.getLong("active", -1L)
        set(v) { prefs.edit().putLong("active", v).apply() }

    /** Гарантирует, что есть хотя бы один профиль. */
    suspend fun ensureDefaultProfile(): Long {
        val all = dao.profilesOnce()
        if (all.isEmpty()) {
            val id = dao.insertProfile(ProfileEntity(name = "ROB RUN", sectorCount = 37, sectorNames = ""))
            activeProfileId = id
            return id
        }
        if (all.none { it.id == activeProfileId }) activeProfileId = all.first().id
        return activeProfileId
    }

    suspend fun addProfile(name: String, count: Int, names: String): Long =
        dao.insertProfile(ProfileEntity(name = name.ifBlank { "Профиль" }, sectorCount = count.coerceIn(2, 100), sectorNames = names))

    suspend fun updateProfile(p: ProfileEntity) {
        dao.updateProfile(p)
        dao.dropOutOfRange(p.id, p.sectorCount)
    }

    suspend fun deleteProfile(id: Long) = dao.deleteProfile(id)
    suspend fun addSpin(profileId: Long, sector: Int) { dao.insertSpin(SpinEntity(profileId = profileId, sector = sector)) }
    suspend fun setSector(id: Long, sector: Int) = dao.setSector(id, sector)
    suspend fun deleteSpin(id: Long) = dao.deleteSpin(id)
    suspend fun resetStats(profileId: Long) = dao.clearSpins(profileId)

    // ------------------------------------------------------------------ экспорт

    private val fmt get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /** CSV активного профиля: n;sector;name;timestamp */
    suspend fun exportCsv(p: ProfileEntity): String {
        val names = p.names()
        val sb = StringBuilder("n;sector;name;timestamp\n")
        dao.spinsOnce(p.id).forEachIndexed { i, s ->
            sb.append(i + 1).append(';').append(s.sector).append(';')
                .append(names.getOrElse(s.sector) { s.sector.toString() }.replace(";", ","))
                .append(';').append(fmt.format(Date(s.ts))).append('\n')
        }
        return sb.toString()
    }

    /** JSON всех профилей (также используется как резервная копия). */
    suspend fun exportJson(onlyProfile: Long? = null): String {
        val arr = JSONArray()
        for (p in dao.profilesOnce()) {
            if (onlyProfile != null && p.id != onlyProfile) continue
            val spins = JSONArray()
            dao.spinsOnce(p.id).forEach { spins.put(JSONObject().put("sector", it.sector).put("ts", it.ts)) }
            arr.put(
                JSONObject().put("name", p.name).put("sectorCount", p.sectorCount)
                    .put("sectorNames", JSONArray(p.names())).put("spins", spins)
            )
        }
        return JSONObject().put("app", "saguka-ai").put("version", 1).put("exportedAt", System.currentTimeMillis())
            .put("profiles", arr).toString(2)
    }

    // ------------------------------------------------------------------ импорт

    /** Импорт JSON: каждый профиль из файла добавляется как новый (ничего не затирается). */
    suspend fun importJson(text: String): ImportResult {
        val root = JSONObject(text)
        val arr = root.getJSONArray("profiles")
        var spinsTotal = 0
        var last: Long? = null
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val k = o.getInt("sectorCount").coerceIn(2, 100)
            val namesArr = o.optJSONArray("sectorNames")
            val names = if (namesArr == null) "" else (0 until namesArr.length()).joinToString("\n") { namesArr.getString(it) }
            val id = dao.insertProfile(ProfileEntity(name = o.optString("name", "Импорт") + " (импорт)", sectorCount = k, sectorNames = names))
            val sp = o.getJSONArray("spins")
            val list = ArrayList<SpinEntity>()
            for (j in 0 until sp.length()) {
                val s = sp.getJSONObject(j)
                val sector = s.getInt("sector")
                if (sector in 0 until k) list.add(SpinEntity(profileId = id, sector = sector, ts = s.optLong("ts", System.currentTimeMillis())))
            }
            dao.insertSpins(list)
            spinsTotal += list.size
            last = id
        }
        return ImportResult(arr.length(), spinsTotal, last)
    }

    /** Импорт CSV в указанный профиль: берётся колонка sector (индекс) либо первая числовая. */
    suspend fun importCsv(text: String, p: ProfileEntity): ImportResult {
        val names = p.names()
        val list = ArrayList<SpinEntity>()
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        for (line in lines) {
            val cols = line.split(';', ',', '\t').map { it.trim() }
            if (cols.isEmpty() || cols[0].equals("n", true)) continue
            // формат нашего экспорта: n;sector;name;timestamp, либо просто один сектор в строке
            val raw = if (cols.size >= 2 && cols[0].toIntOrNull() != null && cols[1].toIntOrNull() != null) cols[1] else cols[0]
            val idx = raw.toIntOrNull()?.takeIf { it in 0 until p.sectorCount } ?: names.indexOf(raw).takeIf { it >= 0 } ?: continue
            list.add(SpinEntity(profileId = p.id, sector = idx))
        }
        dao.insertSpins(list)
        return ImportResult(0, list.size, p.id)
    }
}

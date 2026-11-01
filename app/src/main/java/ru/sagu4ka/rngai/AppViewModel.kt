package ru.sagu4ka.rngai

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.sagu4ka.rngai.data.*
import ru.sagu4ka.rngai.ml.*

/** Всё, что нужно экрану для отрисовки. Все объекты — снимки, движок их не меняет. */
class Analysis(
    val profile: ProfileEntity,
    val names: List<String>,
    val spins: List<SpinEntity>,
    val forecast: Forecast,
    val report: Report,
    val records: List<StepRecord>,
    val wealth: List<Double>,
    val sectorStats: List<SectorStat>,
    val tests: List<TestResult>,
    val netSteps: Long
)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repo(app)
    private val activeId = MutableStateFlow(repo.activeProfileId)

    val profiles: StateFlow<List<ProfileEntity>> = repo.profiles()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeProfileId: StateFlow<Long> = activeId

    private val _busy = MutableStateFlow(true)
    val busy: StateFlow<Boolean> = _busy

    private val msgChannel = Channel<String>(Channel.BUFFERED)
    val messages = msgChannel.receiveAsFlow()

    // ---- движок: живёт между вычислениями и дообучается только новыми результатами
    private var engine: Engine? = null
    private var engProfile = -1L
    private var engK = -1
    private var engVals: List<Int> = emptyList()
    private var engIds: List<Long> = emptyList()

    val analysis: StateFlow<Analysis?> = combine(profiles, activeId) { ps, id -> ps.firstOrNull { it.id == id } }
        .distinctUntilChanged()
        .flatMapLatest { p ->
            if (p == null) flowOf(null)
            else repo.spins(p.id).conflate().map { spins -> compute(p, spins) }
        }
        .flowOn(Dispatchers.Default)
        .onEach { _busy.value = false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            val id = repo.ensureDefaultProfile()
            activeId.value = id
        }
    }

    private fun compute(p: ProfileEntity, spinsAll: List<SpinEntity>): Analysis {
        val k = p.sectorCount
        val spins = spinsAll.filter { it.sector in 0 until k }
        val vals = spins.map { it.sector }
        val ids = spins.map { it.id }

        var e = engine
        val reusable = e != null && engProfile == p.id && engK == k &&
                vals.size >= engVals.size &&
                vals.subList(0, engVals.size) == engVals &&
                ids.subList(0, engIds.size) == engIds
        if (!reusable) {
            e = Engine(k)
            engVals = emptyList(); engIds = emptyList()
        }
        e!!
        for (i in engVals.size until vals.size) e.step(vals[i])
        engine = e; engProfile = p.id; engK = k; engVals = vals; engIds = ids

        return Analysis(
            profile = p, names = p.names(), spins = spins,
            forecast = e.forecast(), report = e.report(),
            records = e.records.toList(), wealth = e.wealthHist.toList(),
            sectorStats = StatsCalc.sectorStats(vals, k),
            tests = StatsCalc.tests(vals, k),
            netSteps = e.net.trainSteps
        )
    }

    // ------------------------------------------------------------ действия

    private fun say(s: String) { msgChannel.trySend(s) }

    fun addSpin(sector: Int) = viewModelScope.launch { repo.addSpin(activeId.value, sector) }

    fun undoLast() = viewModelScope.launch {
        analysis.value?.spins?.lastOrNull()?.let { repo.deleteSpin(it.id) }
    }

    fun setSector(id: Long, sector: Int) = viewModelScope.launch { repo.setSector(id, sector) }
    fun deleteSpin(id: Long) = viewModelScope.launch { repo.deleteSpin(id) }

    fun selectProfile(id: Long) { repo.activeProfileId = id; _busy.value = true; activeId.value = id }

    fun addProfile(name: String, count: Int, names: String) = viewModelScope.launch {
        val id = repo.addProfile(name, count, names)
        selectProfile(id)
    }

    fun saveProfile(p: ProfileEntity) = viewModelScope.launch {
        repo.updateProfile(p)
        say("Профиль сохранён")
    }

    fun deleteProfile(id: Long) = viewModelScope.launch {
        repo.deleteProfile(id)
        val rest = profiles.value.filter { it.id != id }
        if (rest.isEmpty()) { selectProfile(repo.ensureDefaultProfile()) }
        else if (activeId.value == id) selectProfile(rest.first().id)
    }

    fun resetStats() = viewModelScope.launch {
        repo.resetStats(activeId.value)
        say("Статистика сброшена, профиль сохранён")
    }

    // ---- экспорт / импорт через системный выбор файла

    fun exportTo(uri: Uri, kind: String) = viewModelScope.launch {
        try {
            val p = analysis.value?.profile
            val text = withContext(Dispatchers.IO) {
                when (kind) {
                    "csv" -> repo.exportCsv(p ?: return@withContext "")
                    "json" -> repo.exportJson(p?.id)
                    else -> repo.exportJson(null)             // backup — все профили
                }
            }
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            }
            say("Файл сохранён")
        } catch (e: Exception) { say("Ошибка экспорта: ${e.message}") }
    }

    fun importFrom(uri: Uri, kind: String) = viewModelScope.launch {
        try {
            val text = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
            }
            val r = withContext(Dispatchers.IO) {
                if (kind == "csv") repo.importCsv(text, analysis.value?.profile ?: error("нет профиля"))
                else repo.importJson(text)
            }
            if (kind != "csv") r.lastProfileId?.let { selectProfile(it) }
            say("Импортировано результатов: ${r.spins}")
        } catch (e: Exception) { say("Ошибка импорта: ${e.message}") }
    }
}

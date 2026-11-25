package com.example.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.money.MoneyMath
import com.example.data.repository.BusinessReportSnapshot
import com.example.data.repository.BusinessRepository
import com.example.data.repository.ReportPeriodPreset
import com.example.data.repository.ReportsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class ReportsUiState(
  val businessId: Long = 0L,
  val businessName: String = "Stock+",
  val selectedPreset: ReportPeriodPreset = ReportPeriodPreset.THIS_MONTH,
  val customRangeMillis: Pair<Long, Long>? = null,
  val report: BusinessReportSnapshot? = null,
)

class ReportsViewModel(
  private val businessIdFlow: StateFlow<Long>,
  private val reportsRepository: ReportsRepository,
  private val businessRepository: BusinessRepository,
) : ViewModel() {

  private val selectedPreset = MutableStateFlow(ReportPeriodPreset.THIS_MONTH)
  private val customRange = MutableStateFlow<Pair<Long, Long>?>(null)

  @OptIn(ExperimentalCoroutinesApi::class)
  private val domainFlow = combine(businessIdFlow, selectedPreset, customRange) { bizId, preset, custom ->
    Triple(bizId, preset, custom)
  }.flatMapLatest { (bizId, preset, custom) ->
    if (bizId <= 0L) {
      flowOf(null)
    } else {
      val (start, end) = custom ?: ReportsRepository.dateRangeForPreset(preset)
      combine(
        reportsRepository.observeReport(bizId, start, end),
        businessRepository.observeWorkspaceSnapshot(),
      ) { report, workspace ->
        ReportsUiState(
          businessId = bizId,
          businessName = workspace.business?.name ?: "Stock+",
          selectedPreset = preset,
          customRangeMillis = custom,
          report = report,
        )
      }
    }
  }

  val uiState: StateFlow<ReportsUiState> = domainFlow.mapOrDefault()
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = ReportsUiState(),
    )

  private fun kotlinx.coroutines.flow.Flow<ReportsUiState?>.mapOrDefault(): kotlinx.coroutines.flow.Flow<ReportsUiState> =
    kotlinx.coroutines.flow.flow {
      collect { value -> emit(value ?: ReportsUiState()) }
    }

  fun selectPreset(preset: ReportPeriodPreset) {
    customRange.value = null
    selectedPreset.value = preset
  }

  fun setCustomRange(startMillis: Long, endMillis: Long) {
    val ordered = if (startMillis <= endMillis) startMillis to endMillis else endMillis to startMillis
    customRange.value = ordered
  }

  fun buildShareText(): String {
    val state = uiState.value
    val snapshot = state.report ?: return ""
    val label = if (state.customRangeMillis != null) "Rango personalizado" else state.selectedPreset.label
    return ReportsRepository.formatExportSummary(snapshot, state.businessName, label)
  }

  /** Modelo con los datos del reporte listos para exportar a PDF o Excel. */
  fun buildExportModel(): ReportExportModel? {
    val state = uiState.value
    val snapshot = state.report ?: return null
    val label = if (state.customRangeMillis != null) "Rango personalizado" else state.selectedPreset.label
    return ReportExportBuilder.build(
      snapshot = snapshot,
      businessName = state.businessName,
      periodLabel = label,
      periodRangeText = periodRangeText(state),
      generatedAtMillis = System.currentTimeMillis(),
    )
  }

  private fun periodRangeText(state: ReportsUiState): String? {
    val range = state.customRangeMillis
      ?: if (state.selectedPreset == ReportPeriodPreset.ALL) {
        null
      } else {
        ReportsRepository.dateRangeForPreset(state.selectedPreset)
      }
      ?: return null
    if (range.first <= 0L || range.second == Long.MAX_VALUE) return null
    val format = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US)
    return "${format.format(java.util.Date(range.first))} – ${format.format(java.util.Date(range.second))}"
  }

  class Factory(
    private val businessIdFlow: StateFlow<Long>,
    private val reportsRepository: ReportsRepository,
    private val businessRepository: BusinessRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      ReportsViewModel(businessIdFlow, reportsRepository, businessRepository) as T
  }
}

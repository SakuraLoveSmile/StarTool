package app.startool.android

import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 主页面共享的所选日期：记录页与回看页切换页签时不改变日期。
 * 由 AppContainer 持有，两个页面共同读写。
 */
class SelectedDateStore(private val clock: Clock) {

    private val _date = MutableStateFlow(LocalDate.now(clock))
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    fun select(date: LocalDate) {
        _date.value = date
    }

    fun previousDay() = _date.update { it.minusDays(1) }

    fun nextDay() = _date.update { it.plusDays(1) }

    fun today() {
        _date.value = LocalDate.now(clock)
    }
}

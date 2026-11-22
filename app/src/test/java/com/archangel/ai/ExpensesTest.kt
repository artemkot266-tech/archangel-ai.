package com.archangel.ai

import com.archangel.ai.features.Expense
import com.archangel.ai.features.ExpensePeriod
import com.archangel.ai.features.Expenses
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpensesTest {
    private val now: Calendar = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 22, 15, 0, 0); set(Calendar.MILLISECOND, 0) }
    private fun daysAgo(d: Int) = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -d) }.timeInMillis

    private val list = listOf(
        Expense(1, 200.0, "₽", "кофе", daysAgo(0)),
        Expense(2, 1500.0, "₽", "продукты", daysAgo(0)),
        Expense(3, 300.0, "₽", "кофе", daysAgo(3)),
        Expense(4, 5000.0, "₽", "аренда", daysAgo(10)),
        Expense(5, 999.0, "₽", "старое", daysAgo(40)),
    )

    @Test fun format() {
        assertEquals("4 350", Expenses.formatAmount(4350.0))
        assertEquals("12,50", Expenses.formatAmount(12.5))
        assertEquals("200 ₽", Expenses.money(200.0, "₽"))
    }

    @Test fun today() = assertEquals(
        "Сегодня потрачено 1 700 ₽. Больше всего: продукты 1 500 ₽, кофе 200 ₽.",
        Expenses.summary(list, ExpensePeriod.TODAY, now = now),
    )

    @Test fun week() = assertTrue(Expenses.summary(list, ExpensePeriod.WEEK, now = now).startsWith("За неделю потрачено 2 000 ₽"))

    @Test fun month() = assertTrue(Expenses.summary(list, ExpensePeriod.MONTH, now = now).startsWith("За этот месяц потрачено 7 000 ₽"))

    @Test fun category() = assertEquals("На «кофе» за неделю: 500 ₽.", Expenses.summary(list, ExpensePeriod.WEEK, "кофе", now))

    @Test fun empty() = assertTrue(Expenses.summary(emptyList(), ExpensePeriod.MONTH, now = now).startsWith("Расходов за этот месяц нет"))

    @Test fun mixedCurrencies() = assertEquals(
        "100 ₽ и 5 $",
        Expenses.totalText(listOf(Expense(1, 100.0, "₽", "a", 0), Expense(2, 5.0, "$", "b", 0))),
    )
}

package com.yh.schedule.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 考试表解析的回归用例。
 *
 * 表头取自成理强智「考试安排」实页：
 * 序号/校区/考试校区/考试场次/课程编号/课程名称/授课教师/考试时间/考场/座位号
 */
class QzExamParserTest {

    private val header = listOf(
        "序号", "校区", "考试校区", "考试场次", "课程编号",
        "课程名称", "授课教师", "考试时间", "考场", "座位号",
    )

    @Test
    fun `按表头映射课程 时间 考场`() {
        val rows = listOf(
            header,
            listOf("1", "主校区", "主校区", "第1场", "10203040",
                "数据库原理", "张三", "2026-12-25 09:00-11:00", "6A101", "12"),
        )
        val exams = QzExamParser.parse(rows)
        assertEquals(1, exams.size)
        with(exams.first()) {
            assertEquals("数据库原理", course)
            assertEquals(LocalDate.of(2026, 12, 25), date)
            assertEquals("09:00-11:00", timeRange)
            assertEquals("6A101", room)
        }
    }

    @Test
    fun `未查询到数据 视为空表`() {
        val rows = listOf(header, listOf("未查询到数据"))
        assertTrue(QzExamParser.parse(rows).isEmpty())
    }

    @Test
    fun `只有月日时按当前学年补年份`() {
        val rows = listOf(
            header,
            listOf("1", "", "", "", "10203040", "线性代数", "李四", "12月30日 14:00~16:00", "乙203"),
        )
        val exams = QzExamParser.parse(rows)
        assertEquals(1, exams.size)
        val today = LocalDate.now()
        val expectedYear = if (LocalDate.of(today.year, 12, 30).isBefore(today.minusDays(180))) {
            today.year + 1
        } else {
            today.year
        }
        assertEquals(LocalDate.of(expectedYear, 12, 30), exams.first().date)
        assertEquals("14:00-16:00", exams.first().timeRange)
        assertEquals("乙203", exams.first().room)
    }

    @Test
    fun `没有日期的行不会生成考试`() {
        val rows = listOf(header, listOf("2", "主校区", "", "", "", "大学英语", "王五", "待定", "待定"))
        assertTrue(QzExamParser.parse(rows).isEmpty())
    }

    @Test
    fun `非法日期返回 null`() {
        assertNull(QzExamParser.parseDate("2026-13-45"))
        assertNull(QzExamParser.parseDate("下周"))
    }
}

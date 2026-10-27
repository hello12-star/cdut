package com.yh.schedule.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * 学期起始日解析器（成都理工 强智教务）。
 *
 * ## 一、为什么不直接读「学期开始日期」
 *
 * 强智课表页 `/jsxsd/xskb/xskb_list.do` **本身不输出学期第一周的日期**。
 * 页面只提供「周次结构」——当前是第几周、每门课在第几周上。
 * 页面上虽有「2026-2027学年第一学期」这类学期名，但那是**学年学期标识**，
 * 不含任何具体日期，无法换算成某一天。
 *
 * 所以这里采用**反推**：拿到「当前第几周」，用今天的日期倒推第一周周一。
 *
 * ## 二、数据来源与字段位置（按可靠性排序）
 *
 * 探测全部在页面内 JS 完成（见 `TimetableScraper`），结果随课表一起回传。
 *
 * | # | 来源 | 字段位置 | 形态 | 说明 |
 * |---|------|----------|------|------|
 * | ① | 地址栏 query | `xskb_list.do?zs=8` | 纯数字 1~2 位 | 强智标准参数，切周次时随之变化，**最可靠** |
 * | ② | 页面正文 | `document.body.innerText` 前 4000 字 | `第8周` / `第 8 周` | 部分模板顶部有周次标签 |
 * | ③ | 周次下拉框 | `select` 的当前 `value` | 纯数字 | 兜底，页面有多个 select 时可能误判 |
 *
 * 三者都拿不到 → JS 回 `w: 0` → 本解析器返回 null → 调用方保留原值，不破坏已有周次。
 *
 * ## 三、换算公式
 *
 * ```
 * termStartMonday = today.minusWeeks(week - 1).with(previousOrSame(MONDAY))
 * ```
 *
 * 例：今天 2026-10-27（周二）、当前第 9 周
 *  → 10-27 减 8 周 = 09-01（周二）
 *  → 归到该周周一 = **2026-08-31**
 *
 * ## 四、边界情况处理
 *
 * | 情况 | 处理 |
 * |------|------|
 * | week 缺失 / 0 / 负数 | 返回 null，不改动现有值 |
 * | week 不在 1..30 | 返回 null（正常学期 16~25 周，越界说明探测错了） |
 * | 反推日期晚于今天 | 返回 null（第一周周一不可能在未来） |
 * | 与现有值相差 > 120 天 | 返回新值但标记为「差异过大」，由调用方决定是否覆盖 |
 */
object QzTermParser {

    const val MIN_WEEK = 1
    const val MAX_WEEK = 30

    /**
     * 由「当前第几周」+「今天」反推学期第一周周一。
     *
     * @param week 教务页面探测到的当前周次；0 或越界视为无效
     * @param today 基准日，默认今天（测试时可注入）
     * @return 学期第一周周一；无效返回 null
     */
    fun deriveTermStartMonday(week: Int, today: LocalDate = LocalDate.now()): LocalDate? {
        if (week !in MIN_WEEK..MAX_WEEK) return null
        val monday = today
            .minusWeeks((week - 1).toLong())
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        // 第一周周一不可能晚于今天（week=1 且今天是周一时会相等，合法）
        if (monday.isAfter(today)) return null
        return monday
    }

    /**
     * 新推出的学期起始日与本地已存值是否相差过大。
     * 相差过大通常意味着**换学期了**（或教务的 zs 参数含义变了），
     * 调用方可以据此决定是否覆盖、或提示用户确认。
     */
    fun isFarApart(a: LocalDate, b: LocalDate, maxDays: Long = 120): Boolean =
        kotlin.math.abs(ChronoUnit.DAYS.between(a, b)) > maxDays
}

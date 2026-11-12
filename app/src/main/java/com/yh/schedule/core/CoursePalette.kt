package com.yh.schedule.core

/**
 * 课程配色（对齐参考源码 SmartHNU 的 CourseColorUtil）。
 *
 * 调色板顺序一旦发布就不能改：用户手动选的颜色以「索引」持久化，
 * 调整顺序会让所有人的配色集体错位。
 */
object CoursePalette {

    val backgrounds: List<Int> = listOf(
        0xFFE1F5FE, 0xFFE8F5E9, 0xFFFFF3E0, 0xFFF3E5F5,
        0xFFE0F7FA, 0xFFFCE4EC, 0xFFE8EAF6, 0xFFF1F8E9,
        0xFFEFEBE9, 0xFFE0F2F1, 0xFFEDE7F6, 0xFFE3F2FD,
    ).map { it.toInt() }

    /** 浅底统一用深蓝字，保证对比度 */
    const val FOREGROUND: Int = 0xFF1B3B6F.toInt()

    fun defaultIndexOf(courseName: String): Int {
        var hash = 0
        for (c in courseName) hash = hash * 31 + c.code
        return Math.floorMod(hash, backgrounds.size)
    }

    fun background(index: Int): Int = backgrounds[Math.floorMod(index, backgrounds.size)]

    fun indexFor(courseName: String, overrides: Map<String, Int>): Int =
        overrides[courseName] ?: defaultIndexOf(courseName)
}

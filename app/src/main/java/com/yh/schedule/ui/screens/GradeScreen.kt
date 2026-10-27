package com.yh.schedule.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yh.schedule.core.GradeItem
import com.yh.schedule.data.Repository

/**
 * 成绩页：统计卡（加权平均分 / 总学分 / 平均绩点）+ 按学期分组列表。
 */
@Composable
fun GradeScreen(repo: Repository) {
    var grades by remember { mutableStateOf<List<GradeItem>>(emptyList()) }

    LaunchedEffect(Unit) { grades = repo.grades() }

    val totalCredit = grades.sumOf { it.credit }
    val weightedAvg = if (totalCredit > 0) {
        grades.sumOf { it.score * it.credit } / totalCredit
    } else 0.0
    val avgGpa = if (grades.isNotEmpty()) {
        grades.sumOf { (it.gradePoint ?: 0.0) * it.credit } / totalCredit.coerceAtLeast(0.001)
    } else 0.0

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("加权平均分", "%.1f".format(weightedAvg), Modifier.weight(1f))
                StatCard("总学分", "%.1f".format(totalCredit), Modifier.weight(1f))
                StatCard("平均绩点", "%.2f".format(avgGpa), Modifier.weight(1f))
            }
        }

        grades.groupBy { it.term }.forEach { (term, items) ->
            item {
                Text(
                    term,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(items) { g ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(g.course, style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold)
                            Text(
                                "学分 %.1f · 绩点 %s".format(
                                    g.credit,
                                    g.gradePoint?.let { "%.1f".format(it) } ?: "-",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "%.0f".format(g.score),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (g.score >= 60) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(12.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

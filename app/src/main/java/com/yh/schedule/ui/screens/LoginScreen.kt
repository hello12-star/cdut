package com.yh.schedule.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yh.schedule.data.Repository
import kotlinx.coroutines.launch

/**
 * 登录页：统一认证（WebView）与演示模式双入口。
 * 密码不由本应用处理——统一认证在 WebView 内完成（成理 CAS 的瑞数防护要求）。
 */
@Composable
fun LoginScreen(repo: Repository, onCasLogin: () -> Unit) {
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("砚湖日程", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "成都理工大学校园日程助手 · MVP",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(20.dp)) {
                Button(
                    onClick = onCasLogin,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("统一身份认证登录") }

                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { scope.launch { repo.login("", "", demo = true) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("演示模式进入（内置示例数据）") }

                Spacer(Modifier.height(14.dp))
                Text(
                    "登录在安全容器内完成；勾选「记住账号密码」后，账号密码会保存在本机（应用私有目录）" +
                            "用于下次自动填充，登录后仅保存校方下发的会话凭据用于读取课表与成绩。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "说明：成理统一认证部署了动态防护，第三方客户端需以浏览器容器方式登录；" +
                    "演示模式不影响任何真实账号。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

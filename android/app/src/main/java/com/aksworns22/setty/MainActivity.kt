package com.aksworns22.setty

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aksworns22.setty.ui.theme.SettyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SettyTheme {
                SignInScreen()
            }
        }
    }
}

@Composable
fun SignInScreen() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .systemBarsPadding()
            .padding(top = 32.dp, start = 32.dp, end = 32.dp, bottom = 16.dp)
            .imePadding()
    ) {
        Text(text = "Setty 로그인", style = MaterialTheme.typography.headlineLargeEmphasized)
        Text(
            text = "세티와 함께 중고 가구 거래를 시작해보세요.",
            style = MaterialTheme.typography.bodyLargeEmphasized
        )
        OutlinedTextField(
            state = rememberTextFieldState(),
            label = { Text("세티 아이디", style = MaterialTheme.typography.bodyMediumEmphasized) },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next
            ),
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedSecureTextField(
            state = rememberTextFieldState(),
            label = { Text("세티 비밀번호", style = MaterialTheme.typography.bodyMediumEmphasized) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "계정 생성하기",
                style = MaterialTheme.typography.bodyLargeEmphasized,
                modifier = Modifier.clickable {},
            )
            Button(onClick = {}) {
                Text("로그인", style = MaterialTheme.typography.bodyLargeEmphasized)
            }
        }
    }
}

@Preview
@Composable
private fun SignInScreenPreview() {
    SettyTheme {
        SignInScreen()
    }
}

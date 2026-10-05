package com.telegramdrive.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.telegramdrive.app.telegram.AuthState

@Composable
fun LoginScreen(state: AuthState, onPhone: (String) -> Unit, onCode: (String) -> Unit, onPassword: (String) -> Unit) {
    var input by remember(state::class) { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    // নতুন state এলেই (সফল বা এরর) বাটনের লোডিং স্পিনার বন্ধ হয়ে যায়।
    LaunchedEffect(state) { isSubmitting = false }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (state == AuthState.Loading) {
            SplashLoading()
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandMark()
            Spacer(Modifier.height(32.dp))

            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                },
                label = "login-step"
            ) { targetState ->
                when (targetState) {
                    is AuthState.Error -> ErrorStep(targetState.message)
                    AuthState.WaitPhoneNumber -> StepCard(
                        icon = Icons.Filled.Phone,
                        title = "আপনার ফোন নম্বর দিন",
                        subtitle = "Telegram অ্যাকাউন্ট ভেরিফাই করতে ব্যবহার হবে",
                        placeholder = "+880 1XXXXXXXXX",
                        value = input,
                        onValueChange = { input = it },
                        buttonLabel = "কোড পাঠান",
                        isSubmitting = isSubmitting,
                        onSubmit = { isSubmitting = true; onPhone(input) }
                    )
                    AuthState.WaitCode -> StepCard(
                        icon = Icons.Filled.Password,
                        title = "ভেরিফিকেশন কোড",
                        subtitle = "Telegram অ্যাপে পাঠানো কোডটি লিখুন",
                        placeholder = "12345",
                        value = input,
                        onValueChange = { input = it },
                        buttonLabel = "যাচাই করুন",
                        isSubmitting = isSubmitting,
                        onSubmit = { isSubmitting = true; onCode(input) }
                    )
                    AuthState.WaitPassword -> StepCard(
                        icon = Icons.Filled.Lock,
                        title = "টু-স্টেপ পাসওয়ার্ড",
                        subtitle = "আপনার Telegram 2FA পাসওয়ার্ড দিন",
                        placeholder = "পাসওয়ার্ড",
                        value = input,
                        onValueChange = { input = it },
                        buttonLabel = "লগইন করুন",
                        isSubmitting = isSubmitting,
                        isPassword = true,
                        onSubmit = { isSubmitting = true; onPassword(input) }
                    )
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun BrandMark() {
    Box(
        modifier = Modifier
            .size(72.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.CloudQueue,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(36.dp)
        )
    }
    Spacer(Modifier.height(16.dp))
    Text("TelegramDrive", style = MaterialTheme.typography.headlineSmall)
    Text(
        "আপনার ফাইল, নিরাপদে Telegram-এ",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SplashLoading() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BrandMark()
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorStep(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "কিছু একটা ভুল হয়েছে",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    buttonLabel: String,
    isSubmitting: Boolean,
    isPassword: Boolean = false,
    onSubmit: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(placeholder) },
                singleLine = true,
                visualTransformation = if (isPassword)
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                else androidx.compose.ui.text.input.VisualTransformation.None,
                enabled = !isSubmitting
            )
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onSubmit,
                enabled = value.isNotBlank() && !isSubmitting,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(buttonLabel)
                }
            }
        }
    }
}

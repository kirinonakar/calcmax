package com.kirinonakar.calcmax.ui

import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kirinonakar.calcmax.ui.theme.LocalInstrument

private const val GitHubUrl = "https://github.com/kirinonakar/calcmax"

@Composable
fun AboutDialog(onClose: () -> Unit) {
    val colors = LocalInstrument.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }

    AlertDialog(
        onDismissRequest = onClose,
        icon = {
            AndroidView(
                factory = { viewContext ->
                    ImageView(viewContext).apply {
                        setImageDrawable(viewContext.packageManager.getApplicationIcon(viewContext.packageName))
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                },
                modifier = Modifier.size(64.dp)
            )
        },
        title = {
            Text("CalcMax", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("v$version", color = colors.muted)
                Text(
                    GitHubUrl,
                    modifier = Modifier.clickable { uriHandler.openUri(GitHubUrl) },
                    fontSize = 12.sp,
                    color = colors.accent,
                    textAlign = TextAlign.Center,
                    textDecoration = TextDecoration.Underline
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(tr("Close")) }
        }
    )
}

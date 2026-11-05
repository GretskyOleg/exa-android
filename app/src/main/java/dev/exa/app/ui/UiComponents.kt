package dev.exa.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ExaCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ExaSurface),
        border = BorderStroke(1.dp, ExaLine)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun Badge(text: String) {
    Surface(
        color = ExaRed.copy(alpha = 0.10f),
        contentColor = ExaRed,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text.uppercase(),
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun ActionBadge(action: String) {
    val (label, color) = when (action) {
        "proxy" -> "прокси" to ExaGreen
        "block" -> "блок" to ExaRed
        else -> "напрямую" to ExaMuted
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            label,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun SubHeader(title: String, subtitle: String? = null, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "Назад", tint = ExaMuted)
        }
        Spacer(Modifier.width(4.dp))
        Column {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Black)
            if (subtitle != null) {
                Text(subtitle, fontSize = 10.sp, color = ExaMuted2, letterSpacing = 1.1.sp)
            }
        }
    }
}

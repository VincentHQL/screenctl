package com.scrctl.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scrctl.desktop.model.SystemStats

@Composable
fun FooterStatusBar(stats: SystemStats, modifier: Modifier = Modifier) {
    val border = Color(0xFF1F2937)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(Color.White.copy(alpha = 0.02f))
            .border(1.dp, border)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("CPU 使用率", color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Box(modifier = Modifier.width(80.dp).height(6.dp).clip(RoundedCornerShape(999.dp)).background(Color(0xFF334155))) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(stats.cpuPercent / 100f)
                        .background(Color(0xFF22C55E)),
                )
            }
            Text("${stats.cpuPercent}%", color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(14.dp))
            Text(
                "网络 上行/下行",
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${"%.1f".format(stats.uploadMbps)} MB/s | ${"%.1f".format(stats.downloadMbps)} MB/s",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(stats.version, color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(999.dp)).background(Color(0xFF22C55E)))
            Spacer(Modifier.width(6.dp))
            Text(stats.healthLabel, color = Color(0xFF22C55E), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}
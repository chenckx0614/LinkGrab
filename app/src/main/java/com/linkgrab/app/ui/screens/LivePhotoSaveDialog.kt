package com.linkgrab.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkgrab.app.ui.liquid.IOS26GlassDialog
import com.linkgrab.app.ui.theme.IOS26Button
import com.linkgrab.app.ui.theme.IOS26OutlinedButton
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel

@Composable
internal fun LivePhotoSaveDialog(count: Int, onDismiss: () -> Unit, onSave: (Boolean) -> Unit) {
    IOS26GlassDialog(onDismissRequest = onDismiss) {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("保存 $count 张实况", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = iosLabel())
            Text("实况照片：将图片和动态片段保存为一个文件，在支持 Motion Photo 的相册中播放。其他相册可能只显示静态封面。",
                fontSize = 14.sp, color = iosSecondaryLabel())
            Text("封面将转换为高质量 JPEG，超大图片缩至 1600 万像素。若相册不识别，或希望保留原始图片格式，可选择分开保存。",
                fontSize = 13.sp, color = iosSecondaryLabel())
            IOS26Button(text = "保存为实况照片", onClick = { onSave(true) }, modifier = Modifier.fillMaxWidth())
            IOS26OutlinedButton(text = "图片＋视频分开保存", onClick = { onSave(false) }, modifier = Modifier.fillMaxWidth())
            IOS26OutlinedButton(text = "取消", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

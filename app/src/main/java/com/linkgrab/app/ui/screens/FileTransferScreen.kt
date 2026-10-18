package com.linkgrab.app.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkgrab.app.ui.theme.IOS26Canvas
import com.linkgrab.app.ui.theme.IOS26Divider
import com.linkgrab.app.ui.theme.IOS26PageHeader
import com.linkgrab.app.ui.theme.IOS26Panel
import com.linkgrab.app.ui.theme.IOS26StickyPage
import com.linkgrab.app.ui.theme.iosSecondaryLabel

/**
 * 文件传输服务商（工具箱 → 文件传输 → 二级页）。
 *
 * 都是「免登录 / 手机电脑互传」类的中转站：手机上传拿取件码或链接，
 * 电脑端打开取件。统一走应用内 WebView（可在「我的 → 通用」里切浏览器）。
 */
data class FileTransferService(
    val name: String,
    val url: String,
    val subtitle: String,
)

val FileTransferServices = listOf(
    FileTransferService(
        name = "文叔叔",
        url = "https://www.wenshushu.cn/",
        subtitle = "免登录大文件传输，手机电脑互传",
    ),
    FileTransferService(
        name = "AirPortal 快传",
        url = "https://www.airportal.cn/",
        subtitle = "取件码取文件，跨设备即传即取",
    ),
    FileTransferService(
        name = "轻松传",
        url = "https://easychuan.cn/",
        subtitle = "拖进去就能传，扫码或取件码收",
    ),
    FileTransferService(
        name = "file.kiwi",
        url = "https://file.kiwi/zh",
        subtitle = "无需注册，短期限文件分享",
    ),
    FileTransferService(
        name = "JiDrop",
        url = "https://jidrop.com/",
        subtitle = "网页直传，链接分享给任意设备",
    ),
    FileTransferService(
        name = "SendTomo",
        url = "https://sendtomo.com/?lang=zh",
        subtitle = "匿名寄大文件，到期自动失效",
    ),
)

/**
 * 工具箱 → 文件传输：把同类服务收进一个二级页，工具箱主列表不再被单个
 * 服务占位。点任意一行交给 [onOpenUrl]，由 AppNavigation 决定走应用内
 * WebView 还是系统浏览器（跟随「工具用浏览器打开」开关）。
 */
@Composable
fun FileTransferScreen(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    IOS26Canvas {
        IOS26StickyPage(title = "文件传输", onBack = onBack) {
            item {
                IOS26PageHeader(
                    "文件传输",
                    "免登录的大文件中转站：手机上传，电脑取件。",
                    topPadding = 0.dp,
                )
            }
            item {
                IOS26Panel(Modifier.fillMaxWidth()) {
                    FileTransferServices.forEachIndexed { index, service ->
                        StaggeredAppear(index) {
                            ToolRow(
                                title = service.name,
                                subtitle = service.subtitle,
                                onClick = { onOpenUrl(service.url) },
                            )
                        }
                        if (index != FileTransferServices.lastIndex) {
                            IOS26Divider(Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
            item {
                Text(
                    "点开即在应用内打开，右上角菜单可刷新或改用浏览器",
                    color = iosSecondaryLabel(),
                    modifier = Modifier.padding(horizontal = 4.dp),
                    fontSize = 14.sp,
                )
            }
        }
    }
}

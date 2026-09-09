package com.linkgrab.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkgrab.app.ui.theme.IOS26Canvas
import com.linkgrab.app.ui.theme.IOS26PageHeader
import com.linkgrab.app.ui.theme.IOS26Panel
import com.linkgrab.app.ui.theme.IOS26StickyPage
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel

data class UpdateLogItem(val version: String, val date: String, val changes: List<String>)

private val updateLogs = listOf(
    UpdateLogItem("2.1.10", "2026-09-09", listOf(
        "解析结果页底部操作栏改为毛玻璃：实时高斯模糊滚动到栏底的图片/视频内容，从近乎不透明的实色变为通透玻璃",
        "底栏模糊区域延伸到屏幕底部安全区，与系统毛玻璃导航栏观感一致",
    )),
    UpdateLogItem("2.1.9", "2026-09-09", listOf(
        "移除鸿蒙 7 底栏上方的渐变颜色蒙层：深色模式下它显示成一条突兀的透明条",
        "鸿蒙 7 底栏光效改为跟手：按压到其他标签时光点几乎立即跟着移动过去，并提高了光效亮度让跟随清晰可见",
    )),
    UpdateLogItem("2.1.8", "2026-09-09", listOf(
        "修正鸿蒙 7 底栏按压光晕：之前是一个只罩住单个标签的小圆斑浮在底栏上，现在改为从指尖向整个胶囊控件内部扩散",
        "鸿蒙 7 底栏光晕加入扩散动感：半径随按压进度从 40% 扩散到覆盖整个控件，配合容器裁剪形成光在控件内散开的观感",
    )),
    UpdateLogItem("2.1.7", "2026-09-09", listOf(
        "修复三种底栏切换/按压动画掉帧与丢动画（鸿蒙 7）：光效改为在绘制阶段读取动画值，之前在组合期判断导致每帧重组整个底栏",
        "修复三种底栏切换/按压动画掉帧与丢动画（鸿蒙 7）：径向渐变改为 remember 缓存固定实例 + translate 移动，动画期间每帧零对象分配",
        "修复三种底栏切换/按压动画掉帧与丢动画（HyperOS4）：遮罩颜色不再每帧 lerp 出新 Color 实例，改为常态色打底 + 按压色 alpha 叠加",
        "修复三种底栏切换/按压动画掉帧与丢动画（iOS27）：最贵的色散效果（chromaticAberration）现在只在拖拽时开启，点击切换直接跳过",
    )),
    UpdateLogItem("2.1.6", "2026-09-09", listOf(
        "新增 HarmonyOS 7 风格底栏：悬浮胶囊 + 沉浸光感材质 + 光感形变按压反馈",
        "HarmonyOS 7 底栏按官方规范采用固定宽度居中（2 页签 168vp / 3 页签 248vp），不撑满屏幕",
        "HarmonyOS 7 底栏选中态使用品牌高亮色 + 细微弹跳动效，不再沿用 HyperOS4 的灰色椭圆遮罩",
        "HarmonyOS 7 底栏按压光效改为「照亮容器边缘」的环境光，从指尖向外衰减，配合容器轻微形变",
        "HarmonyOS 7 底栏底部补充渐变颜色蒙层（与页面背景色一致），增强界面空间感",
        "设置-外观新增「HarmonyOS 7」底栏选项，与 iOS27、HyperOS4 三选一",
    )),
    UpdateLogItem("2.1.5", "2026-09-09", listOf(
        "底栏切换动画掉帧优化（iOS27）：点击驱动的滑动按压峰值从 1.0 降到 0.5，lens/阴影半径减半，转场中 AGSL 采样成本大降",
        "底栏切换动画掉帧优化（iOS27）：静止时彻底摘掉 lens/chromaticAberration/highlight/shadow/innerShadow（之前传 0 半径仍走 shader 路径，每帧白付采样）",
        "底栏切换动画掉帧优化（HyperOS4）：遮罩滑动/缩放/变色从重组驱动（animateXxxAsState）改为 Animatable + snapshotFlow 事件驱动，动画过程零重组，drawBackdrop 节点不再反复重建",
    )),
    UpdateLogItem("2.1.4", "2026-09-09", listOf(
        "HyperOS4 底栏按压交互修正：按下不再是圆形涟漪，而是椭圆遮罩整体缩小、加深",
        "按住任意标签时遮罩会跟随滑动到该标签，松开未点击则弹回选中项，与 HyperOS4 真机行为一致",
    )),
    UpdateLogItem("2.1.3", "2026-09-09", listOf(
        "重做 HyperOS4 底栏：容器改为真正的毛玻璃（backdrop 取样 + 弱模糊），背景内容可以透出来",
        "选中态改为跟随滑动的浅灰椭圆遮罩（HyperOS4 的标志性元素），去掉之前误加的底部指示线",
        "胶囊边缘补一圈浅色描边，按下时标签中心出现深灰圆形涟漪，与参考图一致",
    )),
    UpdateLogItem("2.1.2", "2026-09-09", listOf(
        "新增 HyperOS4 风格底栏：可在设置-外观中切换",
        "HyperOS4 底栏：悬浮胶囊容器，图标+文字垂直排列，选中态高对比深色，未选中态柔和灰色",
        "HyperOS4 底栏按下反馈：在按下的标签背后出现深色圆形涟漪，与参考图保持一致",
        "保留现有 iOS27 液态玻璃底栏作为默认样式",
    )),
    UpdateLogItem("2.1.1", "2026-09-09", listOf(
        "增强小红书链接识别：扩展短链变体(xhs.link)、更宽松的中英文括号/方括号包裹、更好的结尾标点清理",
        "小红书解析增加多路 fallback：__INITIAL_STATE__ → JSON-LD → og:image/meta → 移动 UA 重试 → 任意 xhscdn 图片兜底，降低「解析成功但看不到内容」的概率",
        "重写实况照片提取：同时探测 stream.h264/h265、video.url、livePhotoUrl、livePhotoMedia 等多条链路",
        "未登录/失效链接给出更明确的错误提示，而不是泛化的解析失败",
    )),
    UpdateLogItem("2.1.0", "2026-09-09", listOf(
        "重构解析结果页整体排版：合并原来的标题条+概览条为统一信息卡片，媒体区更突出，底部操作栏悬浮固定，复制/下载按钮不再跟随内容滚动",
        "修复解析后卡在结果页的死循环：上一版移除页面销毁时清空结果后，首页仍用持久状态跳转导致返回首页立刻再跳回结果页。现在把「解析成功跳结果页」改为一次性事件（SharedFlow），首页和历史页发起的解析统一在导航层处理",
        "结果页状态管理重新梳理：开始新解析时才清 result，页面销毁不动状态，同时保留空状态自动退回兜底",
        "视频结果页去掉重复信息面板，播放器区域更大，点击即播放；实况照片/图片网格的角标和操作按钮保持一致风格",
    )),
    UpdateLogItem("2.0.9", "2026-09-09", listOf(
        "修复解析结果页返回时偶发白屏：之前在结果页销毁时才清空解析结果，而 pop 转场期间页面还在屏幕上，清空瞬间整页塌成骨架屏；现在改为在开始新解析时才清结果，页面销毁不动任何状态",
        "结果页增加空状态兜底：若结果为空且不在加载中，自动退回上一页，不再停留在一个只有返回键的空白页",
    )),
    UpdateLogItem("2.0.8", "2026-09-09", listOf(
        "解析结果页进出不再卡顿：转场 340ms 期间先只画占位，动画落地后才加载图片 / 建播放器。Coil 解码首屏大图和 ExoPlayer 的 build+prepare 都是主线程重活，之前一直跟转场动画抢帧",
        "结果页新增概览条：一眼看清来源平台、媒体数量、实况张数",
        "历史记录里相同内容不再重复显示：按平台+标题去重（分享链接每次带的 token 都不同，只比 url 会漏）",
        "历史记录右上角新增「选择」：进入多选后可勾选批量删除，选中卡片描边高亮",
        "重做删除确认弹窗：换成 iOS 26 风格的圆角面板 + 红色删除图标，替换原来简陋的系统默认弹窗",
    )),
    UpdateLogItem("2.0.7", "2026-09-09", listOf(
        "揪出吸顶头那条线的真凶：backdrop 库的 drawBackdrop 默认会给形状画「高光描边 + 投影」（rim light），且不受滚动进度门控，静止时也画 —— 全宽矩形的描边下边缘就是那条线，之前换了三版渐变都没用",
        "吸顶头显式关闭 highlight / shadow，静止状态下顶部完全零绘制",
    )),
    UpdateLogItem("2.0.6", "2026-09-09", listOf(
        "重做吸顶头结构：玻璃层（含模糊）收窄到 bar 本体，下面另起一层不含模糊的纯色渐隐，整块遮罩不再比标题低一大截",
        "修复吸顶头底边的「线」：backdrop 的 blur 是整块矩形统一模糊，模糊区边缘必然是硬边；现在把它藏在 bar 底边并用接近不透明的底色压住",
        "修复底栏点击没有滑动动画：标签的点击之前直接调导航、绕过了内部选中状态，现在点击也走 animateToValue，玻璃遮罩会滑过去",
        "底栏外部选中态同步改用 rememberUpdatedState，不再因为 lambda 每次重组都换实例而反复重启观察",
    )),
    UpdateLogItem("2.0.5", "2026-09-08", listOf(
        "修复吸顶头底部的「线」：渐变方向反了——之前是顶部半透明、底部 100% 不透明页面底色，色带与下方未模糊内容形成硬边；现在改为顶部到 bar 底 80% frosted tint、bar 底以下 28dp 渐变到全透明，defocus→focus 软过渡",
        "渐变断点按状态栏高度动态计算（barEndFrac = (topInset+52dp)/(topInset+80dp)），刘海/挖孔屏上 bar 底位置仍准确",
        "去掉 vibrancy()：反编译确认其内部是 saturation=1.5x ColorFilter，在中性灰页面会形成偏色矩形；只保留 blur + tint，色靠 blur 自然混合，更接近 iOS Liquid Glass",
    )),
    UpdateLogItem("2.0.4", "2026-09-08", listOf(
        "关闭 Android 预测式返回手势：该手势会与自定义转场抢占控制权，导致返回动画错位/闪屏",
        "返回手势改由应用内统一接管，非首页/非引导页的返回行为完全一致",
        "修复解析结果页返回时的动画错误：视频播放器改用 TextureView，不再脱离图层变换产生黑块与错位",
        "修复实况照片预览弹窗关闭时的残影，退出前先暂停解码",
        "优化结果页返回卡顿：吸顶模糊图层改为滚动时按需挂载，未滚动时不再做全屏离屏合成",
        "优化结果页返回卡顿：清除结果只更新结果字段，避免首页多一次全量重组；图片网格补充稳定 key",
    )),
    UpdateLogItem("2.0.3", "2026-09-08", listOf(
        "修复页面未滚动时顶部就出现一块带硬边的「遮罩」：原因是吸顶头的 vibrancy 色彩滤镜（饱和度 1.5x）未随滚动淡出",
        "吸顶模糊改为滚动超过约 56dp 后才渐入，模糊区底部与页面底色无缝衔接，无分割线",
    )),
    UpdateLogItem("2.0.2", "2026-09-08", listOf(
        "修复小红书解析：补全 xhslink.cn 短链识别、短链自动展开，并加固 __INITIAL_STATE__ 中 undefined 的归一化",
        "小红书链接过期或缺少安全令牌时，改为提示「请在小红书 App 中重新分享」而不是直接报解析失败",
        "小红书视频解析新增 h265/av1 编码兜底，图集图片补充 urlPre 回退与实况照片识别",
        "抖音图文（图集）解析增强：优先取无水印原图 download_url_list，支持更多返回结构",
        "抖音短链解析新增多级兜底（移动端/桌面端 UA、JS 跳转页面），图文与视频自动适配接口参数",
        "顶部导航栏去掉分割线：改为下滑时渐变模糊过渡，向下自然消散",
        "重做页面转场：返回动画改为 iOS 视差推拉，并补齐页面底色，彻底消除切换白屏",
    )),
    UpdateLogItem("2.0.1", "2026-09-08", listOf(
        "修复抖音解析：分享页 SSR 字段下线导致解析失败，切换至 slidesinfo 专用接口，视频与图文均恢复",
        "修复小红书新版 xhslink.cn 短链无法识别的问题",
        "全部页面升级为 iOS 26 吸顶导航栏：返回按钮与标题固定顶部，滚动时呈现实时模糊遮罩",
        "修复底栏点击无选中动画的问题（仅按住滑动才生效）",
        "修复底部标签反复点击导致页面栈堆积、返回时概率白屏卡住的问题",
        "底栏尺寸与排版微调，标签切换改用状态保存式导航",
    )),
    UpdateLogItem("2.0.0", "2026-09-07", listOf(
        "解析系统全面重构：接入原生抖音/小红书解析引擎（移植自 parse-video-py），不再依赖第三方解析网站",
        "新增小红书实况照片（Live Photo）解析、预览播放与下载",
        "新增抖音图文/Live Photo 解析支持",
        "实况照片在网格与预览中显示「实况」徽标，可单独下载实况视频",
        "批量下载时自动同时保存实况图片与实况视频",
        "解析速度与成功率显著提升，抖音视频直链自动 302 跟随",
    )),
    UpdateLogItem("1.8.3", "2026-09-07", listOf("基于 iOS 26 Liquid Glass 设计规范重构全局 UI", "统一系统蓝主题色、更通透的玻璃面板、更精致的圆角与阴影", "优化页面转场动画与按钮按压反馈，提升整体流畅度")),
    UpdateLogItem("1.8.2", "2026-09-07", listOf("基于 NexioSchedule / HyperOS 4 风格重构全局 UI", "引入紫色主题、液态玻璃面板、Squircle 圆角与深色模式配色", "统一首页、结果页、设置页、历史记录、工具箱和引导页视觉")),
    UpdateLogItem("1.8.1", "2026-09-01", listOf("按 Backdrop 官方 Glass Bottom Bar 教程补齐共享 LayerBackdrop 采样结构，增强底栏背景内容的实时折射效果")),
    UpdateLogItem("1.8.0", "2026-08-20", listOf("彻底移除 Miuix，主题、图标、基础控件和模糊兼容层全部迁移到 AndroidX Compose 与 Liquid Glass")),
    UpdateLogItem("1.7.9", "2026-08-20", listOf("将图片转换、视频转音频和解析结果页面的基础控件迁移为 Material 3 Apple 化组件，移除旧 Miuix Scaffold 与 TopAppBar")),
    UpdateLogItem("1.7.8", "2026-08-20", listOf("恢复设置页的跟随系统、浅色和深色手动选择")),
    UpdateLogItem("1.7.7", "2026-08-20", listOf("提升液态玻璃底栏的可视对比度，优化白色基底、蓝色选中态、高光与阴影层次")),
    UpdateLogItem("1.7.6", "2026-08-20", listOf("标题与状态栏增加安全留白，底栏迁移液态玻璃选中胶囊的弹性滑动和折射动画")),
    UpdateLogItem("1.7.5", "2026-08-20", listOf("统一巡检各页面间距、标题留白、内容边距和结果页网格布局，优化小屏设备的可读性与底部避让")),
    UpdateLogItem("1.7.4", "2026-08-20", listOf("接入 AndroidLiquidGlass Backdrop 2.0.0，重构悬浮底栏的液态玻璃背景、选中胶囊和按压反馈")),
    UpdateLogItem("1.7.3", "2026-08-13", listOf("重做悬浮底部导航选中态、按压反馈和胶囊展开动画")),
    UpdateLogItem("1.7.2", "2026-08-13", listOf("恢复并重做关于页面", "保留版本信息、更新日志入口和服务说明")),
    UpdateLogItem("1.7.1", "2026-08-13", listOf("恢复设置页检查更新入口与状态反馈", "保留所有原有设置和更新检查功能")),
    UpdateLogItem("1.7.0", "2026-08-13", listOf("历史记录和更新日志切换为纯 Compose Apple 布局", "统一内容层卡片、空状态和删除确认交互")),
    UpdateLogItem("1.6.9", "2026-08-13", listOf("Apple 内容层与控制层布局重构", "玻璃效果收敛到导航与控制层")),
    UpdateLogItem("1.6.8", "2026-08-13", listOf("首页与导航切换为纯 Compose Apple 风格")),
    UpdateLogItem("1.6.7", "2026-08-13", listOf("优化小红书图片解析等待策略")),
    UpdateLogItem("1.6.5", "2026-08-13", listOf("修复 Android 16 Live Updates 通知")),
)

@Composable
fun UpdateLogScreen(onBack: () -> Unit) {
    IOS26Canvas {
        IOS26StickyPage(
            title = "更新日志",
            onBack = onBack,
            spacedBy = 14.dp,
        ) {
            item { IOS26PageHeader("更新日志", "LinkGrab 的每一次改进。", topPadding = 0.dp) }
            items(updateLogs.size) { index ->
                val item = updateLogs[index]
                IOS26Panel {
                    Text(
                        "v${item.version}",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = iosLabel(),
                        letterSpacing = (-0.3).sp,
                    )
                    Text(
                        item.date,
                        color = iosSecondaryLabel(),
                        modifier = Modifier.padding(top = 3.dp),
                        fontSize = 14.sp,
                    )
                    item.changes.forEach {
                        Text(
                            "•  $it",
                            color = iosSecondaryLabel(),
                            modifier = Modifier.padding(top = 10.dp),
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

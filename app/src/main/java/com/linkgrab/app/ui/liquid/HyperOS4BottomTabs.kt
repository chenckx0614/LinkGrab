package com.linkgrab.app.ui.liquid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.shapes.Capsule
import com.linkgrab.app.ui.theme.isInDarkTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * HyperOS 4 style floating capsule bottom bar.
 *
 * 参考图还原要点（与 iOS27 液态玻璃版的区别）:
 * 1. 容器是**毛玻璃胶囊** —— backdrop 取样 + 弱模糊(14dp)，半透明白底，
 *    背景内容能透出来；没有液态玻璃的 lens 折射和 vibrancy。
 * 2. 选中项背后是**浅灰色椭圆遮罩**(圆角 27dp 的小胶囊)，跟随选中位置
 *    滑动 —— 这是 HyperOS4 的标志性元素，不是底部指示线。
 * 3. 胶囊边缘有一圈浅色描边(顶部亮边)。
 * 4. 按下时在标签中心出现一个**深灰色圆形**涟漪(参考图二)。
 * 5. 图标/文字都是深色，选中与未选中只有轻微透明度差异，
 *    区分主要靠遮罩而不是颜色。
 */
@Composable
fun HyperOS4BottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.(
        selectIndex: (Int) -> Unit,
        reportPress: (index: Int, pressed: Boolean) -> Unit,
    ) -> Unit
) {
    val isLightTheme = !isInDarkTheme()

    // 毛玻璃容器：半透明白 + 边缘亮描边(浅色模式)
    val containerColor =
        if (isLightTheme) Color.White.copy(alpha = 0.55f)
        else Color(0xFF2C2C2E).copy(alpha = 0.65f)
    val strokeColor =
        if (isLightTheme) Color.White.copy(alpha = 0.65f)
        else Color.White.copy(alpha = 0.10f)
    // 选中遮罩：浅灰椭圆；按下时加深
    val maskNormalColor =
        if (isLightTheme) Color(0xFF3C3C43).copy(alpha = 0.12f)
        else Color.White.copy(alpha = 0.14f)
    val maskPressedColor =
        if (isLightTheme) Color(0xFF3C3C43).copy(alpha = 0.22f)
        else Color.White.copy(alpha = 0.24f)

    // 按压中的标签：遮罩会跟过去，松开(未点击)则弹回选中项。
    // 注意：pressedIndex 只在 snapshotFlow 里读取，组合期不读 ——
    // 这样按压/松开不会触发底栏重组，动画全程只有 draw/layout 失效。
    var pressedIndex by remember { mutableStateOf<Int?>(null) }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val tabWidthPx = constraints.maxWidth.toFloat() / tabsCount
        // 参考图（HyperOS 文件管理器）：遮罩与胶囊边缘贴合更紧
        val maskInset = 4.dp
        val maskInsetPx = with(density) { maskInset.toPx() }
        val maskWidth = with(density) { (tabWidthPx - maskInsetPx * 2).toDp() }

        val selectedIndexState by rememberUpdatedState(selectedTabIndex())

        // 动画全部用 Animatable + snapshotFlow 驱动：animateXxxAsState 是
        // 重组驱动的，target 每次变化都会重组整个底栏 lambda，drawBackdrop
        // 节点跟着重建 —— 这是点击掉帧的主因。Animatable 只在启动时
        // 改一次 target，动画过程零重组。
        val initialPosition = remember {
            val i = selectedTabIndex()
            if (isLtr) i.toFloat() else (tabsCount - 1 - i).toFloat()
        }
        val maskPosition = remember { Animatable(initialPosition) }
        val maskScale = remember { Animatable(1f) }
        val maskPress = remember { Animatable(0f) } // 0=常态 1=按下

        LaunchedEffect(Unit) {
            snapshotFlow {
                val i = pressedIndex ?: selectedIndexState
                if (isLtr) i.toFloat() else (tabsCount - 1 - i).toFloat()
            }.collectLatest { target ->
                maskPosition.animateTo(
                    target,
                    spring(dampingRatio = 0.82f, stiffness = 420f),
                )
            }
        }
        // 动画用外部 scope 启动：collectLatest 收到新值会取消 block（含子协程），
        // 在它里面 launch 的动画会被中途掐死——快速按压/松开交替时遮罩的
        // 缩放/加深停在中间态弹不回来。scope.launch 不受取消影响。
        val animationScope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            snapshotFlow { pressedIndex != null }.collectLatest { pressed ->
                animationScope.launch {
                    maskScale.animateTo(
                        if (pressed) 0.88f else 1f,
                        spring(dampingRatio = 0.68f, stiffness = 520f),
                    )
                }
                animationScope.launch { maskPress.animateTo(if (pressed) 1f else 0f, tween(140)) }
            }
        }

        // 遮罩常态色与按压色预先建好：绘制期只做 alpha 叠加，
        // 不再每帧 lerp 出新 Color 实例，消除动画中的对象分配与 GC 抖动。
        val maskPressColor =
            if (isLightTheme) Color(0xFF3C3C43)
            else Color.White

        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    // 弱模糊即可，HyperOS4 的毛玻璃没有液态玻璃那么强的折射。
                    // 默认 highlight/shadow 会画出多余的描边与投影，必须关掉。
                    effects = { blur(14.dp.toPx()) },
                    highlight = { null },
                    shadow = { null },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .border(1.dp, strokeColor, RoundedCornerShape(28.dp)),
        ) {
            // ---- 椭圆遮罩：跟随选中项，按压时跟到按压项并缩小加深 ----
            // 位置/缩放/颜色都在 draw 或 layout 阶段读 Animatable.value，
            // 动画过程零重组。
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            x = (maskPosition.value * tabWidthPx + maskInsetPx).roundToInt(),
                            y = 0,
                        )
                    }
                    .padding(vertical = maskInset)
                    .width(maskWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleX = maskScale.value
                        scaleY = maskScale.value
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .drawBehind {
                        // 常态色打底，按压色以 alpha 叠加，避免每帧 lerp 分配。
                        drawRect(maskNormalColor)
                        val press = maskPress.value
                        if (press > 0.001f) {
                            drawRect(maskPressColor, alpha = 0.10f * press)
                        }
                    },
            )

            // ---- 标签内容 ----
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                content = {
                    content(
                        { index -> onTabSelected(index) },
                        { index, pressed -> pressedIndex = if (pressed) index else null },
                    )
                },
            )
        }
    }
}

@Composable
fun HyperOS4TabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onPress: (pressed: Boolean) -> Unit = {},
    onClick: () -> Unit,
) {
    val isLightTheme = !isInDarkTheme()

    // 参考图里两个标签的图标/文字都是深色，区别只在遮罩；
    // 未选中仅轻微变淡。
    val activeColor = if (isLightTheme) Color(0xFF1A1A1A) else Color(0xFFFFFFFF)
    val inactiveColor =
        if (isLightTheme) Color(0xFF1A1A1A).copy(alpha = 0.55f)
        else Color.White.copy(alpha = 0.60f)
    val contentColor by animateColorAsState(
        targetValue = if (selected) activeColor else inactiveColor,
        animationSpec = tween(180),
        label = "contentColor",
    )

    // 按压状态上报给父组件：遮罩会跟到被按压的标签，而不是在标签上画圆。
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    LaunchedEffect(isPressed) { onPress(isPressed) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.15).sp,
                lineHeight = 12.sp,
            )
        }
    }
}

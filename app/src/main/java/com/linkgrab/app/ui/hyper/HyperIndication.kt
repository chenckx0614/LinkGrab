package com.linkgrab.app.ui.hyper

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * MIUI 的点击指示：**矩形叠色**，不缩放、不涟漪。
 *
 * 照抄 miuix 的 `MiuixIndication`：
 * - 按下叠加 10% 前景色，悬停 6%，聚焦 8%，长按 10%（可叠加）；
 * - 进 spring(1.0, 0.2s)、出 spring(0.95, 0.35s)，用 folme 的阻尼/响应参数，
 *   出来比进去慢一档，手感像「按下去立刻有、松手慢慢退」。
 *
 * 这是 HyperOS 和 iOS 主题观感差异的关键一环：iOS 那边按下去是整块缩小，
 * MIUI 这边形状不动，只是颜色压深一层。
 */
@Immutable
class HyperIndication(
    private val color: Color = Color.Black,
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        Node(interactionSource, color)

    override fun equals(other: Any?): Boolean =
        this === other || (other is HyperIndication && other.color == color)

    override fun hashCode(): Int = color.hashCode()

    private class Node(
        private val interactionSource: InteractionSource,
        private val color: Color,
    ) : Modifier.Node(), DrawModifierNode {

        private var pressed = false
        private var hovered = false
        private var focused = false
        private var alpha = Animatable(0f)
        private var entering: Job? = null
        private var settling: Job? = null

        private fun target(): Float {
            var t = 0f
            if (hovered) t += 0.06f
            if (focused) t += 0.08f
            if (pressed) t += 0.10f
            return t
        }

        private fun animateTo(spec: SpringSpec<Float>, afterPress: Boolean) {
            val to = target()
            if (afterPress || to == 0f) {
                // 松手：先等按下那段跑完，再退色，避免「一松手就弹回」的跳变
                settling?.cancel()
                settling = coroutineScope.launch {
                    entering?.join()
                    alpha.animateTo(to, spec)
                }
            } else {
                entering?.cancel()
                settling?.cancel()
                entering = coroutineScope.launch { alpha.animateTo(to, spec) }
            }
        }

        override fun onAttach() {
            coroutineScope.launch {
                interactionSource.interactions.collect { interaction ->
                    val wasPressed = pressed
                    val wasHovered = hovered
                    val wasFocused = focused
                    when (interaction) {
                        is PressInteraction.Press -> pressed = true
                        is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
                        is HoverInteraction.Enter -> hovered = true
                        is HoverInteraction.Exit -> hovered = false
                        is FocusInteraction.Focus -> focused = true
                        is FocusInteraction.Unfocus -> focused = false
                        else -> return@collect
                    }
                    val spec = when {
                        wasPressed != pressed -> if (pressed) PressEnter else PressExit
                        wasHovered != hovered -> if (hovered) HoverEnter else HoverExit
                        wasFocused != focused -> if (focused) HoverEnter else HoverExit
                        else -> return@collect
                    }
                    animateTo(spec, afterPress = wasPressed && !pressed)
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            val a = alpha.value
            if (a > 0.001f) drawRect(color = color, alpha = a, size = size)
        }

        private companion object {
            val PressEnter = folmeSpring<Float>(damping = 1.0f, response = 0.20f)
            val PressExit = folmeSpring<Float>(damping = 0.95f, response = 0.35f)
            val HoverEnter = folmeSpring<Float>(damping = 1.0f, response = 0.60f)
            val HoverExit = folmeSpring<Float>(damping = 0.96f, response = 0.20f)
        }
    }
}

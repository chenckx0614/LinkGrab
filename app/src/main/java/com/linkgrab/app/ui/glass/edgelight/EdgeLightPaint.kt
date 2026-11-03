package com.linkgrab.app.ui.glass.edgelight

import android.graphics.BlurMaskFilter
import androidx.compose.ui.graphics.Paint
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asComposeShader

/**
 * EdgeLight 用的两个 Paint 工具，等价于 backdrop fork 里 com.kyant.backdrop.internal 的
 * blur / setRuntimeShader（那是模块 internal，应用侧拿不到），这里用公开 API 自实现。
 * backdrop 2.0.0 已公开 RuntimeShader 与 asComposeShader，无需任何内部 API。
 */
@Suppress("DEPRECATION")
internal fun Paint.edgeLightBlur(radius: Float) {
    this.asFrameworkPaint().maskFilter =
        if (radius > 0f) BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        else null
}

@Suppress("DEPRECATION")
internal fun Paint.edgeLightRuntimeShader(runtimeShader: RuntimeShader?) {
    this.asFrameworkPaint().shader = runtimeShader?.asComposeShader()
}

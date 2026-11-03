package com.linkgrab.app.ui.glass.edgelight

// AGSL shader 字符串，fork 自 Kyant0/backdrop（Apache-2.0，经 HaoZai000/NexioSchedule 转手）。
// 颜色 uniform 用 float4 而非 layout(color) half4：跨平台打包对齐考虑，保持原样。

internal const val RoundedRectSDF = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}"""

internal const val EdgeLightDirectionalShaderString = """
uniform float2 size;
uniform float4 cornerRadii;
uniform float4 color;
uniform float width;
uniform float blurRadius;
uniform float intensity;
uniform float angle;
uniform float falloff;

$RoundedRectSDF

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(coord, cornerRadii);
    
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    
    float2 normal = float2(cos(angle), sin(angle));
    float d = dot(grad, normal);
    
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    float edgeDist = abs(sd);
    
    float halfWidth = width * 0.5;
    float innerEdge = halfWidth;
    float outerEdge = halfWidth + blurRadius;
    
    float edgeAlpha = 1.0 - smoothstep(innerEdge, outerEdge, edgeDist);
    
    float directionalAlpha = pow(abs(d), falloff);
    
    float alpha = edgeAlpha * directionalAlpha * intensity;
    
    return color * alpha;
}"""

internal const val EdgeLightGlowShaderString = """
uniform float2 size;
uniform float4 cornerRadii;
uniform float4 color;
uniform float width;
uniform float blurRadius;
uniform float intensity;
uniform float glowSize;

$RoundedRectSDF

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(coord, cornerRadii);
    
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    
    float edgeDist = abs(sd);
    
    float halfWidth = width * 0.5;
    float coreAlpha = 1.0 - smoothstep(halfWidth * 0.5, halfWidth, edgeDist);
    
    float glowAlpha = exp(-edgeDist * edgeDist / (2.0 * glowSize * glowSize));
    
    float alpha = (coreAlpha + glowAlpha * 0.5) * intensity;
    
    return color * alpha;
}"""

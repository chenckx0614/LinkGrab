# 两个 COUI（ColorOS UI）库的对比与可用性

查于 2026-11-03。两个库名字撞了，但不是一个东西：一个是完整控件库，一个是视觉效果库。

| | [suqi8/coui](https://github.com/suqi8/coui) | [ItosEO/COUI-Kit](https://github.com/ItosEO/COUI-Kit) |
|---|---|---|
| 对标 | ColorOS 16 | ColorOS 17「凝光视效」 |
| 出身 | [Miuix](https://github.com/compose-miuix-ui/miuix) 的 fork，重做成 ColorOS 语言 | 从零写的 Android 库，参考真机实现 |
| 平台 | Compose Multiplatform（Android / iOS / desktop / web） | Android only |
| 规模 | 111 个控件 + 156 图标 + 模糊/squircle 独立模块 | 91 个文件，一半是 effect |
| 组件 | 全：Card / 列表行 / Switch / 下拉 / 顶栏 / 底栏 / Picker / 搜索 / 滑块… | 偏少：Checkbox / Chip / Dialog / FAB / Menu / 顶栏 / Slider / Switch |
| 许可 | Apache-2.0 | MPL-2.0 + NOTICE 第三方声明 |
| 坐标 | `io.github.suqi8.coui.kmp:coui-ui:1.1.0` | `io.github.itoseo:coui:0.0.1-alpha3` |

一句话：**要做完整 ColorOS 风格页面，看 coui；要抄凝光视效（模糊材质、轮廓光、跟手拖影、超椭圆），看 COUI-Kit。**

---

## 一、能不能直接依赖：都不能

| | Kotlin | Compose | AGP | 其它门槛 |
|---|---|---|---|---|
| LinkGrab | 2.3.20 | BOM 2025.06.00（1.8.x） | 8.10 | minSdk 31 |
| coui | 2.4.10 | CMP 1.11.1 | 9.3.1 | — |
| COUI-Kit | 2.4.20 | BOM 2026.08.01 / foundation 1.12.0 | 9.4.1 | 材质要 API 33，顶栏按钮要 API 34 |

和上次引 miuix 撞的是同一堵墙：**Kotlin metadata 版本更高，编译器直接读不了**；就算读得了，Compose runtime ABI 也对不上。要升级到 Kotlin 2.4 + Compose 1.12 才能谈。

许可上：coui 是 Apache-2.0，**源文件可以整段拷进项目改包名**（保留版权声明）；COUI-Kit 是 MPL-2.0，是文件级 copyleft，拷代码要连带开源那个文件——**只借数值、算法思路没问题，别整文件搬**。

---

## 二、值得抄的规格

### 1. 色板（coui `theme/Colors.kt`）

| 语义 | 亮 | 暗 |
|---|---|---|
| primary | `#0066FF` | `#247CFF` |
| surface（页面底） | `#F0F1F2` | `Black` |
| background（更外层） | — | `#1E1E1E` |
| surfaceContainer（卡片） | `White` | `#1AFFFFFF`（半透明白，不是实色） |
| surfaceContainerHigh（按下态） | `#E6E6E6` | `#33FFFFFF` |
| dividerLine | `#1F000000` | `#33FFFFFF` |
| 主文字 onSurface | `#E6000000` | `#E6FFFFFF` |
| 摘要 onSurfaceVariantSummary | `#66000000` | `#66FFFFFF` |

和 HyperOS 的最大差别：**ColorOS 深色卡片是半透明白叠在黑底上，不是不透明 #242424**；分隔线用带 alpha 的黑/白，不是实色灰。

### 2. 圆角：两套完全不同的数学

coui / ColorOS 16 —— 圆角块放大法（`SquirclePath.kt`）：

```
tile   = radius × 1.2819        // 角块尺寸
handle = tile × (1 - 0.643)     // 立方贝塞尔控制点比例
```
宽高各切掉 `tile`，四角用两段 cubicTo 过渡。是"把圆角矩形四边中点外的部分拉成连续曲率"的简化版。

COUI-Kit / ColorOS 17 —— Lp 超椭圆（`shape/CouiShape.kt`）：

```
(a, b) 满足 (a^w + b^w)^(1/w) = 1，w = cornerWeight（默认 2.5）
diagonal = 0.5^(1/w)
b = (1 - a^w)^(1/w)，a 从 0 走到 diagonal
```
w=2 退化成正圆，w=2.5 是 ColorOS 的观感。按 0.05px 误差自动算分段数，折线逼近（不是贝塞尔）。

HyperOS 那套（miuix fork 的 G2 连续曲率）是第三种：四段 cubic + arcTo，靠曲率过渡区（1.5287×半径）控制。

### 3. 动效：folme 弹簧公式

两个库用同一个公式（coui `anim/COUIEasing.kt`、Kit `motion/CouiSpring.kt`）：

```kotlin
stiffness = (2π / response)²      // response 单位秒
dampingRatio = 1f - bounce
```

ColorOS 的实际取值：

| 场景 | response | bounce |
|---|---|---|
| 菜单弹出（位移） | 0.35 | 0.2（damping 0.8） |
| 菜单收回 | 0.25 | 0（damping 1） |
| 按压按下 | 0.25 / 0.3（长条） | 0 |
| 按压松开 | 0.5 | 0.5 / 0.6（长条） |

和 HyperOS 的区别一眼可见：**ColorOS 松手回弹有回弹（bounce 0.5~0.6），HyperOS 是 no-bouncy**。

### 4. 按压反馈：两个库自己就不一致

- **coui（ColorOS 16）**：叠色。Card 的 `pressedColor = surfaceContainerHigh`，填充色 lerp 过去，形状不动。
- **COUI-Kit（ColorOS 17）**：缩放。`CouiPressMotion` 按控件面积和宽高比算目标：

```
面积 20dp²→150dp² 映射 scale 0.85→0.97
宽高比 >2（"糖果条"）再按 2→15 映射 0.85→1.00，两者取平均
```

这跟 HyperOS（只叠色不缩放）是两代思路，ColorOS 17 反而回到了缩放。

### 5. 控件数值（coui）

| 件 | 值 |
|---|---|
| Card | 圆角 12dp，卡内 padding 0（内容自己给），头尾拼接留 2dp |
| Button | 高 44dp / 圆角 22dp / 左右 12dp；小号 28dp / 14dp |
| Divider | 0.33dp，左侧 inset 16dp |
| SmallTitle | 12sp Medium，`PaddingValues(32, 8)`，最小高 16dp |
| Switch | 轨道 38×24，拇指 18，边距 3，拇指阴影 radius 2.29 / offsetY 1.14 |
| TopAppBar | 收起 52dp、展开 107dp、大标题上边距 54dp、下边距 12dp，收起时底边露发丝线 |
| ListPopup | 宽 178~232dp，圆角 12dp，项分隔 0.33dp（inset 16dp，带图标 52dp），按压 `#1F000000` / `#33FFFFFF` |

对比 HyperOS 的同一批数值：卡片 20dp 圆角、行高 56dp、按钮 insideMargin 16×13 + 17sp、菜单宽 200dp 圆角 25dp。**ColorOS 整体更紧凑、更小圆角。**

### 6. COUI-Kit 独有的视觉层（想再进一步时看）

- `effect/CouiMaterial.kt`：RuntimeShader 做亮度混色（luminosity + Overlay / ColorDodge），API 33+
- `effect/CouiContour*.kt`：轮廓光，和 CouiShape 共用同一条 Lp 边界
- `effect/CouiSpotlight.kt`：菜单选中项的聚光
- `effect/CouiShadow*.kt`：自绘投影（几何 + 着色器 + 三角化），不靠 elevation
- `effect/CouiMetaball*`：底部导航的融合水滴

这些都要 API 33/34，且是自绘管线，移植成本远高于抄数值。

---

## 三、给 LinkGrab 的结论

1. **别试图引依赖**，除非先整体升到 Kotlin 2.4 / Compose 1.12 —— 那是一次大版本迁移，和主题无关。
2. 真要做第三个主题（ColorOS），最小的落法是复用现有分派结构：`AppVisualStyle` 加 `COLOR_OS`，`isHyperOsStyle()` 旁边加 `isColorOsStyle()`，色板抄上面的表，形状换成 Lp 超椭圆（w=2.5），动效换 folme 公式。
3. **形状别混**：现有 `HyperSquircleShape` 是 G2 连续曲率，ColorOS 是 Lp 超椭圆，两者不能同一个 shape 顶替，观感差别在直边到圆角的过渡段。

---

## 四、在 LinkGrab 的落地（3.3.0）

主题加在 `ui/coloros/`，和 `ui/hyper/` 平级，分派口仍是 `AppVisualStyle`。

| 文件 | 内容 |
|---|---|
| `CouiShape.kt` | `CouiShape`：Lp 超椭圆（w=2.5），折线按 0.05px 误差自动分段；`CouiCorners` 档位 18/12/10/8dp、按钮胶囊 |
| `CouiMotion.kt` | `couiSpring(response, bounce)` = `stiffness=(2π/response)²`；`CouiMotion` 参数表；`Modifier.couiPressScale`（按面积/宽高比算 scale） |
| `CouiDesign.kt` | 色板（coui 默认主题）、`CouiCard`、`CouiRow`、`CouiSectionTitle`、`CouiPrimaryButton` / `CouiSecondaryButton` |
| `CouiEffects.kt` | `Modifier.couiContour`：135° 渐变内描边（凝光轮廓光） |

**用上的**：

- 圆角：卡片 12dp 超椭圆；大件 18、小件 10/8
- 色板：亮 `#F0F1F2` 底 + 白卡 / 暗纯黑底 + 10% 白卡；primary `#0066FF` / `#247CFF`
- 排版：行高 48dp、标题 17sp Medium、摘要 14sp、小标题 12sp Medium、大标题 32sp、折叠标题 20sp
- 动效：行的按压是叠色（fadeIn 0.35/0.2 → fadeOut 0.25/0），按钮是**按面积缩放**（`0.85~0.97`，长条再叠宽高比那一路），松开带 0.6 回弹
- 效果：卡片轮廓光（深色下 8% 白，勾出半透明卡的边）；下拉菜单命中项改成左侧散开的聚光

**没做的，以及原因**：

- 材质混色（`CouiMaterial` 的亮度混色 + ColorDodge）：要 API 33 的 RuntimeShader，还得挂在现有 backdrop 采样链上，收益只在菜单那一层
- 自绘投影 / 浮动几何 / metaball：ColorOS 17 的整套自绘管线，移植成本远超配色层面
- 聚光跟随手指：原版按触点扩散，这里只做了静态的选中态

**以后要加**：升级到 Kotlin 2.4 + Compose 1.12 后，coui 可以整包替换掉 `ui/coloros/`（Apache-2.0，注意保留版权声明）；COUI-Kit 仍是 MPL-2.0，只能借算法。

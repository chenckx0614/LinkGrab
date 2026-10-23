# LinkGrab 项目长期约定

## 版本与发布
- **每次代码修改之后必须升版本号**，versionCode +1、versionName 按 3.0.x 递增（用户明确要求"每次都得"）。
- 更新日志（UpdateLogScreen.recentLogs）随版本号一起加条目，每条一句话。
- 交付链路：assembleRelease（release + debug 签名 + R8），出包后双 ABI 覆盖 dist/。
- 推送节奏：本地 commit → 用户确认 → 代理 7897 push GitHub/Gitee；Release 需用户说一声才发。

## UI 约定
- 图片展示一律原比例（FillWidth），禁止 FillBounds / 固定 aspectRatio 拉伸。
- 毛玻璃视觉统一：吸顶栏与底栏同配方 blur 18dp + 半透明着色，不用高不透明度实色遮罩盖住模糊。
- 按钮样式：白底胶囊 + iosBlue 蓝字（用户否掉了磨砂色团效果）。
- 卡片列表要有间距（14dp 节奏），引导页与设置页排列方式保持一致（竖排大图）。

## 动画规范（长期有效，写进 CLAUDE.md）
- 所有动画默认：**弹簧动画（spring）**，且默认不回弹（`Spring.DampingRatioNoBouncy`）。
- 拖动过程必须 1:1 跟手（直接用手势位移驱动），松手后用弹簧接住手指速度（把速度传给 spring，如 `initialVelocity`）。
- 颜色 / 透明度等不挪位置的属性照常淡入淡出（tween / animate*AsState）。

## 引导页（GuideScreen）
- 四页推进式：欢迎「3」→ 解析（guide_parse.png）→ 历史记录（guide_history.png）→ 底栏样式选择。
- 页间推进：旧页左移半屏渐隐 + 新页右滑入；完成时背景模糊 + 白纱展开 + 样式卡落位衔接真底栏（barEntryFrom Rect）。
- guide_shown 语义：key 不存在（首次安装）才显示。

## 已知教训
- 不要尝试引入 miuix 整体替换 UI（fork 深度耦合课程表项目，已回退）。
- ResultScreen 底栏 backdrop 必须用独立的 contentBackdrop，不能采样整个 NavHost（循环引用会崩溃）。
## 发布约定\n- Gitee 仓库附件配额 1GB：Release 附件只保留最近两个版本，发新包前删旧附件（本地 dist/ 有全量备份）。
- 签名：release 用固定 keystore app/linkgrab-sign.jks（gitignored），密码在 local.properties 的 LINKGRAB_*（gitignored，路径用正斜杠）。**此 keystore 丢失 = 所有用户必须卸载重装，务必备份**（建议复制到云盘/安全位置）。

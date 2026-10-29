# 抖音 web 签名参考实现（Apache-2.0，来自 Evil0ctal/Douyin_TikTok_Download_API v5.1.8）

Kotlin 移植在 `app/src/main/java/com/linkgrab/app/data/api/DouyinWebSigner.kt`，
单测 `DouyinWebSignerTest` 用 golden.json 钉死。

抖音如果换签名版本（a_bogus 结构失效的信号：detail 接口持续 403 / 返回空），
在这里重新跑 `python gen_golden.py`（目录内需 dtk 包结构，见 gen_golden.py 头部），
对照更新 Kotlin 端。上游仓库更新时优先看 `src/dtk/signing/native/`。

实测记录（2026-10-29）：detail 接口 = ttwid + uifid（页面 JS 铸造）+ a_bogus +
x-secsdk-web-signature，缺 uifid 一律 403 Uifid Not Found；msToken 可伪造。
PC 无头浏览器会被验证码拦，App 内 WebView（真机）不会。

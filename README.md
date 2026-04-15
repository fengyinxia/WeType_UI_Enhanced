# WeType_UI_Enhanced

为微信输入法（WeType）提供界面美化与窗口效果增强。

> 本项目 fork 自 [NEORUAA/WeType_UI_Enhanced](https://github.com/NEORUAA/WeType_UI_Enhanced)。

## 项目定位

- 专注 `com.tencent.wetype` 的 UI 增强与体验优化
- 重点是背景、模糊、圆角、高光、按键与候选栏外观
- 不再以 MIUI 全面屏限制解锁、多输入法适配为主要目标
- 如果你更需要旧版多输入法/系统侧兼容能力，请参考上游项目

## 功能

- 自定义窗口背景颜色与透明度
- 调整模糊强度与圆角大小
- 开启/关闭边缘高光并调节强度
- 调整按键颜色、按键透明度、候选词背景透明度
- 调整候选词背景圆角与拼音左边距
- 支持寄生设置页：点击 WeType 关于页 logo 进入模块设置

## 使用方法

- Xposed API Version >= 93
- 在 LSPosed 作用域中勾选 `com.tencent.wetype`
- 重启 WeType 作用域后打开模块
- 也可通过 WeType 关于页 logo 进入寄生设置页

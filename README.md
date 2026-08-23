# General Tools（通用工具）

一个 Minecraft 1.20.1 Forge 模组（modid: `generaltools`），提供多功能瑞士刀（Swiss Knife）。

## 特性

- **瑞士刀**：可放入 7 种工具槽位——剑、镐、斧、铲、锄、剪刀、打火石
- **自动 / 锁定模式**：GUI 按钮切换，服务端同步；自动模式按目视方块即时选择工具并切换贴图
- **完整工具继承**：
  - 挖掘速度、正确工具判定、挖掘完成事件全部委托当前生效槽位工具
  - 对方块右键直接使用槽位工具（完整继承其功能/充能，如锄耕地、斧去皮、剪刀剪羊毛）
  - 附魔自动继承（效率、时运、精准采集、锋利等由原版机制生效）
  - 攻击时继承剑槽伤害并消耗对应工具耐久（剩余耐久 ≤ 1 自动禁用功能保护工具不损坏）
- **GUI**：SophisticatedBackpacks 风格界面，模式按钮图集，槽位背景图标
- **扳手模式（通用扳手兼容，无需放入工具）**：
  - 瑞士刀自带 `forge:tools/wrench` / `forge:wrenches` / `c:tools/wrench` / `c:wrenches` 通用扳手标签，**扳手能力常态生效**，可被 AE2、机械动力（Create）、格雷科技（GTCEu）、通用机械（Mekanism）、Pipez 等模组识别为扳手，跨模组执行通用扳手操作（旋转、拆除、管道操作等）
  - **锁定扳手模式**：右键**只生效扳手**，屏蔽其他工具操作
  - **其他模式**：扳手与其他工具**共同生效**——AE2 等模组方块由对应 wrench 机制接管，普通方块仍使用当前工具功能
- **自定义 tooltip**：已装备工具列表 + 当前模式
- **黑名单系统**（`config/generaltools_blacklist.toml`）：
  - 按物品 ID 禁用：`blacklistedTools`
  - 按物品标签禁用：`blacklistedTags`
  - 内置标签 `generaltools:blacklist`：带此标签的物品同样禁止放入（可在数据包中为该标签添加物品）
- **合成配方**：铜锭 ×4 + 铁锭 ×1 + 铁粒 ×1 → 瑞士刀 ×1

## 环境要求

- Minecraft：1.20.1
- Forge：47.4.22
- Java：17+

## 构建

```bat
.\gradlew.bat build
```

构建产物：`build/libs/generaltoolsV1.5_1.20.1Forge.jar`，放入 `mods` 目录即可。

## 配置

首次运行后在 `config` 目录生成 `generaltools_blacklist.toml`，可配置：
- 黑名单：`blacklistedTools`（物品 ID）、`blacklistedTags`（物品标签）

## 项目结构

```
src/main/java/com/example/examplemod/
├── ExampleMod.java       主类（注册、创造标签、网络通道、GUI 注册）
├── Config.java           配置（黑名单 ID / Tag / 内置标签）
├── SwissKnifeItem.java   瑞士刀物品（模式、工具委托、通用扳手兼容、附魔、tooltip）
├── SwissKnifeMenu.java   容器菜单（7 工具槽 + 黑名单判定）
├── SwissKnifeScreen.java 客户端 GUI
├── ModeSetPacket.java    模式切换网络包
└── ClientEvents.java     客户端自动模式贴图跟随
src/main/resources/
├── assets/generaltools/  模型、贴图、语言（中/英）
└── data/                 配方、标签、forge wrench 兼容标签
```

## 许可

本模组基于 Minecraft Forge 开发，Forge 相关许可见 `LICENSE.txt`。模组自身代码许可请以发布仓库声明为准。

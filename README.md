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

构建产物：`build/libs/generaltools-1.0.0.jar`，放入 `mods` 目录即可。

## 配置

首次运行后在 `config` 目录生成 `generaltools_blacklist.toml`，按文件内注释填写黑名单即可。

## 项目结构

```
src/main/java/com/example/examplemod/
├── ExampleMod.java       主类（注册、创造标签、网络通道、GUI 注册）
├── Config.java           黑名单配置（ID / Tag / 内置标签）
├── SwissKnifeItem.java   瑞士刀物品（模式、工具委托、附魔、tooltip）
├── SwissKnifeMenu.java   容器菜单（7 工具槽 + 黑名单判定）
├── SwissKnifeScreen.java 客户端 GUI
├── ModeSetPacket.java    模式切换网络包
└── ClientEvents.java     客户端自动模式贴图跟随
src/main/resources/
├── assets/generaltools/  模型、贴图、语言（中/英）
└── data/generaltools/    标签、合成配方
```

## 许可

本模组基于 Minecraft Forge 开发，Forge 相关许可见 `LICENSE.txt`。模组自身代码许可请以发布仓库声明为准。

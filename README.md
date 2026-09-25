# General Tools 1.21.1 NeoForge

General Tools 的 Minecraft 1.21.1 NeoForge 移植版，提供带 7 个工具槽和通用扳手能力的瑞士刀。

## 开发环境

- Minecraft 1.21.1
- NeoForge 21.1.216
- ModDevGradle 2.0.144
- Gradle 9.2.1
- Java 21

开发环境使用 NeoForge 21.1.216 编译；发布 JAR 的运行依赖范围为 NeoForge 21.1.0 及以上，不会强制整合包升级到相同的补丁版本。

首次导入时使用 IntelliJ IDEA 打开本目录的 `build.gradle`，等待 Gradle 同步完成。

## 构建

```powershell
.\gradlew.bat build --console=plain
```

构建产物：`build/libs/generaltoolsV1.5.4_1.21.1NeoForge.jar`。

## 主要变化

- Forge 注册、配置和事件 API 已迁移到 NeoForge。
- 工具槽由旧版物品 NBT 改为 `minecraft:container` 数据组件。
- 模式使用 `minecraft:custom_data` 和 `minecraft:custom_model_data` 数据组件保存。
- GUI 模式切换使用原版菜单按钮同步，不再使用旧版 `SimpleChannel` 消息。
- 配方与标签目录已更新为 1.21 的单数目录格式。

配置文件仍为 `config/generaltools_blacklist.toml`。
## License

General Tools is licensed under the [MIT License](LICENSE).
Copyright (c) 2026 c09nat.

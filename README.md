# Minecraft Mods

Minecraft 模组源码合集，每个模组位于 `mods/` 下的独立目录，分别维护构建配置、说明文档和许可证。

| 模组 | 版本 | 说明 | 游戏 / 加载器 |
| --- | --- | --- | --- |
| [Aviation](mods/aviation/README.md) | 1.0.4 | 机场、普通飞机、战斗机与客机 | Minecraft 26.3 / Fabric |

## 目录结构

```text
mods/
  aviation/     # 飞机与机场模组，独立 Gradle 项目
```

后续模组放在 `mods/<mod-name>/`，并在上表补充链接。目录名保持稳定，版本通过各模组的版本配置和 Git 历史管理。

## 构建 Aviation

需要 Java 25，在仓库根目录执行：

```sh
cd mods/aviation
./gradlew build
```

构建产物位于 `mods/aviation/build/libs/`。安装方法、玩法和测试命令见 [Aviation 说明](mods/aviation/README.md)。

## 许可证

每个模组遵循自身目录中的许可证。Aviation 的许可证见 [mods/aviation/LICENSE](mods/aviation/LICENSE)。

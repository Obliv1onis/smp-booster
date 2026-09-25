# SMP Booster

适用于 Minecraft Java Edition 26.3 / Paper 26.3 的可配置限制与轻量反作弊插件。

## 构建与安装

```bash
./gradlew build
```

把 `build/libs/SMP Booster-1.0.jar` 放进 Paper 服务端的 `plugins` 目录，然后启动服务端。首次启动后配置位于 `plugins/smp-booster/config.yml`，修改后重启服务端生效。

## 限制配置

- `disabled-from-level: 5`：从 V 级起禁止，因此物品会被降到 IV。
- `disabled: true`：完全禁用附魔或药水效果。附魔默认只从物品上移除。
- `remove-item: true`：与附魔的 `disabled: true` 搭配，删除带有该附魔的整组物品。默认的消失诅咒示例已开启此项。
- 力量 II 等原版强效药水会变为对应的 I 级药水；自定义药水效果也会被降级。
- 末影珍珠和紫颂果开关只禁用传送用途，不会删除物品。
- 爆炸倍率 `1.0` 是原版、`0.5` 是一半、`0` 是无伤害。

插件会持续检查在线玩家物品，并在容器区块加载、物品拾取、发射、铁砧/锻造台产出和背包操作时检查物品；因此未加载区块或离线玩家的数据会在下次加载时处理。

## 反作弊

检查项目包括攻击距离、Killaura、CrystalAura、普通飞行和无烟花鞘翅平飞。违规证据会写到 `plugins/smp-booster/anticheat.log`。

- `/acwhitelist add <玩家>`：加入白名单。
- `/acwhitelist remove <玩家>`：移出白名单。
- `/acwhitelist list`：查看白名单。
- `/silentac on`：只记录，并向在线 OP 报告达到阈值的玩家。
- `/silentac off`：达到确认阈值后按配置封禁。
- `/silentac status`：查看当前模式。

以上指令仅限 OP。封禁消息和时长可在 `anti-cheat.ban` 修改；时长支持 `30m`、`12h`、`7d`、`2w` 和 `permanent`。

## 村民无限补货

- `/infvillager`：切换全服村民无限补货。
- `/infvillager on|off|toggle|status`：明确开启、关闭、切换或查看状态。

仅限 OP。开启后交易不会增加使用次数，已售罄的已加载村民也会立即恢复；未加载区块中的村民会在区块下次加载时恢复。开关状态保存在 `features.infinite-villager-trades`。

## Paper 原版漏洞修复开关

- `/paperfix`：切换 Paper 修复。
- `/paperfix on|off|toggle|status`：明确开启、关闭、切换或查看状态。

`on` 表示采用 Paper 的安全行为；`off` 表示允许对应的原版漏洞。目前统一控制 attribute swap（包括长矛）、绊线钩复制、活塞复制、末地传送复制、无头活塞、不可破坏方块漏洞和无敌末地水晶。指令会同时更新运行时配置及 Paper 配置文件，并在首次修改时创建 `.smp-booster.bak` 备份。

注意：关闭这些修复可能造成物品复制、破坏基岩/末地传送门等不可逆影响。Paper 官方把其中多项标为“不受支持的设置”。部分依赖特定方块更新顺序的老式刷钩机，即使关闭修复也不保证在所有设计上可用。

## 语言与反作弊告警

- `/language zh`：全服插件提示切换为中文。
- `/language en`：全服插件提示切换为英文。
- `/language ja`：切换为日语。
- `/language de`：切换为德语。
- `/language es`：切换为西班牙语。

语言指令仅限 OP，选择会保存到 `messages.language`。静默反作弊确认达到告警阈值时，所有在线 OP 会收到整条加粗的聊天提示和一声提示音。

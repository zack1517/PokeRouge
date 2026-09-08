# UI 模块接口与界面设计文档（接口文档_UI模块）

> 版本：v0.1.3（草稿） · 日期：2026-09-08 · 模块：JavaFX UI 设计（FXML / Controller / CSS / 界面）
> 角色：UI 负责人 · 状态：**待组长评审**，评审通过前不进入编码
> 依据：《需求文档》§7 界面与交互需求、《测试用例草稿》§D、《接口文档_战斗服务.md》v1.0、
> **本阶段六系统任务分工**（游戏流程系统 / 存档系统 / 宝可梦系统 / 战斗系统 / 肉鸽系统 / JavaFX UI；其中游戏流程与存档由同一人负责）
> 版本说明：v0.1.3 将「游戏流程&存档」拆为游戏流程系统与存档系统（同一人负责，共六系统），并同步修订边界表、接口归属与内部设计措辞；
> 文中「[待确认]」项为需与组长 / 对应系统负责人确认后方可定稿的内容。

## 1. 概述

### 1.1 模块定位（六系统版）

UI 模块是**纯视图层**，负责全部 JavaFX 界面的实现：**FXML 布局、Controller、CSS 样式**。
不承载任何业务规则与流程决策，也不持有导航状态机。

UI 只做三件事：

1. **查询展示**：读取各系统 model / 服务接口的只读状态并渲染；
2. **命令转发**：把用户操作转成对所属系统服务接口的一次调用；
3. **提供页面并响应调度**：向"游戏流程系统"暴露页面注册表，按 `ScreenId`
   提供可直达的场景；**何时切换、切换去哪个页面由流程系统决定，UI 不自行跳转**。

### 1.2 六系统边界与本模块的协作

| 系统 | 职责（组长给定） | 与本模块的关系 |
| --- | --- | --- |
| 游戏流程系统 | 菜单、游戏流程、**场景切换** | **调度方**：决定显示哪个页面、传页面参数 |
| 存档系统 | 保存 / 读取当前游戏状态 | **状态提供方**：页面状态对象定义与序列化（读档直达任意页面）；与游戏流程系统**同一人负责** |
| 宝可梦系统 | 宝可梦、技能、队伍 | **数据提供方**：选队/图鉴/面板/队伍展示的数据与扩展（性格等 [待确认]） |
| 战斗系统 | 一场战斗的规则 | **服务提供方**：`BattleService`（v1.0 已交付），战斗页的结算来源 |
| 肉鸽系统 | 战斗后的奖励、强化、下一轮 | **服务提供方**：节点/奖励/商店等页面数据与交互结果 |
| **JavaFX UI（本模块）** | FXML、Controller、CSS、界面 | 消费宝可梦/战斗/肉鸽系统；被游戏流程系统调度；依赖存档系统的页面状态定义 |

调用方向：

```
游戏流程系统 ──调度（ScreenId + 页面参数 / 状态）──► JavaFX UI
存档系统 ────── 页面状态定义 / 读档恢复 ────────► JavaFX UI
     ▲                                                   │ 查询 / 命令
     │                                                   ▼
     └──────────── 场景结果 / 行动请求 ──────────  宝可梦 / 战斗 / 肉鸽 系统
```

- UI 不直接依赖流程/存档系统实现，只依赖**调度契约**（§6.2）与**页面状态定义**（§6.3）；
  流程系统不依赖具体页面类，只依赖 `ScreenId → 场景工厂` 注册表；
- 界面数量与内容的最终解释权：页面清单来自《需求文档》§7，**页面驱动关系由组长按
  六系统边界确认**（见 §9）。

### 1.3 技术选型与界面实现约束

| 项 | 约定 |
| --- | --- |
| 布局 | **FXML**（`resources/fxml/*.fxml`），页面控件经 `fx:id` 暴露给 Controller |
| 控制器 | 每页一个 Controller（`fx:controller` 绑定），实现本模块定义的页面回调接口 |
| 样式 | 统一 **CSS**（`resources/css/app.css`），**禁止散落内联样式** |
| 运行 | JDK 17 + JavaFX 17.0.20，classpath 方式（`mvn javafx:run`），窗口参数收口在 `AppConfig` |
| 资源 | 任何资源（fxml/css/图片）引用必须启动即验证存在，fail-fast（Day01 images/logo.png 教训） |

### 1.4 与 feature/battle 分支既有界面的关系

现状：战斗系统（原开发 A）的 `feature/battle` 已交付 `BattleView/BattleController`
（**纯 Java 布局 + 内联样式**）与改造版 `MainView`（主菜单演示）。

[待确认] 处理约定（需组长认可）：
- 本模块正式界面一律 **FXML 化**；A 的纯代码界面视作**交互行为与布局的参考实现**，
  由其提供的"场景主菜单 → 战斗 → 结算"演示闭环作为联调对照物；
- feature/battle 合入 dev 后，正式版页面由本模块实现；是否同步移除 A 的纯代码页面
  由组长决定（建议保留至 FXML 版战斗页替换验收通过）。

## 2. 模块结构

```
src/main/resources/                         （FXML 与样式随代码走 classpath）
├── fxml/
│   ├── mainMenu.fxml                       主菜单页（游戏流程系统驱动，含存档入口）
│   ├── teamSelect.fxml                     初始选队页
│   ├── routeMap.fxml                       路线地图页
│   ├── battle.fxml                         战斗页
│   ├── shop.fxml                           商店页
│   ├── reward.fxml                         战后奖励/强化页（肉鸽系统）[待确认]
│   ├── mergeNature.fxml                    同类合并/性格选择弹窗（宝可梦系统）[待确认]
│   └── result.fxml                         结算/结局页
├── css/
│   └── app.css                             全局主题（HP 条/属性标签/按钮/面板）
└── data/                                   [沿用 battle 交付的 CSV，新增见 §7]

src/main/java/org/example/
├── App.java                                [改造] start()：装配页面注册表，由游戏流程系统驱动首屏
├── config/AppConfig.java                   [沿用] 窗口/文案常量
├── ui/                                     [本模块对外契约 · 新增建议]
│   ├── ScreenId.java                       [待确认] 页面标识枚举（§6.2）
│   ├── ScreenFactory.java                  [待确认] ScreenId → Scene/Parent 工厂注册表（§6.2）
│   └── PageController.java                 [待确认] 页面控制器基约定：setState(状态)/回调接口（§6.3）
├── controller/                             每页一 Controller（与 fxml 同名对应）
│   ├── MainMenuController / TeamSelectController / RouteMapController
│   ├── BattleController / ShopController / RewardController
│   ├── NatureSelectController / ResultController
└── view/                                   [纯代码时代的布局类逐步退役，仅保留共用组件]
    └── component/                          HpBar、TypeBadge 等可复用小部件（FXML 内以自定义控件/嵌套布局实现）
```

### 2.1 FXML 与 Controller 命名对应约定

| 约定 | 说明 |
| --- | --- |
| 文件对应 | `fxml/<page>.fxml` ↔ `controller/<Page>Controller.java`（fx:controller 指向后者） |
| 控件注入 | Controller 内 `@FXML` 注入 `fx:id` 控件，不自行 `lookup` |
| 页面回调 | Controller 构造时不做事，实现「页面回调接口」（如 `BattlePage.Actions`）由调度方注入（§6.3） |
| 弹窗 | 复用同一 `fxml` 页面 + 模态 Stage 或覆盖层展示（性格选择等），交互回执经回调返回 |

## 3. 页面清单与场景流转

### 3.1 场景流转（页面关系图）

依据《需求文档》§六典型节奏与 §7 界面清单。**本图仅描述页面关系与可达路径；
实际跳转时机由"游戏流程系统"判定（AP 耗尽、战斗结局等）；读档直达场景由"存档系统"发起**：

```
                ┌──────────────┐
                │ mainMenu 主菜单│ ← 启动 / 一局结束 / 读档入口
                └──────┬───────┘
                       ▼
                ┌──────────────┐
                │ teamSelect 选队│ 多选初始宝可梦，确定后开始新 Run
                └──────┬───────┘
                       ▼
   ┌─────────► ┌──────────────┐
   │            │ routeMap 地图 │ 段节点链 + AP + 金币/队伍摘要
   │            └──┬───────┬───┘
   │    AP耗尽/终点 │       │ 进入节点（路人/野外/道馆/四天王/Boss…）
   │               ▼       ▼
   │        ┌──────────┐  ├────────► battle 战斗页（含捕捉交互）
   │        │ result    │  ├────────► shop / reward / hospital… [按肉鸽节点]
   │        │ 结算/结局  │  └────────► mergeNature 弹窗（捕获合并/性格）
   │        └────┬─────┘
   └─────────────┘ 结束 → 回 mainMenu（入口由游戏流程系统控制，落盘/恢复归存档系统）
```

- 战斗页结束后**是否回地图**、失败扣金币还是结束 Run、是否触发奖励页——全部由
  流程系统按结果状态（§4.1 `BattleService.Status`）决策并调度下一页面；
- **存档**：存档系统可保存/恢复任意时刻状态 → 任意页面都可能被直达恢复（§6.3）。

### 3.2 页面总表（对照需求 §7、用例 §D、六系统驱动关系）

| 页面 | 主要功能 | 驱动/数据系统 | 对应用例 |
| --- | --- | --- | --- |
| 主菜单 | 新 Run / 读档 / 退出（退出确认沿用现有 MainController 文案） | 游戏流程系统（读档动作归存档系统） | — |
| 初始选队 | 多选初始宝可梦、数量上限校验、确定出发 | 游戏流程系统 + 宝可梦系统(初始池 I-01) | UI-01 |
| 路线地图 | 段节点链、AP 显示、节点可选性（判定归服务侧）、金币/队伍摘要 | 游戏流程系统（调度 I-02a）+ 肉鸽系统（I-02b） | UI-02 |
| 战斗 | 出招/技能/道具/换宠/逃跑、HP·EXP 条、属性面板、日志 | 战斗系统（BattleService） | UI-03 |
| 战后奖励/强化 | 战斗胜利后奖励选择（三选一/商店形态 [待确认]） | 肉鸽系统（I-03） | FLOW-05/06 相关 |
| 医院/回复节点 | 治疗费用与确认（濒死恢复） | 肉鸽或流程 [待确认]（I-05） | COMBAT-05 / FLOW-07 |
| 合并/性格选择 | 同类合并（等级取高）提示、性格选择 | 宝可梦系统（I-06，范围 [待确认]） | GROW-06/07 / UI-04 |
| 结算 | 失败原因/通关/侵略战结局 + 存档提示 | 游戏流程系统（I-07）+ 存档系统（I-09） | FLOW-10~17 |
| 图鉴 | 收集展示 | 宝可梦系统 | [待确认] 是否首版 |

### 3.3 战斗页（FXML 版要求）

行为与布局参照 battle 分支的 `BattleView + BattleController` 参考实现，正式版按
FXML 重写，要求：

- 布局：上=双方精灵信息面板（名称/属性/Lv/HP 条/EXP 条），中=战斗日志，下=行动区
  （主菜单：技能/背包/精灵/逃跑；子菜单：技能列表、背包列表、队伍列表）；
- 按钮可用性规则（沿用参考实现）：PP 耗尽禁用、满血回复道具禁用、倒下/当前出战
  精灵禁用；非 `ONGOING` 时清空行动区显示结果与"返回"；
- 日志展示只读、行动后全量覆盖（与参考实现一致），胜负/升级/学招/进化等文案
  一律来自日志行透传，UI 不自行生成业务文案。

### 3.4 存档对页面的约束（新增）

读档可能直达**任意场景**（如直接从地图中段恢复、从商店恢复）：

- 每个页面 Controller 必须**状态驱动渲染**：由调度方在展示前传入完整页面状态
  （队伍、金币、AP、货架、战斗现场等），Controller 依据状态渲染，**禁止依赖
  "从上一页顺序进入"的隐式前提**；
- 页面自身不留跨页历史（返回路径由流程系统维护，UI 只响应调度）。

## 4. UI 依赖的外部契约（已存在，可直接引用）

以下接口/类型已由 feature/battle（战斗系统）交付，版本 v1.0；契约引用以此为准。

### 4.1 战斗服务（引用《接口文档_战斗服务.md》§3）

| 需求 | 使用方页面 | 说明 |
| --- | --- | --- |
| `BattleService.Status` 枚举 | 战斗页、结算页 | 战斗结束分流：`PLAYER_WIN / PLAYER_LOSE / FLED / CAUGHT / ONGOING` |
| `useMove / useItem / tryRun / switchActive` | 战斗页 | 四个行动入口；返回 `List<String>`（本回合新日志）；非 ONGOING 调用抛 `IllegalStateException`（UI 先判 `isOngoing()`） |
| `playerActive() / getWild() / getPlayer()` | 战斗页 | 面板与队伍菜单渲染（引擎自动换宠后重新读取，可能为 null，需空态） |
| `getLog()` | 战斗页 | 完整日志只读，行动后全量覆盖展示 |
| `getBag()` | 战斗页 | 配合 `Bag.availableStacks()` 渲染背包菜单 |

### 4.2 工厂与遭遇环境（引用《接口文档_战斗服务.md》§4）

| 方法 | 归属 | 说明 |
| --- | --- | --- |
| `BattleServices.newBattle(Player, Pokemon[, Random])` | 战斗页创建入口（由流程系统在调度战斗页前调用并传入现场，或经页面回调创建 [待确认]） | 玩家无存活出战精灵抛异常，创建前校验 `hasHealthyPokemon()` |
| `BattleServices.wildLevelAround / randomWild` | 肉鸽系统 | 遭遇生成属流程/肉鸽侧，UI 不直接调用；`Optional` 空值需空态提示 |

### 4.3 model 只读查询（UI 展示所需字段，归宝可梦系统维护）

| 对象 | UI 用到的查询 |
| --- | --- |
| `Pokemon` | `getName / getLevel / getSpecies / getCurrentHp / getMaxHp / getExp / expToNextLevel / isFainted / getMoveSlots / getMoves / getStats / hasType` |
| `Species` | `getId / getName / getTypes / getBaseStats / canEvolveAt(level)` |
| `Stats` | `getHp / getAttack / getDefense / getSpAttack / getSpDefense / getSpeed` |
| `ElementType` | `getDisplayName()`；`parse(name)` 供数据加载 |
| `Player` | `getName / getParty / getActive / getBag / hasHealthyPokemon / healParty`（医院页） |
| `Bag / ItemStack` | `availableStacks() / countOf / getAll`；`getItem() / getCount()` |
| `Item` | `getId / getName / getCategory / getEffect / isAlwaysCatch`（`ItemCategory.HEAL / POKE_BALL`） |
| `MoveSlot / Move` | `getMove / getPp / exhausted`；`getId / getName / getType / getCategory / getPower / getMaxPp` |

> 展示所需中文名均有现成 getter，**不要求逻辑层为展示拼装字符串**；格式拼接由 UI 负责。
> 若宝可梦系统扩展 model（金币、性格、努力值等），保持现有 getter 风格并**同步更新本文档
> 或单独出接口文档**（契约变更通知约束，见 §9）。

### 4.4 数据注册表（`GameData` 单例）

`GameData.instance()` 提供：`allSpecies / allMoves / allItems / species(id) /
move(id) / item(id) / wildPool / createPokemon(speciesId, level)`。
[待确认] 该注册表本阶段归宝可梦系统维护还是另立数据系统；初始可选池见 §5 I-01。

### 4.5 应用配置（`AppConfig`）

`APP_TITLE = "PokeRouge"`、窗口 640×480、退出确认文案已定；新增窗口/文案常量一律
进 `AppConfig`（或 `config/UiText`），禁止内联散落。

## 5. 对外部系统的接口缺口（[待确认]，需组长分派承诺）

以下为 UI 页面必需、当前工程中不存在的契约。各系统按 BattleService 模式交付接口
文档 + 交付包；未承诺前 UI 用桩数据开发，不阻塞。

| 编号 | UI 侧需要的契约 | 归属系统 | 备注 |
| --- | --- | --- | --- |
| I-01 | 初始宝可梦可选池：`GameData.initialPool()`（3~6 只初始种族，数值 [待确认]） | 宝可梦系统 | 选队页 UI-01 前置 |
| I-02a | Run 状态机与场景调度接口：`startRun(party)` + **场景调度**（§6.2 契约） | 游戏流程系统 | 地图/页面流转 UI-02 前置；**UI 与流程系统的核心对齐点** |
| I-02c | 存档服务与页面状态：`save() / load()`、可序列化页面状态对象定义（§6.3） | 存档系统 | 读档直达任意页面的前提；状态类型需与 UI 共同定稿 |
| I-02b | 节点与路线数据：当前段节点链、`apRemaining/apMax`、`canEnter(nodeId)`（可选性判定归服务侧） | 肉鸽系统 | 地图 UI-02 前置 |
| I-03 | 奖励/商店服务：战后奖励选项、货架（成长规则 [待确认]）、`buy(itemId)`、余额校验 | 肉鸽系统 | 商店/奖励页 FLOW-05/06 |
| I-04 | 金币：`Player.getMoney()/spend/earn` | 宝可梦系统 | 地图摘要、商店/医院扣费、路人奖惩展示均需要 |
| I-05 | 医院/回复节点服务：费用规则与执行 | 肉鸽或流程 [待确认] | model 已有 `Player.healParty()` |
| I-06 | 同类合并 + 性格选择：`Nature` 模型与加成表、合并服务、可选性格列表 | 宝可梦系统 | **范围 [待确认]**（六系统描述未含性格/努力值，若本轮裁剪由组长明示）；UI-04 前置 |
| I-07 | 结算信息：结局枚举（通关/侵略战/战败） | 游戏流程系统 | 结算页展示，不自行判定 |
| I-09 | 存档提示与反馈契约（保存成功/读档完成的 UI 提示时机与内容） | 存档系统 | [待确认] 是否需要 UI 提示位；与主菜单/结算页存档入口配套 |
| I-08 | 节点事件回执：进入节点后"去哪个页面 + 页面参数"的统一分发对象 | 肉鸽系统 | 地图→子页面的跳转数据载体（配合 I-02a 调度） |

> 承诺方式：组长在评审时分派；各系统以接口文档/交付包回复（参照 battle 交付模式）。

## 6. UI 内部设计约定

### 6.1 FXML 页面实现模式

```
fxml 布局（控件 + fx:id + fx:controller）
        │ @FXML 注入 / 事件绑定
        ▼
Controller ──查询──► 各系统服务接口（BattleService / 肉鸽 / 宝可梦…）
   │        ──命令──► 同上（行动后由本页 render() 刷新，不自行跳转）
   │
   └── 页面回调（如 onExit/onFinished）──► 由调度方注入 → 流程系统
```

- Controller 的事件处理方法中**只做"调用服务 + 刷新本页"**；
- 页面状态展示收敛为 `render(state)` 方法，任何入口（新建/读档）都走同一渲染路径；
- CSS 类名统一前缀（如 `.pr-*`），视觉细节（配色/圆角/字体）全部收敛 `app.css`。

### 6.2 场景调度契约（UI 对"游戏流程系统"的对外接口）[待确认草案]

UI 模块对外只暴露注册表与工厂，由流程系统驱动：

```java
// ui/ScreenId.java —— 页面标识（与 §3.2 页面一一对应，新增页面需同步）
public enum ScreenId {
    MAIN_MENU, TEAM_SELECT, ROUTE_MAP, BATTLE,
    SHOP, REWARD, NATURE_SELECT, RESULT
}

// ui/ScreenFactory.java —— 页面工厂：注册/创建（实现由 UI 模块内部各 Controller 承担）
public interface ScreenFactory {
    Parent create(ScreenId id, Object pageState);   // pageState = 调度方传入的页面状态
    void register(ScreenId id, Supplier<PageController> supplier);
}

// 游戏流程系统侧的用法（示意，由流程系统实现方确认）：
// Parent p = screenFactory.create(ScreenId.BATTLE, new BattlePageState(engine));
// stage.getScene().setRoot(p);    // 或 stage.setScene(...)
```

- `ScreenId` / `ScreenFactory` 的最终形态（含 `pageState` 类型体系）**由 UI 与游戏流程系统
  负责人共同定稿**；评审时流程与存档双方均需在场（状态类型与存档序列化耦合）；
- UI 不持有 Stage 的切换逻辑，只保证"给定状态，产出已渲染的 Parent/Scene"。

### 6.3 状态驱动渲染（存档约束落地）

- 每个 Controller 提供 `setState(Object state)`（或构造时传入）+ `render()`；
- 页面状态对象（如 `BattlePageState(engine)`、`MapPageState(runContext)`）定义归属：
  [待确认] 由存档系统定义（负责序列化还原）还是各页面自定——建议存档系统统一定义
  便于 `save/load` 序列化；游戏流程系统调度时按类型下发状态；
- **反例约束**：Controller 不得在 `initialize()` 里拉取"全局当前 Run"之外的隐式数据
  （不得假设自己是从主菜单点进来的）。

### 6.4 刷新策略

- 战斗页：行动后全量刷新（面板/日志/按钮区），与 battle 参考实现一致；自动换宠后
  `playerActive()` 可能为 null，需空态防御；
- 非战斗页：进入时构建 + 操作后局部刷新（金币/AP/货架文本）；
- 战斗过程更新（HP 动画等）若需要定时器，一律 `Platform.runLater` 安全提交 [待确认
  是否需要动画，首版可静态跳变]。

### 6.5 空态与异常防御

- `Optional` 空遭遇、队伍全倒、背包空等必须有可见空态（参照参考实现「背包空空如也」）；
- 服务抛 `IllegalStateException`（如战斗已结束仍行动）属 UI 缺陷：调用前以状态禁用
  入口，**禁止 try-catch 兜业务流**；
- fxml/css/图片加载失败启动即暴露（fail-fast），运行时不做静默降级。

## 7. 数据驱动与资源约定

| 项 | 约定 |
| --- | --- |
| 已有 CSV（battle 交付） | `species.csv / moves.csv / items.csv`（`#` 注释头格式）；归宝可梦/战斗系统维护 |
| 新增数据 [待确认] | 节点/路线、奖励池、商店货架、性格表（`nature.csv` 等），由归属系统产出后并入 `resources/data/`，格式沿用 `#` 注释风格 |
| 界面资源 | `resources/fxml/*.fxml`、`resources/css/app.css`；中文统一「Microsoft YaHei」防乱码 |
| 文案 | 静态界面文案收 `AppConfig` / `config/UiText`（沿用现有 EXIT_CONFIRM_* 先例） |

## 8. 验收映射（用例 → 页面）

| 用例 | 验收页面 | 验收要点 | 优先级 |
| --- | --- | --- | --- |
| UI-01 初始选队 | teamSelect | 可多选搭配、数量限制正确、确定后交流程系统开始 Run | P0 |
| UI-02 路线地图 | routeMap | 节点与 AP 展示正确；可选性随 AP 联动（判定来自服务侧 `canEnter`，UI 只渲染） | P0 |
| UI-03 战斗界面 | battle | 四类行动可用；HP/EXP/属性显示与 `BattleService` 结算一致 | P0 |
| UI-04 性格选择 | mergeNature | 交互反馈即时正确；属性加成生效可见（依赖 I-06） | P1 |
| CFG-01 数据驱动 | 各数据页面 | 修改数据文件后重载生效，无需改代码 | P1 |

## 9. 待确认清单（评审沟通要点）

### A. 六系统边界（新增，最高优先）
1. **[功能归属矩阵]** 组长给"需求功能 → 六系统"对应表；重点：AP/路线/节点归流程还是
   肉鸽？医院归谁？性格/努力值/抓捕合并**本轮是否做**、归宝可梦还是肉鸽？
2. **[场景调度与页面状态]** UI 与游戏流程系统的调度契约定稿（§6.2）；页面状态对象体系
   与存档系统共同定稿（§6.3）——两者同一人负责，建议评审时一同在场；
3. **[主菜单归属]** 游戏流程系统职责含"菜单"：逻辑与场景切换归它无疑；菜单**界面**是否
   仍由本模块实现（本文档按"UI 实现 mainMenu.fxml、游戏流程系统驱动"处理）；存档入口
   按钮的交互回执归存档系统；
4. **[battle 界面]** FXML 全面替换 A 的纯代码界面的节奏与移交点（§1.4）。

### B. 原待确认（更新）
5. **[范围]** 图鉴是否首版；选队/队伍数量上限（当前 engine 未强制）；
6. **[承诺]** §5 I-01~I-08 责任人（各系统）+ 交付时间；
7. **[文案/数值]** 医院费用、商店成长、奖励池等展示所需数值（需求 §9 待定项）；结算
   文案由流程给枚举、UI 本地映射；
8. **[样式基准]** 组长/客户对界面原型的最终草图（需求 §9-10 待定项），UI 以 FXML/CSS
   按其还原。

## 版本记录

| 版本 | 日期 | 内容 | 提交人 |
| --- | --- | --- | --- |
| v0.1.1 | 2026-09-08 | 初稿：页面清单、场景流转、外部契约引用、接口缺口、内部设计、验收映射（按 README 六人分工） | [待确认] |
| v0.1.2 | 2026-09-08 | 按五系统分工重写：场景调度归流程&存档（§1/§6.2）、界面 FXML 化（§1.3/§2/§6.1）、缺口按系统重分配（§5）、新增存档约束-状态驱动渲染（§3.4/§6.3）、待确认清单分 A/B 组 | [待确认：UI 负责人] |
| v0.1.3 | 2026-09-08 | 系统拆分："游戏流程&存档"拆为游戏流程系统与存档系统（同一人负责，共六系统）；修订 §1.2 边界表/§3.1-3.2/§5 归属（新增 I-02c、I-09）/§6.2-6.3/§9 A 组 | [待确认：UI 负责人] |

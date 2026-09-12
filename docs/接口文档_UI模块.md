# UI 模块接口与界面设计文档（接口文档_UI模块）

> 版本：v0.1.21（草稿） · 日期：2026-09-12 · 模块：JavaFX UI 设计（FXML / Controller / CSS / 界面）
> 角色：UI 负责人 · 状态：**待组长评审**，评审通过前不进入编码
> 依据：《需求文档》§7 界面与交互需求、《测试用例草稿》§D、《接口文档_战斗服务.md》v1.14、
> **本阶段六系统任务分工**（游戏流程系统 / 存档系统 / 宝可梦系统 / 战斗系统 / 肉鸽系统 / JavaFX UI；其中游戏流程与存档由同一人负责）
> 版本说明：v0.1.3 将「游戏流程&存档」拆为游戏流程系统与存档系统（同一人负责，共六系统），并同步修订边界表、接口归属与内部设计措辞；
> **v0.1.4 同步战斗服务 v1.4 的训练师轮战契约：§3.3 增训练师轮战界面要求，§4.1/§4.2 敌方渲染改用 `foeActive()` 并补 `getTrainer()`、`newTrainerBattle`，§4.3 补 `Trainer` 查询**；
> **v0.1.5 同步战斗服务 v1.5 的异常状态契约：战斗页显示异常摘要与实际速度，道具菜单显示解除适用范围**；
> **v0.1.6 同步战斗服务 v1.6 的数据端口：战斗模块只消费数据，技能/种族/野生池由外部数据模块经 `BattleDataPort` 注入，UI 侧只在组装入口注入、渲染无变化**；
> **v0.1.7 同步战斗服务 v1.7 的职责收敛：遭遇生成（等级浮动 / 随机挑种族）移出战斗模块，改由组装侧 `WildEncounter` 提供，主界面的随机遭遇仍由 `MainController` 调用、渲染无变化**；
> **v0.1.8 同步战斗服务 v1.8 的道具目标选择：背包菜单改为「选道具 → 选目标精灵」两步，可对队伍任意精灵（含替补）使用回复 / 解除道具，精灵球跳过目标步骤；道具可用性判定改为「队伍中是否存在合法目标」**；
> **v0.1.9 同步战斗服务 v1.9 的成长判定外移：经验 / 升级 / 学招 / 进化由外部成长模块结算，战斗页只渲染日志、学习抉择仍走 `pendingLearnChoices()` / `decideLearn(int)` 弹窗，UI 侧仅组装入口多注入一个成长端口，渲染无变化**；
> **v0.1.14 常驻节点可重复进入：路线地图页对已走过的一次性节点仍置灰，对常驻节点（路人 / 野外精灵 / 医院）改为可点并标注「已走过 N 次 · 可再次进入」，卡片消耗改取 `Option#apCostForNextEntry()`；「挑战道馆」兜底入口的触发条件不变**；
> **v0.1.15 节点列表刷新 + 道馆胜利推进修复：每走完一个路线节点由 `MainController#finishNodeStep(boolean)` 调 `GameSession#refreshRogueRoute()` 整批重抽本段节点（AP / 段号 / 金币不变，故界面上的「已走过」标记在新一批节点里不再出现）；必然节点（道馆 / 四天王 / 冠军 / 首领侵略战）胜利改走 `GameSession#resolveRogueMandatoryVictory()`——此前该入口漏接导致打赢道馆后阶段停在 `GYM` 反复重开道馆战，现已在控制器接线**；
> **v0.1.16 界面流转收敛到启动页：场景图新增启动页节点，主菜单由「进入层内事件 / 保存游戏 / 退出游戏」改为「进入层内事件 / 保存游戏 / 读取存档 / 返回主界面」，一轮远征结束（通关 / 战败 / 队伍全灭）改为回启动页开新游戏，「退出程序」只保留窗口关闭二次确认**；
> **v0.1.17 同步战斗服务 v1.11 的逐只即时结算：经验改为「每击倒一只对手即时发放」，故战斗页日志可在**战斗进行中**出现升级 / 学招文本，`pendingLearnChoices()` 也可能在战斗中即非空——战斗页渲染顺序调整为「挂起抉择优先于战斗菜单、结局展示最后」，渲染字段无变化**；
> **v0.1.18 同步战斗服务 v1.14 己方倒下后的玩家补位：战斗页新增「必须选出替补」面板（`showReplacementMenu`，不可返回），`isOngoing()` 为真但 `isAwaitingReplacement()` 为真时必须先 `chooseReplacement(int)` 才能继续；`playerActive()` 不再出现 null（补位前为刚倒下的那只），§4.1 / §6.4 / §6.6 相应修订**；
> **v0.1.19 商店上架全部可携带装备（77 件）：§3.6 `ShopView` 改为「消耗品 / 装备」两分区抽签，装备池全量取自 `GameData#allEquipment()`、按 `HeldItemEffect` 定解锁段位与基础价、已拥有不再上架**；
> **v0.1.20 新增道具图鉴页：§3.2 页面总表增「道具图鉴」行并把主菜单行补上入口，新增 §3.7 `ItemDexView`（93 件全量目录 + 四档筛选 + 已拥有 / 穿戴标注 + 装备直接穿脱）；目录口径复用 `ShopStock#catalog()`，与货架的定价 / 解锁段位同源**；
> **v0.1.21 取消商店分段解锁：§3.6 `ShopView` 段改写 —— 消耗品与装备不再按段解锁，第 1 段起全部可能上架，「越往后越多」只由货架格位（`RouteConfig#shopStockSize`）与售价通胀体现；大师球改为剧情专属道具（不进商店，击败火箭队首领必得），§3.7 图鉴把「第 N 段起可在商店购买」改为「商店出售 / 不售卖」标识**；
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
| 战斗系统 | 一场战斗的规则 | **服务提供方**：`BattleService`（v1.11 已交付），战斗页的结算来源；**数据由外部模块经 `BattleDataPort` 注入、遭遇生成由组装侧 `WildEncounter` 提供、成长判定由外部成长模块经 `BattleGrowthPort` 逐只即时申报**（UI 不感知判定过程，只渲染日志与学习抉择） |
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
                │ title 启动页   │ ← 程序启动 / 返回主界面 / 一轮结束
                └──────┬───────┘   「开始游戏」新档、「继续游戏」读档
                       ▼
                ┌──────────────┐
                │ mainMenu 主菜单│ ← 新建档完成 / 读档完成
                └──────┬───────┘   「保存游戏」「读取存档」「返回主界面」
                       ├────────► itemDex 道具图鉴（93 件全量 + 装备穿脱）
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
   └─────────────┘ 结束 → 回 title 启动页（在那里选「开始游戏」开新档或「继续游戏」读档）
```

- 战斗页结束后**是否回地图**、失败扣金币还是结束 Run、是否触发奖励页——全部由
  流程系统按结果状态（§4.1 `BattleService.Status`）决策并调度下一页面；
- **存档**：存档系统可保存/恢复任意时刻状态 → 任意页面都可能被直达恢复（§6.3）；
- **退出程序**只能由窗口关闭按钮的二次确认（`AppConfig.EXIT_CONFIRM_*`）触发；
  界面上的「返回主界面」只回启动页，不结束进程。

### 3.2 页面总表（对照需求 §7、用例 §D、六系统驱动关系）

| 页面 | 主要功能 | 驱动/数据系统 | 对应用例 |
| --- | --- | --- | --- |
| 启动页 | 开始游戏（新档）/ 继续游戏（读档，无档置灰）/ 成就 / 设置 | 游戏流程系统 + 存档系统 | SAVE-01~03 |
| 主菜单 | 进入层内事件 / 道具图鉴 / 保存游戏 / 读取存档 / 返回主界面（不退出程序） | 游戏流程系统（读档动作归存档系统） | — |
| 存档位选择 | 4 个档位的摘要与可用状态，选定后新游戏 / 读档 / 保存 | 存档系统（详见《接口文档_存档系统.md》） | SAVE-01~03 |
| 初始选队 | 多选初始宝可梦、数量上限校验、确定出发 | 游戏流程系统 + 宝可梦系统(初始池 I-01) | UI-01 |
| 路线地图 | 段节点链、AP 显示、节点可选性（判定归服务侧）、金币/队伍摘要 | 游戏流程系统（调度 I-02a）+ 肉鸽系统（I-02b） | UI-02 |
| 战斗 | 出招/技能/道具/换宠/逃跑、HP·EXP 条、属性面板、日志 | 战斗系统（BattleService） | UI-03 |
| 战后奖励/强化 | 战斗胜利后奖励选择（三选一/商店形态 [待确认]） | 肉鸽系统（I-03） | FLOW-05/06 相关 |
| 医院/回复节点 | 治疗费用与确认（濒死恢复） | 肉鸽或流程 [待确认]（I-05） | COMBAT-05 / FLOW-07 |
| 商店 | 展示段内货架、金币余额、买不起的商品禁用、购买后即时扣款 | 肉鸽系统（货架由存档/肉鸽状态提供） | 见 §3.6 |
| 合并/性格选择 | 同类合并（等级取高）提示、性格选择 | 宝可梦系统（I-06，范围 [待确认]） | GROW-06/07 / UI-04 |
| 结算 | 失败原因/通关/侵略战结局 + 存档提示 | 游戏流程系统（I-07）+ 存档系统（I-09） | FLOW-10~17 |
| 图鉴 | 收集展示（宝可梦图鉴：全量列出 + 已捕获标注，入口在启动页） | 宝可梦系统 | [待确认] 是否首版 |
| 道具图鉴（参考实现已落地） | 主菜单「道具图鉴」进入：93 件全量目录（16 道具 + 77 装备）、四档筛选、已拥有/穿戴标注、装备直接穿脱 | 肉鸽系统（`ShopStock.catalog()` 定价与「在售 / 不售卖」口径）+ 宝可梦系统（装备库 / 背包） | 见 §3.7 |
| 精灵详情页（参考实现已落地） | 主菜单点精灵名进入：立绘/信息/数值/技能 + 装备槽（穿戴/脱下） | 宝可梦系统 + 肉鸽系统（装备库） | UI-03 相关 |

### 3.3 战斗页（FXML 版要求）

行为与布局参照 battle 分支的 `BattleView + BattleController` 参考实现，正式版按
FXML 重写，要求：

- 布局：上=双方精灵信息面板（名称/属性/Lv/HP 条/EXP 条），中=战斗日志，下=行动区
  （主菜单：技能/背包/精灵/逃跑；子菜单：技能列表、背包列表、队伍列表）；
- 按钮可用性规则（沿用参考实现）：PP 耗尽禁用、倒下/当前出战
  精灵禁用；非 `ONGOING` 时清空行动区显示结果与"返回"；
- 日志展示只读、行动后全量覆盖（与参考实现一致），胜负/升级/学招/进化等文案
  一律来自日志行透传，UI 不自行生成业务文案；
- 训练师轮战（v1.4）：敌方为一整支队伍，敌方面板取 `foeActive()`（对方换宠后自动跟随），
  可用 `getTrainer()` 显示对手名与对方剩余精灵；逃跑与投球会被引擎拒绝（仅提示日志、
  不消耗回合），按钮可置灰；仅当对方整队精灵全部倒下才展示胜利。
- 异常状态展示（v0.1.5，参考实现已落地）：双方精灵卡片在 HP 条下方显示**状态徽章**
  （主要异常/混乱/倒下，橙底 `#ffe6c7` + 深字 `#a35200`，无异常时不占位）；
  技能列表的说明文字叠加异常提示（100% 为"使目标陷入X状态"，否则"可能使目标陷入X状态（n%）"）；
  队伍列表与队伍菜单显示异常摘要，并给出计异常倍率后的**实际速度**（`effectiveSpeed()`）；
- 道具可用性（v0.1.5）：解除类道具（`ItemCategory.CURE`）在当前出战精灵无对应异常时禁用，
  菜单中显示其解除范围（多项用 `/` 连接，"全部异常状态"表示 `ALL`）。
- 道具目标选择（v0.1.8，参考实现已落地）：背包菜单为「选道具 → 选目标精灵」两步 ——
  回复 / 解除类道具选中后进入目标面板（3×2 六格，复用精灵面板布局），
  **可以是队伍中任意精灵（含替补）**；精灵球跳过目标步骤直接投向敌方。
  可用性判定改为「队伍中是否存在任一合法目标」（回复：未倒下且未满血；解除：有可解除的异常），
  精灵球恒可用。目标面板中不可选精灵呈灰格（仍可悬停查看详情），返回键回到背包菜单。
- 战斗动画（v0.1.11，参考实现已落地）：行动结算后由引擎演出事件驱动**进场 / 放出 / 收回 /
  技能释放 / 受击 / 倒下 / 投球 / 道具 / 逃跑**动画（见《接口文档_战斗服务.md》§16）。
  界面要求：① 事件序列播放期间**锁死行动输入**（按钮置灰），播完才解锁；② 精灵立绘、HP 条等
  在**动画结束后**才刷新（避免「还没挨打 HP 就掉了」，也避免引擎已换宠导致动画作用到新精灵）；
  ③ 日志与天气/场地行可在动画开始前刷新，让本回合文本与演出同步可见；④ 动画只用几何/纹理特效，
  **禁止对动态中文文本使用描边或特效**（实测每字约 50ms 且不缓存，会导致数秒卡顿）；
  ⑤ 整场景等比缩放（`UiScale`），动画按 640×427.6 设计单位书写。
- **己方倒下后的补位面板（v0.1.18，参考实现已落地）**：己方出战精灵倒下后**不再自动换宠**，
  引擎挂起等待玩家选择（`isAwaitingReplacement()`，见《接口文档_战斗服务.md》§3.3a）。界面要求：
  ① `render()` 在 `isOngoing()` 为真时**优先**判断 `isAwaitingReplacement()`，是则展示队伍选择
  面板并 `return`，**不得进入主菜单**（此时任何行动按钮都会被引擎拒绝）；
  ② 面板复用队伍列表的 3×2 六格布局与灰度规则（可点：健康且非当前出战），提示文案为
  「请选择接下来上场的精灵！」，**不提供「返回」按钮**（必须选出替补才能继续）；
  ③ 选中后调用 `chooseReplacement(idx)`，按通常流程取演出事件、播动画、刷新界面；
  ④ 选择已倒下精灵等非法目标不会退出等待状态，面板需保持可见并允许重试；
  ⑤ 参考实现：`BattleView.showReplacementMenu(party, activeIndex, onPick)` +
  `BattleView.Actions#onReplacementSelected(int)` + `BattleController#showReplacementMenu()`，
  控制台 demo 对应 `BattleConsole#doReplacement(BattleService)`。

### 3.4 存档对页面的约束（新增）

读档可能直达**任意场景**（如直接从地图中段恢复、从商店恢复）：

- 每个页面 Controller 必须**状态驱动渲染**：由调度方在展示前传入完整页面状态
  （队伍、金币、AP、货架、战斗现场等），Controller 依据状态渲染，**禁止依赖
  "从上一页顺序进入"的隐式前提**；
- 页面自身不留跨页历史（返回路径由流程系统维护，UI 只响应调度）。

### 3.5 精灵详情页与装备穿戴（v0.1.10，参考实现已落地）

主菜单队伍面板中点击精灵名进入**精灵详情页**（`org.example.view.PokemonDetailView`，独立场景切换），
精灵名以下划线样式 + 手型光标提示可点击。页面内容：

- 立绘区：立绘（公共工具 `org.example.util.SpriteLoader.load(name)`，无图回退「精灵名（暂无立绘）」占位）
  + 名称/等级/分类/图鉴描述（分类与描述取自宝可梦库，缺失时留空）；
- 信息区：属性、性格、异常状态（含混乱）、HP、EXP（当前/升级所需）；
- 数值区：六项能力值（实际值 vs 种族值对照）；
- 技能区：4 招（名称/属性/分类/威力/命中/PP 剩余）；
- **装备槽**：当前装备（名称+效果说明+「脱下」按钮）；下方装备库列表
  （`Player#getEquipment()`），每件显示名称/效果与状态按钮：已穿戴（禁用）、
  换过来（已穿在其他精灵上，穿戴时自动脱下原穿戴者）、穿戴；

交互规则：每次穿脱后**回调控制器重建主菜单**（`MainView.Actions#onShowPokemonDetail`），
详情页自身即时重建装备区；装备唯一性由 `Player#equip` 保证（穿给一只自动脱下另一只）。
装备获取来自肉鸽「装备补给」事件（`OptionType.REWARD`），由流程/控制器结算后入库。

### 3.5 存档位选择页（v0.1.11 新增，已落地）

`SaveSlotView(purpose, statuses, currentSlot, onChoose, onCancel)` —— 一屏列出 4 个存档位，
**三种用途复用同一个视图**（`Purpose.NEW_GAME / CONTINUE / SAVE`）：

| Purpose | 标题 | 可选档位 | 按钮文案 |
| --- | --- | --- | --- |
| `NEW_GAME` | 选择存档位 | 全部（覆盖由控制器二次确认） | 空档「在此开始」/ 已占用「覆盖并开始」 |
| `CONTINUE` | 继续游戏 | 仅 `SlotStatus#usable()`（存在且可解析） | 「继续游戏」 |
| `SAVE` | 保存游戏 | 全部 | 当前档「保存到当前档」/ 其他档「保存并切换到此」 |

- 每行展示 `SaveSlot#displayName()`、`SaveSummary#describe()` 与状态色
  （空档灰、正常深灰、损坏红字）；
- **视图不做任何读写**：摘要来自传入的 `SaveStore.SlotStatus`，选择结果经 `onChoose`
  回调交回 `MainController`，覆盖确认与失败提示都由控制器负责 —— 与其他页面
  「Controller 持有状态、View 只渲染」的约定一致；
- 启动页「继续游戏」在 `SaveStore#hasAnySave()` 为 false 时置灰并给出 tooltip；
- 「本轮已结束」的档位在列表里与进行中的档位**看起来一样**（摘要不含结束标记），
  由控制器在 `loadFromSlot()` 里按 `GameSession#isRogueRunFinished()` 拒绝载入并提示
  回启动页开新游戏（见 §3.5 场景入口）。

存档相关的场景入口（`MainController`）：

```
启动页 ──开始游戏──► 选初始精灵 ──► 选档位(NEW_GAME) ──► newGame + autoSave + 主菜单
  └──继续游戏──► 选档位(CONTINUE) ──► load ──► 主菜单
主菜单「保存游戏」──► 选档位(SAVE) ──►（覆盖其他已有档需确认）──► save ──► 回主菜单
                      所选档位成为当前档位，标题栏随之更新，之后自动存档写该档
主菜单「读取存档」──► 选档位(CONTINUE) ──► load ──► 主菜单（先 autoSave 当前这局再换档）
主菜单「返回主界面」──► autoSave + 释放会话 ──► 启动页（不退出程序）
一轮远征结束（通关 / 战败 / 队伍全灭）──► autoSave + 释放会话 ──► 启动页，在那里「开始游戏」开新档
读档被拒的三种情况：空档 / 存档损坏 / 该轮已结束 ──► 弹窗提示并留在选档页
主菜单 / 通过节点后 ──► autoSave（静默）
战斗中 ──► 手动存档与读取存档都被拒绝并提示「请先结束当前战斗」
```

### 3.6 路线地图页与商店页（v0.1.12 新增，已落地）

跟随《需求文档》§4「路线节点 / 行动点」改版，两个页面由「事件列表」改为「**按段选节点**」：

**`RogueFloorView`（路线地图）**

| 区域 | 内容 |
| --- | --- |
| 标题 | `第 N / 5 段 · <阶段名>`，阶段名取 `RoutePhase#getDisplayName()` |
| 副标题 | `行动点：X / Max　金币：N 🪙` |
| 节点列表 | 每段 3~5 个路线节点；**已走过的一次性节点**置灰且不可点；**常驻节点（路人 / 野外精灵 / 医院）走过后仍可点**，标题标注「已走过 N 次 · 可再次进入」与再次进入的行动点消耗；行动点不足以进入的节点禁用并给出提示 |
| 必然节点区 | 阶段为道馆 / 四天王 / 冠军 / 首领侵略战时改为展示 `buildMandatoryBox`（必然节点不占行动点） |
| 兜底按钮 | 本段行动点耗尽或已无节点可走时，显示「挑战道馆」按钮触发必然节点 |

> **v0.1.13**：节点列表无需为火箭队剧情线新增 UI——「火箭队出没」「火箭队抓捕神兽」「神兽偶遇」
> 都是普通路线节点（类型分别为 `ROCKET` / `ROCKET_CAPTURE` / `LEGENDARY`），沿用同一张卡片即可；
> 火箭队线击败首领后追加的 0 点「神兽偶遇」也只是列表里多一张**不消耗行动点**的卡片，
> 界面不必特殊处理。通关文案在首领侵略战胜利时改为「击败来袭的火箭队首领，本轮远征通关！」，
> 由 `MainController` 依据 `GameSession#isRogueAggressionTriggered()` 判断。
>
> **v0.1.14**：常驻节点可重复进入后，节点卡片改为按 `Option#apCostForNextEntry()` 显示消耗
> （已走过的常驻节点按类型默认消耗计，「免单」只对首次进入有效），并按 `Option#isRepeatable()`
> 决定是否渲染为灰色只读条目；行动点仍不足时按钮禁用。「挑战道馆」兜底入口的触发条件不变，
> 仍是 `RunData#hasSelectableOption()` 为 false（行动点已不足以进入任何节点）。
>
> **v0.1.15**：**每走完一个路线节点，本段节点列表整批重抽**——控制器在 `finishNodeStep(true)`
> 里调 `GameSession#refreshRogueRoute()`，随后才用新列表判定是否触发道馆（否则 AP 归零时旧列表里
> 的 0 点节点会让道馆永远不触发）。因此界面上「［已走过 N 次 · 可再次进入］」标签在同段内通常不会再
> 出现（旧节点已被替换），常驻节点仍因「每批必然入列」而随时可再次进入，可见的置灰条目只剩
> 同一批内走完的一次性节点。页面渲染逻辑本身无需改动。

**`ShopView`（商店）**

- 构造参数 `ShopView(session, stock, onBuy, onLeave)`，展示本次上架的 `ShopStock.Entry` 列表
  （类型标签 + 名称 + 效果说明 + 售价）、当前金币余额；买不起的条目禁用；购买成功后即时刷新余额；
- 商品**不再按段解锁**（v0.1.21）—— 第 1 段起全部消耗品与全部装备都可能上架，
  「越往后商品越多」只体现为**货架格位变多**（`RouteConfig#shopStockSize`）与**售价通胀**
  （`RouteConfig#shopPrice`），两者均为 `RouteConfig` 中的可调数值（见需求文档 §4.5）。
- **大师球不上架**：`i_master_ball` 是剧情专属道具（击败火箭队首领必得，见需求文档 §4.4），
  `ShopStock` 的商品池把它标为「不售卖」，只出现在道具图鉴里并标注「不售卖」。
- **展示名不在商店侧自存**：`ShopStock` 通过 `GameData.instance().item(id).getName()` 取消耗品名、
  `equipment(id).getName()` 取装备名，只有注册表里查不到该 id 时才退回内置兜底名。
  这样即便道具表 / 装备表改名，商店也自动跟随。

> **v0.1.16（商店上架装备）**：货架改为两个分区，装备与消耗品各自抽签、各自占位 ——
> - **消耗品分区**：原 16 件池，购买走背包，可重复购买；
> - **装备分区**：池子**全量取自 `GameData#allEquipment()`**（47 件装备 + 30 种树果），
>   基础价按 `HeldItemEffect` 分类推出（`ShopStock#equipmentBasePrice`），
>   因此**新增装备只要登记进数据表就会自动上架**；
> - **格位**：`RouteConfig#shopStockSize` 只管总格数，装备另占
>   `shopEquipmentStockSize` 格（第 1 段 1 格 → 第 3 段起 2 格），剩余格位全给消耗品；
> - **已拥有不再上架**：`ShopStock.forSegment(segment, random, ownedIds)` 会把玩家已拥有的装备
>   剔出池子，控制器传 `player.getEquipment()` 的 id 集合；
> - **购买分流**：`MainController#buyFromShop` 按 `Entry#isEquipment()` 分流 —— 消耗品入背包，
>   装备走 `Player#addEquipment` 并随即从货架下架（`ShopStock#withoutEntry`），
>   避免同一件重复购买。
>
> 展示层只多渲染「【装备】/【道具】」前缀与装备的效果说明，禁用规则不变。
>
> **v0.1.21（取消分段解锁）**：原实现有「解锁段位」一维 —— 消耗品按 5 档、装备按
> `HeldItemEffect` 分类逐段放开，第 1 段的货架只会出现三件基础商品与最便宜的装备。
> 需求方裁决**取消这一维**：`Consumable` 记录的第四列由 `unlockSegment` 改为 `sold`
> （是否在商店售卖），`equipmentUnlockSegment(...)` 整个删除，商品池退化为一个与段号无关的
> 常量集合。因此 `ShopStock#unlockedConsumableIds` / `#unlockedEquipment` 更名为
> `#sellableConsumableIds` / `#sellableEquipment`（去掉 `segment` 参数），
> `CatalogEntry` 的 `unlockSegment` 字段也换成 `sold`。**段号对货架的影响只剩格位与售价两处**。

**`MainView` 标题栏**

新增金币显示：`第 N 段 · 地图 · 金币 N 🪙[ · 存档名]`。金币为负（本轮远征尚未开始）时**不显示**
金币段，避免主菜单上出现「金币 -1」。

> 与 §3.4 的「状态驱动渲染」一致：两个页面都不读全局状态，节点列表 / 货架 / 金币均由
> `MainController` 在展示前传入。



### 3.7 道具图鉴页（v0.1.20 新增，已落地）

**`ItemDexView`（道具图鉴）** —— 主菜单「道具图鉴」按钮进入，纯展示 + 装备穿脱：

- 构造参数 `ItemDexView(player, background, onBack)`；`createScene()` 依次渲染标题栏、
  筛选栏、可滚动卡片列表与「返回主菜单」栏，`onBack` 回主菜单（重建以同步队伍变化）。
- **口径是「全量列出」**：数据来自 `ItemDexData#build(player)`，其条目数与
  `ShopStock#catalog()` 完全相同（当前 **93 件** = 16 件消耗品 + 77 件装备），
  **不做收集解锁**——未拥有的也照常显示全部信息，只在状态位标注「未拥有」。
- **四档筛选**（`ItemDexView.Filter`，单选 `ToggleButton`）：
  全部 / 道具 / 装备 / 已拥有；摘录行同时给出「共 N 件（x 件道具 + y 件装备）已拥有 Z 件」。
- **每张卡片**：名称（前置 `【道具】` / `【装备】`）、状态位（`已拥有 ×N` / `已拥有 · 未穿戴` /
  `已拥有 · [精灵名] 持有` / `未拥有`）、效果说明、以及来源行
  「商店出售 · 基础价 X 金币（段数越靠后售价越高）」（v0.1.21 前为「第 N 段起可在商店购买」）；
  不售卖的道具（大师球）来源行改显示「不售卖 · 剧情专属道具（击败火箭队首领必得）」。
- **装备可直接穿脱**（已拥有时）：未被穿戴 → 「穿给」`ComboBox<Pokemon>`（显示 `名字 Lv.N`，
  默认先发）+「穿戴」按钮；已被穿戴 → 「[名字] 持有」+「脱下」按钮。走的是
  `Player#equip` / `Player#unequip`，因此**全队唯一穿戴**的约束由模型层保证，界面层不重复校验；
  操作后本页即时重建。未拥有的装备给出「可在商店购买（第 1 段起即可能上架），或在肉鸽『装备补给』事件中获得」提示。
- **消耗品为只读**（战斗内才可使用）；`ItemDexData#describe(Item)` 按类别现推一句效果说明
  （`HEAL` → 回复量、`POKE_BALL` → 捕捉倍率或「必定捕捉成功」、`CURE` → 解除范围），
  因为 `items.csv` 没有描述列；装备说明直接取 `HeldItem#getDescription()`。

> **v0.1.20（目录口径复用）**：`ShopStock#catalog()` 与 `#forSegment` 共用同一份消耗品池
> （`CONSUMABLES`）与同一个定价函数（`equipmentBasePrice`），
> 因此图鉴上写的价格**不会与货架说法不一**；新增装备登记进 `equipment.csv` 后，
> 商店与图鉴都会自动包含，这两处都无需改动。
>
> 排序为「消耗品在前 → 基础价 → 名称」（v0.1.21 起不再有解锁段位这一维），
> 读起来即从便宜常见到稀有强力的推进线；
> 展示名一律取自 `GameData`（查不到才退回内置兜底名），与商店同一套规则。

**`MainView` 入口**：`Actions` 新增 `onShowItemDex()`，行动栏变为
「进入层内事件 / 道具图鉴 / 保存游戏 / 读取存档 / 返回主界面」五个按钮。

以下接口/类型已由 feature/battle（战斗系统）交付，版本 v1.0（v1.4 新增训练师轮战，
见《接口文档_战斗服务.md》§3.4）；契约引用以此为准。

### 4.1 战斗服务（引用《接口文档_战斗服务.md》§3）

| 需求 | 使用方页面 | 说明 |
| --- | --- | --- |
| `BattleService.Status` 枚举 | 战斗页、结算页 | 战斗结束分流：`PLAYER_WIN / PLAYER_LOSE / FLED / CAUGHT / ONGOING`；`FLED/CAUGHT` 仅野生遭遇会出现 |
| `useMove / useItem(item, partyIndex) / useItem(item) / tryRun / switchActive` | 战斗页 | 行动入口；返回 `List<String>`（本回合新日志）；非 ONGOING **或补位等待中**（`isAwaitingReplacement()`）调用抛 `IllegalStateException`（UI 先判 `isOngoing()` 与 `isAwaitingReplacement()`）。**v1.8**：`useItem(item, partyIndex)` 对队伍任意精灵（含替补）使用回复/解除道具，`useItem(item)` 为作用于出战精灵的便捷重载；精灵球忽略 `partyIndex`。训练师轮战中 `tryRun()` 与对训练师投球只回提示日志、不消耗回合，按钮可置灰或保留提示。**v0.1.18**：等待补位期间本行全部方法都会被引擎拒绝，界面不应展示主菜单（见 §3.3 补位面板） |
| `playerActive() / foeActive() / getPlayer()` | 战斗页 | 面板与队伍菜单渲染。**敌方面板一律用 `foeActive()`**：野生为野生精灵，训练师轮战为训练师当前出战精灵（训练师换宠后自动跟随）；`getWild()` 在训练师轮战中为 `null`。**v0.1.18**：己方不再由引擎自动换宠（见 §3.3），因此 `playerActive()` **始终非 null** —— 补位等待期间返回刚倒下的那只，补位后返回新上场精灵 |
| `isAwaitingReplacement() / chooseReplacement(int)` | 战斗页 | **v0.1.18**：己方出战精灵倒下且队伍仍有健康精灵时为 `true`，此时 `isOngoing()` 仍为 `true` 但一切行动被拒；界面应改为展示**补位面板**（复用队伍列表的 3×2 六格，不可返回），玩家选中后调用 `chooseReplacement(idx)`，然后照常取事件 / 播动画 / 刷新 |
| `getTrainer()` | 战斗页 | 训练师轮战非空：可用 `getTrainer().getName()` 显示对手名，`getTrainer().getParty()` 显示对方队伍/剩余数量 |
| `getLog()` | 战斗页 | 完整日志只读，行动后全量覆盖展示 |
| `drainEvents()` | 战斗页 | **取走**本回合演出事件（`List<BattleEvent>`，取走即清空）驱动战斗动画；为纯演出数据，不参与结算，界面可按需忽略。**v1.11**：推荐顺序「行动 → 取事件 → 播动画 → 播完再刷新界面」（见《接口文档_战斗服务.md》§16）。**v1.12**：HP 条改由每个事件自带的 `hp` 快照在**该步动画开始前**刷新（先扣血、再播动画），立绘/等级/异常状态等仍在全部播完后统一刷新 |
| `getBag()` | 战斗页 | 配合 `Bag.availableStacks()` 渲染背包菜单 |

### 4.2 工厂与遭遇环境（引用《接口文档_战斗服务.md》§4）

| 方法 | 归属 | 说明 |
| --- | --- | --- |
| `BattleServices.newBattle(Player, Pokemon[, Random])` | 战斗页创建入口（野生遭遇） | 玩家无存活出战精灵抛异常，创建前校验 `hasHealthyPokemon()` |
| `BattleServices.newTrainerBattle(Player, Trainer[, Random])` | 战斗页创建入口（训练师轮战） | 敌方为一整支队伍，某一方精灵全部倒下才结束（不可逃跑/捕捉）；训练师队伍无健康精灵时抛 `IllegalArgumentException` |
| `BattleServices.newBattle/newTrainerBattle(..., BattleDataPort, BattleGrowthPort)` | 组装入口（v1.9 起） | 组装层（`MainController` / `PokemonBattleAdapter`）同时注入数据端口与**成长端口**；UI 只渲染引擎日志，不感知成长判定过程。v1.11 起成长改为**每击倒一只即时结算**，故升级 / 学招文本可能出现在**战斗进行中**的日志里 |
| `pendingLearnChoices() / decideLearn(int)` | 战斗页学习抉择弹窗 | 技能栏已满时成长模块挂起抉择；`decideLearn(槽位)` 返回日志行（`-1` = 放弃学习），队列为空抛 `IllegalStateException`。**v1.11 起该队列可能在对战中就非空**：`BattleController#render` 应**先处理挂起抉择**（逐项弹窗直至队列空），再决定展示战斗菜单还是结局 |
| `WildEncounter.levelAround / randomWild` | 肉鸽系统 | 遭遇生成属流程/肉鸽侧（v1.7 已移出战斗模块），UI 不直接调用；`Optional` 空值需空态提示 |

### 4.3 model 只读查询（UI 展示所需字段，归宝可梦系统维护）

| 对象 | UI 用到的查询 |
| --- | --- |
| `Pokemon` | `getName / getLevel / getSpecies / getCurrentHp / getMaxHp / getExp / expToNextLevel / isFainted / getMoveSlots / getMoves / getStats / hasType / getStatus / isConfused / getSleepTurns / getConfusionTurns / getBadlyPoisonCounter / effectiveSpeed / effectiveAttack`（v0.1.5 增异常状态与计倍率的实际能力值） |
| `Species` | `getId / getName / getTypes / getBaseStats / canEvolveAt(level)` |
| `Stats` | `getHp / getAttack / getDefense / getSpAttack / getSpDefense / getSpeed` |
| `ElementType` | `getDisplayName()`；`parse(name)` 供数据加载 |
| `Player` | `getName / getParty / getActive / getBag / hasHealthyPokemon / healParty`（医院页） |
| `Trainer`（v1.4 新增） | `getName / getParty / getActive / getPartySize / hasHealthyPokemon / isPartyAllFainted`（训练师轮战的对手名、对方队伍与剩余精灵展示） |
| `Bag / ItemStack` | `availableStacks() / countOf / getAll`；`getItem() / getCount()` |
| `Item` | `getId / getName / getCategory / getEffect / isAlwaysCatch / getCuresSpec / canCure / curedStatuses / curesAll`（`ItemCategory.HEAL / POKE_BALL / CURE`） |
| `MoveSlot / Move` | `getMove / getPp / exhausted`；`getId / getName / getType / getCategory / getPower / getMaxPp / hasInfliction / getInflicts / getInflictionChance` |
| `BattleEvent`（v1.11 新增） | `kind / side / actor / moveName / element / category / success / hp / opponent()`；嵌套枚举 `Kind`（`BATTLE_START / SEND_OUT / RECALL / MOVE / HIT / FAINT / CAPTURE / ITEM / RUN`）与 `Side`（`PLAYER / FOE`）；`ElementType.getColorCode()` 供动画配色，`MoveCategory` 供动画形态。**v1.12**：`hp` 为 `Hp(current, max)` 快照（无 HP 变化时为 `Hp.NONE`，先判 `present()`），界面在播该步动画前据此刷新血条 |
| `StatusCondition` | `getDisplayName / isMajor / isVolatile / immunityType`；`parse(name)` 供数据加载 |

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

### 4.6 成长 / 图鉴数据接口（v0.1.10 新增，供图鉴与成长面板页使用）

需求 §3.2「多次使用同族精灵 → 后续遭遇的精灵个体值提升」的**数据接口已就绪**，
UI 侧当前**尚未接入界面**（本轮只交付模型 + 服务 + 单测）。

> **进度是永久存档**：三个入口取到的都是同一份 `GrowthProgress`，由 `GrowthProgressStore`
> 载入并**自动保存**（每次捕捉 / 对战 / 清档后立即落盘），因此**关闭程序再打开、开启新一轮远征，
> 已获得的加成仍然生效**。UI 无需关心保存时机，也**不应**自行读写该文件。
>
> **v0.1.11 起，成长记录改为按存档位存放**：4 个档位各有自己的
> `saves/slotN/growth-progress.txt`（由 `SaveStore#loadGrowth/createGrowth` 提供），
> 换档位等于换一份个体值成长。`GrowthProgressStore` 的全局单文件路径仍可用于自定义位置
> （`GrowthProgressStore#at(Path)`），但新档位不再使用。详见 §4.7 与《接口文档_存档系统.md》。

| 取数入口 | 说明 |
| --- | --- |
| `GameSession#getGrowthProgress()` | 会话级成长进度，**局外（主菜单 / 图鉴页）首选入口** |
| `PokemonService#getGrowthProgress()` | 宝可梦系统侧同一份进度的查询入口 |
| `GrowthService#getProgress()` | 成长模块侧同一份进度（与战斗模块共用） |

`GrowthProgress`（`org.example.growth`）对外查询：

| 方法 | 返回 | 典型用途 |
| --- | --- | --- |
| `record(String speciesId)` | `SpeciesGrowthRecord` | 单族图鉴条目；**无记录时返回全零空记录且不写入** |
| `dexEntries()` | `List<SpeciesGrowthRecord>` | 图鉴列表（按物种 id 排序，含只参战未捕捉的族） |
| `captureCount(id)` / `battleCount(id)` | `int` | 图鉴列表的「捕捉次数 / 对战次数」列 |
| `ivBonus(id)` | `int` | 单族个体值加成（0~31），图鉴展示用 |
| `globalIvBonus()` | `int` | **全局加成**（各族之和，封顶 31）；即后续所有精灵获得的个体值提升量 |

`SpeciesGrowthRecord` 只读字段：`getSpeciesId / getCaptureCount / getBattleCount / getIvBonus / isBlank`。

> 展示注意：加成实际作用于**个体值**（六项个体值各加 `globalIvBonus()` 并截断 31），
> **种族值不变**；面板数值变化由属性公式自动体现，UI 直接用 `Pokemon#getStats()` 即可。
> [`GrowthProgress#clear()`] 仅供「重开存档」，UI 不应在常规流程调用 —— 它**同时清空本地存档文件**，
> 玩家已累积的永久加成会一并丢失，务必配合二次确认。

### 4.7 存档系统接口（v0.1.11 新增，已接入界面）

存档系统已交付并接线，UI 只需依赖 `SaveManager`（详见《接口文档_存档系统.md》）：

| 入口 | 说明 |
| --- | --- |
| `SaveManager#defaultManager()` / `store()` | 默认编排器 / 其仓库（根目录 `<user.home>/.pokerouge/saves`） |
| `SaveStore#statuses()` | 4 个 `SlotStatus`，档位列表的**唯一数据源** |
| `SlotStatus#empty() / readable() / usable() / summary()` | 空档 / 可解析 / 可继续；`summary()` 为 `null` 表示空档 |
| `SaveSummary#describe()` | 一行式摘要（`小明 · 队伍 3 · 第 2 段 · 行动点 4 · 金币 320 · 2026-09-10 15:30`；尚未开始远征时为 `… · 未开始远征`） |
| `SaveManager#save(slot, player, session)` | 手动保存；**失败抛异常**，UI 须提示 |
| `SaveManager#autoSave(slot, player, session)` | 自动保存；**失败只返回 false**，UI 不得打扰玩家 |
| `SaveManager#load(slot)` | `Optional<GameSession>`；空档返回空，损坏抛 `SaveFormatException` |
| `SaveManager#newGame(slot, trainerName, starter)` | 清空该档后建立新会话 |

> **每个档位各自独立**：进度快照与图鉴成长都存放在该档位自己的目录下，换档位等于换一份
> 个体值成长。UI 只需持有当前 `SaveSlot`，**不应**自行读写 `growth-progress.txt`。
>
> 存档时机由控制器把握：**仅在未作战时**（主菜单「保存游戏」手动保存、「读取存档」换档前保护当前进度、
> 回到主菜单 / 通过节点后 / 离开这一局前自动保存）；战斗场景接管舞台期间手动保存与读取存档都会被拒绝。
> UI 侧因此**无需**在战斗页放置存档入口。
>
> 读档入口有两处且共用同一段逻辑（`loadFromSlot`）：启动页「继续游戏」与主菜单「读取存档」
> （后者先 `autoSave()` 当前这局再让玩家换档）。空档、损坏档、以及**那一轮已经结束**的档位
> 都会被拒绝并提示，视图只需如实渲染选档页。

### 4.8 肉鸽路线只读查询（v0.1.12 新增，已接入界面）

路线地图页 / 商店页所需的运行状态，一律经 `MainController` 从 `GameSession#getRogueRunData()`
（`RunData`）与 `GameSession#getRogueOptions()` 取**只读快照**后传参渲染，UI 不做规则判定：

| 入口 | 说明 |
| --- | --- |
| `RunData#getSegment() / getAp() / getApMax()` | 段号、当前行动点、本段上限（标题与副标题） |
| `RunData#getGold()` | 金币余额（标题栏与商店页） |
| `RunData#getPhase()` | `RoutePhase`（`EXPLORING`/`GYM`/`ELITE_FOUR`/`CHAMPION`/`CLEARED`），取 `getDisplayName()` 显示 |
| `RunData#getAvailableOptions() / getMandatoryOption()` | 本段可选节点 / 当前必然节点（`Optional`，仅必然阶段有值） |
| `RunData#isNotStarted() / isGameOver() / isCleared()` | 「未开始远征」/ 失败 / 通关三态，决定主菜单与结算页文案 |
| `GameSession#refreshRogueRoute()`（**v0.1.15 新增**） | 节点步骤收尾用：整批重抽本段剩余节点（AP / 段号 / 金币不变）。是**写操作**，只在 `MainController#finishNodeStep(true)` 内调用，视图不调用 |
| `Option#getType() / getTypeDisplayName() / getDescription() / isConsumed() / isRepeatable() / getVisitCount() / apCostForNextEntry()`（**v0.1.14 更新**） | 单个节点的展示与置灰依据：一次性节点 `isConsumed()` 后置灰，常驻节点走过后仍可点并按 `apCostForNextEntry()` 显示下次消耗 |
| `RoutePhase#isMandatoryBattle() / toOptionType()` | 必然阶段与 `OptionType.GYM`/`ELITE_FOUR`/`CHAMPION`/`ROCKET_INVASION` 的映射 |
| `GameSession#isRogueRocketLineUnlocked()` / `isRogueRocketBossDefeated()` / `isRogueLegendaryMet()` / `isRoguePendingLegendary()` / `isRogueAggressionTriggered()`（**v0.1.13 新增**） | 火箭队剧情线与神兽偶遇的**只读查询**：供 UI 展示剧情提示或决定通关文案；UI 不参与分支判定（进入节点即由 `RogueTurnManager` 打标记） |

> 行动点是否足够、节点是否可点、商店能否买得起，**均以视图的禁用态表达**，
> 真正扣点 / 扣金币发生在点击回调回来之后，由控制器与服务侧完成。

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

- 战斗页：行动后全量刷新（面板/日志/按钮区），与 battle 参考实现一致；**v0.1.18**：己方
  不再由引擎自动换宠，刷新时若 `isAwaitingReplacement()` 为真则改渲补位面板（不可返回），
  补位前 `playerActive()` 为刚倒下的那只（非 null）；
- 非战斗页：进入时构建 + 操作后局部刷新（金币/AP/货架文本）；
- 战斗过程更新（HP 动画等）**v0.1.11 已落地**：由 `drainEvents()` 的演出事件驱动动画，
  见 §6.6；动画一律在 JavaFX 主线程用 `Animation`/`setOnFinished` 串联，无需自建定时器。

### 6.6 战斗动画层（v0.1.11，参考实现已落地）

参考实现：`org.example.view.BattleView`（动画层）+ `org.example.controller.BattleController`（编排）。

| 要点 | 约定 |
| --- | --- |
| 数据来源 | `BattleService.drainEvents()`；界面**不解析日志文本**推断动作 |
| 编排 | 控制器持有 `playing` 标志：结算后取事件 → 锁输入 → 逐条播动画 → 播完解锁并 `render()` |
| 播放 | `playEvents(events, onFinished)` 逐条串行播放（每条动画用 `setOnFinished` 触发下一条），事件为空立即回调 |
| 特效层 | 与内容根同尺寸的透明 `Pane` 叠放（`StackPane`），`setMouseTransparent(true)`；飞行道具/闪光/光点挂此层，落位用 `sceneToLocal` 换算（不受全局缩放与内边距影响） |
| 立绘定位 | 事件只携带**精灵名**：放出/收回/倒下动画先按名切图再演，避免引擎已换宠（训练师派出 / 玩家补位）时作用到错误精灵 |
| 刷新时机 | 动画开始前只刷日志 + 天气/场地；立绘、HP 条在动画**结束后**刷新 |
| 输入防护 | 播放期间 `setInputLocked(true)` 禁用行动区，控制器各行动处理器再加 `if (playing) return;` 二次防护 |
| 性能红线 | **禁止**对动态中文文本使用描边（`Stroke`）或 `Effect`：实测每字约 50ms 且不缓存，会造成数秒卡顿。动画只用形状/图片特效 |
| 验证 | `src/test/java/org/example/view/BattleAnimationSmokeTest.java`（默认跳过）：`mvn -o test -Dtest=BattleAnimationSmokeTest -Dbattle.smoke=true -DfailIfNoSpecifiedTests=false`，在真实工具包/窗口下确认动画播完、回调触发、输入解锁 |


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
| v0.1.4 | 2026-09-10 | 同步《接口文档_战斗服务.md》v1.4 训练师轮战：§3.3 增训练师轮战界面要求（敌方面板取 `foeActive()`、对手名与剩余精灵、逃跑/投球被拒提示）；§4.1 契约表补 `foeActive()/getTrainer()` 并说明 `getWild()` 在训练师战为 null；§4.2 补 `newTrainerBattle` 工厂；§4.3 补 `Trainer` 只读查询 | [待确认：UI 负责人] |
| v0.1.5 | 2026-09-10 | 同步《接口文档_战斗服务.md》v1.5 异常状态：§3.3 增状态徽章、招式异常提示、异常摘要与实际速度、解除道具可用性要求；§4.3 契约表补 `Pokemon` 异常查询与 `effectiveSpeed/effectiveAttack`、`Item` 解除范围方法、`Move` 附带异常字段与 `ItemCategory.CURE`；新增 `StatusCondition` 只读查询 | [待确认：UI 负责人] |
| v0.1.6 | 2026-09-10 | 同步《接口文档_战斗服务.md》v1.6 数据端口：战斗模块只消费数据，技能/种族/野生池由外部数据模块经 `BattleDataPort` 注入；UI 侧仅在组装入口注入，渲染无变化 | [待确认：UI 负责人] |
| v0.1.7 | 2026-09-10 | 同步《接口文档_战斗服务.md》v1.7 职责收敛：遭遇生成（等级浮动 / 随机挑种族）移出战斗模块，改由组装侧 `WildEncounter` 提供；§1.2 战斗系统行与外部依赖表同步 | [待确认：UI 负责人] |
| v0.1.8 | 2026-09-10 | 同步《接口文档_战斗服务.md》v1.8 道具目标选择：§3.3 增「选道具 → 选目标精灵」两步交互与新的可用性判定；§4.1 契约表补 `useItem(item, partyIndex)` 重载说明 | [待确认：UI 负责人] |
| v0.1.9 | 2026-09-10 | 同步《接口文档_战斗服务.md》v1.9 成长判定外移：经验/升级/学招/进化改由外部成长模块经 `BattleGrowthPort` 结算；§1.2 战斗系统行与 §4.2 组装入口补成长端口注入与 `pendingLearnChoices()/decideLearn(int)` 学习抉择弹窗，渲染无变化 | [待确认：UI 负责人] |
| v0.1.10 | 2026-09-10 | 同步《接口文档_战斗服务.md》v1.10 个体值成长：**新增 §4.6 成长/图鉴数据接口**（`GameSession#getGrowthProgress()`、`PokemonService#getGrowthProgress()`、各族捕捉/对战次数与个体值加成查询），供后续图鉴/成长面板页接入；§3.3 战斗页无需改动（个体值影响面板数值，渲染字段不变） | [待确认：UI 负责人] |
| v0.1.11 | 2026-09-10 | **存档系统落地并接线**：§3.2 页面总表增「存档位选择」页；**新增 §3.5 存档位选择页**（`SaveSlotView` 三用途复用、视图不做 IO、启动页「继续游戏」置灰条件、场景入口流转）；**新增 §4.7 存档系统接口**（`SaveManager`/`SaveStore#statuses()`/`SlotStatus`/`SaveSummary`，四个档位各自独立的图鉴成长，存档仅在未作战时）；§4.6 说明成长进度已改为按档位存放 | [待确认：UI 负责人] |
| v0.1.12 | 2026-09-10 | **路线节点 / 行动点改版落地**：§3.2 页面总表增「商店」页并改写「路线地图」行；**新增 §3.6 路线地图页与商店页**（`RogueFloorView` 段 / 行动点 / 金币标题、已走过节点置灰、必然节点卡片与「挑战道馆」入口；`ShopView` 余额与买不起禁用；`MainView` 标题栏金币与负值隐藏）；**新增 §4.8 肉鸽路线只读查询**（`RunData`/`Option`/`RoutePhase`，界面只读不判规则）；§4.7 摘要示例随存档 v2 更新 | [待确认：UI 负责人] |
| v0.1.13 | 2026-09-10 | **火箭队剧情线与神兽偶遇落地**：§3.6 补「节点列表沿用同一张卡片、0 点神兽偶遇即多一张不耗点卡片、通关文案随首领侵略战区分」说明；§4.8 补 `ROCKET_INVASION` 映射与 5 个剧情线**只读查询**入口 | [待确认：UI 负责人] |
| v0.1.14 | 2026-09-10 | **常驻节点可重复进入**：§3.6 节点列表改为「一次性节点置灰、常驻节点走过后仍可点并标注『已走过 N 次 · 可再次进入』」，卡片按 `apCostForNextEntry()` 显示下次消耗；§4.8 `Option` 只读入口补 `isRepeatable()/getVisitCount()/apCostForNextEntry()` | [待确认：UI 负责人] |
| v0.1.15 | 2026-09-11 | **节点列表刷新 + 道馆胜利推进修复**：§3.6 说明「每走完一个路线节点整批重抽本段节点」及其对「已走过」标签的影响；§4.8 新增 `GameSession#refreshRogueRoute()`（写操作，仅 `MainController#finishNodeStep(true)` 调用）并把误写的 `RunData#getRouteOptions()` 更正为 `getAvailableOptions()` | [待确认：UI 负责人] |
| v0.1.16 | 2026-09-11 | **界面流转收敛到启动页（跟随《接口文档_存档系统.md》v2.3）**：§3.1 场景图新增启动页（程序启动 / 返回主界面 / 一轮结束的落点）并把「结束 → 回 mainMenu」改为回启动页；§3.2 页面总表增「启动页」行、主菜单行改为「进入层内事件 / 保存游戏 / 读取存档 / 返回主界面（不退出程序）」；§3.5 场景入口补「读取存档」「返回主界面」「一轮结束回启动页」与被拒读档的三种情况；§4.7 存档时机补「换档前保护进度 / 离开这一局前」 | [待确认：UI 负责人] |
| v0.1.17 | 2026-09-11 | 同步《接口文档_战斗服务.md》v1.11 逐只即时结算：§1.2 战斗系统行说明「每击倒一只即时申报」；§4.2 组装入口与 `pendingLearnChoices()/decideLearn(int)` 行补「战斗进行中即可能非空」与战斗页渲染顺序（挂起抉择优先、结局最后）；裁决连线：`BattleController#render` 已按此顺序实现 | [待确认：UI 负责人] |
| v0.1.18 | 2026-09-11 | 同步《接口文档_战斗服务.md》v1.14 己方倒下后的玩家补位：§3.3 新增「补位面板」界面要求（优先于主菜单渲染、复用队伍 3×2 六格、无返回键、选中后 `chooseReplacement`）；§4.1 契约表补 `isAwaitingReplacement()/chooseReplacement(int)` 并修订行动方法与 `playerActive()` 说明（不再为 null）；§6.4 / §6.6 去除「自动换宠」旧描述 | [待确认：UI 负责人] |
| v0.1.19 | 2026-09-12 | **商店上架全部装备（77 件）**：§3.6 `ShopView` 段改写 —— 货架分「消耗品 / 装备」两分区各自抽签，装备池全量取自 `GameData#allEquipment()`（47 件装备 + 30 种树果）并按 `HeldItemEffect` 定解锁段位与基础价，格位由 `RouteConfig#shopEquipmentStockSize` 控制（第 1 段 1 格 → 第 3 段起 2 格）；已拥有的装备不再上架（`forSegment(segment, random, ownedIds)`）；购买按 `Entry#isEquipment()` 分流（消耗品入背包 / 装备入库并下架）；展示层增「【装备】/【道具】」前缀与装备说明 | [待确认：UI 负责人] |
| v0.1.20 | 2026-09-12 | **新增道具图鉴页**：§3.2 页面总表增「道具图鉴」行、主菜单行补入口（五个按钮）；**新增 §3.7 `ItemDexView`**（`ItemDexData#build(player)` 全量列出 `ShopStock#catalog()` 的 93 件 = 16 消耗品 + 77 装备，四档筛选 `Filter`，卡片给出效果说明 / 来源（第 N 段起可买 + 基础价）/ 已拥有·未拥有·某队员持有标注；已拥有装备经 `Player#equip` / `#unequip` 直接穿脱，消耗品只读；`describe(Item)` 按类别现推消耗品说明）；`ShopStock` 新增 `CatalogEntry` 与 `catalog()`，与 `forSegment` 共用同一份消耗品池与定价 / 解锁函数 | [待确认：UI 负责人] |
| v0.1.21 | 2026-09-12 | **取消商店分段解锁**：§3.6 `ShopView` 段改写 —— 消耗品与装备的「解锁段位」一维整个删除，第 1 段起全部商品都可能上架，段号对货架的影响只剩**格位数量**（`RouteConfig#shopStockSize` / `shopEquipmentStockSize`）与**售价通胀**（`shopPrice`）；**大师球 `i_master_ball` 改为剧情专属道具**（`Consumable.sold = false`，不进商店抽签池，仍由火箭队首领战必得，图鉴照常列出并标注「不售卖」）；`Consumable` 第四列 `unlockSegment` → `sold`，`equipmentUnlockSegment(...)` 删除，`ShopStock#unlockedConsumableIds` / `#unlockedEquipment` 更名为 `#sellableConsumableIds` / `#sellableEquipment`（去掉 `segment` 参数），`CatalogEntry.unlockSegment` → `sold`；§3.7 图鉴卡片来源行与排序口径同步改写（排序改为「消耗品在前 → 基础价 → 名称」） | [待确认：UI 负责人] |

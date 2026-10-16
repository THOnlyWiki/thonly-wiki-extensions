# thonly-wiki-extensions

东方Project线下活动维基（[THOnlyWiki/WikiKernel](https://github.com/THOnlyWiki/WikiKernel)）的官方扩展之一，
聚合「线下活动」相关的全部功能：

- **线下活动速查表**（`/wiki/线下活动速查表`）：分年份数据表、行内编辑（弹窗表单）、变更历史与回滚；
  编辑需要登录 + 对「线下活动速查表」有 `edit` 权限（默认登录用户，可在「特殊:权限配置」按用户组调整）；
- **活动分布地图**（`/wiki/活动分布地图`）：本地 ECharts 中国地图，按 `分类:<省>活动` 的**词条数**着色，
  点省份弹窗列出词条；「全屏预览」是页面内的大弹窗（不是浏览器全屏），弹窗里点省份照样弹词条列表；
- **活动日历**（`/wiki/活动日历`、解析标签 `<touhou-calendar />`、独立页 `/calendar`）：
  本地 evoCalendar，数据来自本站活动表（不依赖 Google 表格，也没有 iframe）；
- **独立页** `/excel`（只读表格）与 `/excel/calendar/<年>/<id>.ics`（单条活动的 .ics 下载，手机/电脑日历通用）；
- **API**：读 `/api/thonly-activity/years|list|revisions`；写 `/api/thonly-activity/save|delete|restore`
  （登录 + 同源 + 未封禁 + `edit` 权限；每次修改写一条 `edit` 日志，最近更改可见）。

## 安装

把构建出的 jar 放进 WikiKernel 的 `plugins/` 目录，或在源码树里执行：

```bat
.\gradlew.bat :plugin:thonly-wiki-extensions:build installPlugins
```

数据表 `thonly_activity_<年>` 与 `thonly_activity_revision` 由插件声明、core 自动建表（SQLite / MySQL / H2）；
首次启动时表为空会从 `src/main/resources/thonly-activity-seed.tsv` 导入种子数据（三年 592 行）。
功能开关 `features.thonly-wiki-extensions`（默认开），可在 `/init` 或「特殊:权限配置 → 插件设置」里关闭。

## 与主仓库的关系

本仓库是 [WikiKernel](https://github.com/THOnlyWiki/WikiKernel) 的**子模块**（主仓库路径 `plugin/thonly-wiki-extensions`），
随主仓库一起发布。适配 WikiKernel `0.0.1-SNAPSHOT`；插件开发约定见主仓库 `docs/PLUGIN-DEV.md`。

## 许可

GPL-3.0（与主仓库一致），见 [LICENSE](LICENSE)。

package cc.thonly.wikikernel.plugin.thonlywikiextensions.service;

import cc.thonly.wikikernel.common.extension.Viewer;
import cc.thonly.wikikernel.common.service.DbRow;
import cc.thonly.wikikernel.common.service.LogWriter;
import cc.thonly.wikikernel.common.service.PluginDatabase;
import cc.thonly.wikikernel.plugin.thonlywikiextensions.ThonlyActivitySchedulePlugin;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 活动速查表数据（分年份物理表 + 变更历史）。
 * <p>
 * 表结构（一年一张）：
 * <pre>
 * thonly_activity_&lt;年&gt; (id, category, status, name, region, date, qq, sort_order,
 *                        deleted, created_at, updated_at)   -- 主键 id 由本类显式分配（跨库可移植）
 * thonly_activity_revision (id, year, activity_id, action, category, status, name, region,
 *                           date, qq, revision_user, comment, created_at)
 * </pre>
 * 所有写操作都会：① 写一行变更历史（供回滚）② 写一条 {@code edit} 日志（目标
 * 「线下活动速查表」）→ 最近更改里可见。
 * <p>
 * 首次启动（表为空时）从插件资源 {@code /thonly-activity-seed.tsv} 导入线上抓下来的数据。
 */
@Service
public class ActivityStore {

    private static final Logger log = LoggerFactory.getLogger(ActivityStore.class);

    public static final String PAGE_TITLE = ThonlyActivitySchedulePlugin.PAGE_TITLE;
    public static final String TABLE_PREFIX = "thonly_activity_";
    public static final String REVISION_TABLE = "thonly_activity_revision";
    public static final String SEED_RESOURCE = "/thonly-activity-seed.tsv";

    /** 允许的活动分类（与线上四个工作表一致）。 */
    public static final List<String> CATEGORIES = List.of("tho", "thp", "school", "live");

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private PluginDatabase db;

    @Autowired
    private LogWriter logWriter;

    @PostConstruct
    public void bootstrap() {
        try {
            db.update("CREATE TABLE IF NOT EXISTS " + REVISION_TABLE + " ("
                    + "id INTEGER PRIMARY KEY, year INTEGER NOT NULL, activity_id INTEGER, action TEXT NOT NULL,"
                    + "category TEXT, status TEXT, name TEXT, region TEXT, date TEXT, qq TEXT,"
                    + "revision_user TEXT, comment TEXT, created_at TEXT NOT NULL)");
            for (int year : new TreeSet<>(seedYears())) {
                ensureYear(year);
            }
            ensureYear(LocalDateTime.now().getYear());
            importSeedIfEmpty();
        } catch (RuntimeException exception) {
            log.warn("活动速查表初始化失败：{}", exception.getMessage());
        }
    }

    /** 建某年的活动表（不存在才建）。 */
    public void ensureYear(int year) {
        String table = tableOf(year);
        db.update("CREATE TABLE IF NOT EXISTS " + table + " ("
                + "id INTEGER PRIMARY KEY, category TEXT NOT NULL, status TEXT, name TEXT NOT NULL,"
                + "region TEXT, date TEXT, qq TEXT, sort_order INTEGER NOT NULL DEFAULT 0,"
                + "deleted INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL, updated_at TEXT NOT NULL)");
    }

    /** 已经确保存在的「最远年份」：跨年后的第一个请求会再补一次（volatile，不依赖重启）。 */
    private volatile int ensuredThrough = 0;

    /**
     * 按需把数据表年份延伸到「**服务器当前年 + 2**」：读取年份 / 列表、新增与修改入口都会先过一遍；
     * 已覆盖时只是一次整数比较，不产生数据库开销；跨年后由第一个请求自动补上新表。
     */
    public synchronized void ensureUpcomingYears() {
        int currentYear = LocalDateTime.now().getYear();
        if (ensuredThrough >= currentYear + 2) {
            return;
        }
        try {
            for (int year = currentYear; year <= currentYear + 2; year++) {
                ensureYear(year);
            }
            ensuredThrough = currentYear + 2;
            log.debug("活动数据表年份已覆盖到 {}（当前年 {} + 2）", ensuredThrough, currentYear);
        } catch (RuntimeException exception) {
            log.warn("活动数据表年份延伸失败（当前年 {}）：{}", currentYear, exception.getMessage());
        }
    }

    public List<Integer> years() {
        ensureUpcomingYears();
        TreeSet<Integer> years = new TreeSet<>();
        for (DbRow row : db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name LIKE ?",
                TABLE_PREFIX + "%")) {
            String name = row.string("name");
            if (name == null || name.length() != TABLE_PREFIX.length() + 4) {
                continue;
            }
            try {
                years.add(Integer.parseInt(name.substring(TABLE_PREFIX.length())));
            } catch (NumberFormatException ignored) {
                // 非年份表（理论上不会有）忽略
            }
        }
        if (years.isEmpty()) {
            years.add(LocalDateTime.now().getYear());
        }
        return new ArrayList<>(years);
    }

    /**
     * 默认展示年份：优先「服务器当前年」（年份延伸后列表里总是有它），
     * 这样往后的 +1 / +2 空表不会抢先成为各页面的默认年份。
     */
    public int defaultYear() {
        List<Integer> years = years();
        int current = LocalDateTime.now().getYear();
        int target = -1;
        for (int year : years) {
            if (year <= current) {
                target = year;
            }
        }
        return target > 0 ? target : years.get(years.size() - 1);
    }

    /** 某年的活动（按分类 + 顺序，排除已删除）。 */
    public List<Map<String, Object>> list(int year) {
        ensureUpcomingYears();
        ensureYear(year);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DbRow row : db.query("SELECT * FROM " + tableOf(year)
                + " WHERE deleted = 0 ORDER BY category, sort_order, id")) {
            rows.add(toMap(row));
        }
        return rows;
    }

    /** 新增一条活动。 */
    public long add(Viewer viewer, int year, String category, String status, String name,
                    String region, String date, String qq, String comment) {
        ensureUpcomingYears();
        ensureYear(year);
        String categoryValue = normalizeCategory(category);
        String nameValue = requireName(name);
        String now = LocalDateTime.now().format(TIME);
        return db.transaction(() -> {
            long id = nextId(year);
            int order = nextOrder(year, categoryValue);
            db.update("INSERT INTO " + tableOf(year) + " (id, category, status, name, region, date, qq,"
                            + " sort_order, deleted, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
                    id, categoryValue, status, nameValue, region, date, qq, order, now, now);
            revision(viewer, year, id, "insert", categoryValue, status, nameValue, region, date, qq, comment);
            logWrite(viewer, "新增 " + year + " / " + categoryValue + " / " + nameValue);
            return id;
        });
    }

    /** 修改一条活动（整行覆盖）。 */
    public boolean update(Viewer viewer, int year, long id, String category, String status, String name,
                          String region, String date, String qq, String comment) {
        ensureUpcomingYears();
        ensureYear(year);
        String categoryValue = normalizeCategory(category);
        String nameValue = requireName(name);
        String now = LocalDateTime.now().format(TIME);
        return db.transaction(() -> {
            int changed = db.update("UPDATE " + tableOf(year) + " SET category = ?, status = ?, name = ?,"
                            + " region = ?, date = ?, qq = ?, updated_at = ? WHERE id = ? AND deleted = 0",
                    categoryValue, status, nameValue, region, date, qq, now, id);
            if (changed == 0) {
                return false;
            }
            revision(viewer, year, id, "update", categoryValue, status, nameValue, region, date, qq, comment);
            logWrite(viewer, "修改 " + year + " / " + nameValue);
            return true;
        });
    }

    /** 软删除一条活动。 */
    public boolean delete(Viewer viewer, int year, long id, String comment) {
        ensureYear(year);
        String now = LocalDateTime.now().format(TIME);
        return db.transaction(() -> {
            DbRow row = db.queryOne("SELECT * FROM " + tableOf(year) + " WHERE id = ? AND deleted = 0", id)
                    .orElse(null);
            if (row == null) {
                return false;
            }
            db.update("UPDATE " + tableOf(year) + " SET deleted = 1, updated_at = ? WHERE id = ?", now, id);
            revision(viewer, year, id, "delete", row.string("category"), row.string("status"),
                    row.string("name"), row.string("region"), row.string("date"), row.string("qq"), comment);
            logWrite(viewer, "删除 " + year + " / " + row.string("name"));
            return true;
        });
    }

    /** 变更历史（最近的在前）。 */
    public List<Map<String, Object>> revisions(int year, int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DbRow row : db.query("SELECT * FROM " + REVISION_TABLE
                + " WHERE year = ? ORDER BY id DESC LIMIT ?", year, Math.max(1, Math.min(limit, 200)))) {
            rows.add(toMap(row));
        }
        return rows;
    }

    /** 按变更历史回滚一条：把该版本的数据重新写回活动表。 */
    public boolean restore(Viewer viewer, long revisionId, String comment) {
        DbRow revision = db.queryOne("SELECT * FROM " + REVISION_TABLE + " WHERE id = ?", revisionId).orElse(null);
        if (revision == null) {
            return false;
        }
        int year = revision.intValue("year", LocalDateTime.now().getYear());
        long activityId = revision.longValue("activity_id", 0);
        String category = normalizeCategory(revision.string("category"));
        String status = revision.string("status");
        String name = requireName(revision.string("name"));
        String region = revision.string("region");
        String date = revision.string("date");
        String qq = revision.string("qq");
        ensureYear(year);
        String now = LocalDateTime.now().format(TIME);
        return db.transaction(() -> {
            DbRow existing = db.queryOne("SELECT * FROM " + tableOf(year) + " WHERE id = ?", activityId).orElse(null);
            if (existing == null) {
                db.update("INSERT INTO " + tableOf(year) + " (id, category, status, name, region, date, qq,"
                                + " sort_order, deleted, created_at, updated_at)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
                        activityId, category, status, name, region, date, qq,
                        nextOrder(year, category), now, now);
            } else {
                db.update("UPDATE " + tableOf(year) + " SET category = ?, status = ?, name = ?, region = ?,"
                                + " date = ?, qq = ?, deleted = 0, updated_at = ? WHERE id = ?",
                        category, status, name, region, date, qq, now, activityId);
            }
            revision(viewer, year, activityId, "restore", category, status, name, region, date, qq,
                    comment == null || comment.isBlank() ? ("回滚到版本 #" + revisionId) : comment);
            logWrite(viewer, "回滚 " + year + " / " + name + "（版本 #" + revisionId + "）");
            return true;
        });
    }

    // ==================== 内部 ====================

    private void revision(Viewer viewer, int year, long activityId, String action, String category,
                          String status, String name, String region, String date, String qq, String comment) {
        long id = db.queryOne("SELECT COALESCE(MAX(id), 0) AS m FROM " + REVISION_TABLE)
                .map((row) -> row.longValue("m", 0L)).orElse(0L) + 1;
        db.update("INSERT INTO " + REVISION_TABLE + " (id, year, activity_id, action, category, status,"
                        + " name, region, date, qq, revision_user, comment, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, year, activityId, action, category, status, name, region, date, qq,
                viewer == null ? "" : viewer.name(), comment == null ? "" : comment,
                LocalDateTime.now().format(TIME));
    }

    /** 记「最近更改」：复用 core 的 edit 日志类型，目标 = 速查表页面。 */
    private void logWrite(Viewer viewer, String detail) {
        try {
            logWriter.record("edit", viewer == null || viewer.anonymous() ? null : viewer.id(),
                    PAGE_TITLE, detail);
        } catch (RuntimeException exception) {
            log.warn("活动表格日志写入失败：{}", exception.getMessage());
        }
    }

    private long nextId(int year) {
        return db.queryOne("SELECT COALESCE(MAX(id), 0) AS m FROM " + tableOf(year))
                .map((row) -> row.longValue("m", 0L)).orElse(0L) + 1;
    }

    private int nextOrder(int year, String category) {
        return db.queryOne("SELECT COALESCE(MAX(sort_order), -1) AS m FROM " + tableOf(year)
                        + " WHERE category = ?", category)
                .map((row) -> row.intValue("m", -1)).orElse(-1) + 1;
    }

    private String tableOf(int year) {
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("activity.yearInvalid");
        }
        return TABLE_PREFIX + year;
    }

    private static String normalizeCategory(String category) {
        String value = category == null ? "" : category.trim().toLowerCase(java.util.Locale.ROOT);
        return CATEGORIES.contains(value) ? value : "tho";
    }

    private static String requireName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("activity.nameRequired");
        }
        return value.length() > 200 ? value.substring(0, 200) : value;
    }

    private static Map<String, Object> toMap(DbRow row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", row.longValue("id", 0));
        if (row.has("year")) {
            map.put("year", row.intValue("year", 0));
        }
        for (String column : List.of("category", "status", "name", "region", "date", "qq", "action",
                "revision_user", "comment", "created_at", "updated_at")) {
            if (row.has(column)) {
                map.put(column, row.textOrNull(column));
            }
        }
        return map;
    }

    private Set<Integer> seedYears() {
        Set<Integer> years = new TreeSet<>();
        try (InputStream stream = getClass().getResourceAsStream(SEED_RESOURCE)) {
            if (stream == null) {
                return years;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    try {
                        years.add(Integer.parseInt(line.split("\t", 2)[0].trim()));
                    } catch (NumberFormatException ignored) {
                        // 跳过坏行
                    }
                }
            }
        } catch (Exception exception) {
            log.warn("读取活动表格种子数据失败：{}", exception.getMessage());
        }
        return years;
    }

    private void importSeedIfEmpty() {
        try (InputStream stream = getClass().getResourceAsStream(SEED_RESOURCE)) {
            if (stream == null) {
                return;
            }
            String now = LocalDateTime.now().format(TIME);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    String[] parts = line.split("\t", -1);
                    if (parts.length < 6) {
                        continue;
                    }
                    int year;
                    try {
                        year = Integer.parseInt(parts[0].trim());
                    } catch (NumberFormatException exception) {
                        continue;
                    }
                    ensureYear(year);
                    long count = db.queryOne("SELECT COUNT(*) AS c FROM " + tableOf(year))
                            .map((row) -> row.longValue("c", 0L)).orElse(0L);
                    if (count > 0) {
                        continue; // 该年已有数据（或已导入过），不覆盖
                    }
                    break;
                }
            }
        } catch (Exception exception) {
            log.warn("导入活动表格种子数据失败：{}", exception.getMessage());
        }
        // 逐行导入（只在整年为空时）
        try (InputStream stream = getClass().getResourceAsStream(SEED_RESOURCE)) {
            if (stream == null) {
                return;
            }
            String now = LocalDateTime.now().format(TIME);
            Map<Integer, Integer> orders = new LinkedHashMap<>();
            Map<Integer, Long> ids = new LinkedHashMap<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    String[] parts = line.split("\t", -1);
                    if (parts.length < 7) {
                        continue;
                    }
                    int year;
                    try {
                        year = Integer.parseInt(parts[0].trim());
                    } catch (NumberFormatException exception) {
                        continue;
                    }
                    if (!ids.containsKey(year)) {
                        long existing = db.queryOne("SELECT COUNT(*) AS c FROM " + tableOf(year))
                                .map((row) -> row.longValue("c", 0L)).orElse(0L);
                        if (existing > 0) {
                            ids.put(year, -1L); // 标记为「已跳过」
                        } else {
                            ids.put(year, 0L);
                            orders.put(year, 0);
                        }
                    }
                    if (ids.get(year) < 0) {
                        continue;
                    }
                    String category = normalizeCategory(parts[1]);
                    String status = parts[2];
                    String name = parts[3];
                    String region = parts[4];
                    String date = parts[5];
                    String qq = parts[6];
                    if (name.isBlank()) {
                        continue;
                    }
                    long id = ids.get(year) + 1;
                    int order = orders.get(year) == null ? 0 : orders.get(year);
                    db.update("INSERT INTO " + tableOf(year) + " (id, category, status, name, region, date, qq,"
                                    + " sort_order, deleted, created_at, updated_at)"
                                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
                            id, category, status, name, region, date, qq, order, now, now);
                    ids.put(year, id);
                    orders.put(year, order + 1);
                }
            }
        } catch (Exception exception) {
            log.warn("导入活动表格数据失败：{}", exception.getMessage());
        }
    }
}

package cc.thonly.wikikernel.plugin.thonlywikiextensions.service;

import cc.thonly.wikikernel.common.service.DbRow;
import cc.thonly.wikikernel.common.service.PluginDatabase;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

/** Reclassifies old activity edit logs only when their activity revision confirms the operation. */
final class ActivityLegacyLogs {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final PluginDatabase db;

    ActivityLegacyLogs(PluginDatabase db) { this.db = db; }

    int migrate() {
        long cursor = 0;
        int changed = 0;
        while (true) {
            List<DbRow> batch = db.query("SELECT l.id, l.detail, l.time, l.actor_user_id, u.username FROM wiki_log l"
                    + " LEFT JOIN wiki_user u ON u.id = l.actor_user_id"
                    + " WHERE l.log_type = ? AND l.target = ? AND l.id > ? ORDER BY l.id LIMIT 200",
                    "edit", ActivityStore.PAGE_TITLE, cursor);
            if (batch.isEmpty()) return changed;
            changed += db.transaction(() -> migrateBatch(batch));
            cursor = batch.get(batch.size() - 1).longValue("id", cursor);
        }
    }

    private int migrateBatch(List<DbRow> batch) {
        int changed = 0;
        for (DbRow log : batch) {
            var time = log.dateTime("time");
            if (time == null || (!log.isNull("actor_user_id") && log.string("username").isBlank())) continue;
            var revisions = db.query("SELECT * FROM " + ActivityStore.REVISION_TABLE
                            + " WHERE revision_user = ? AND created_at >= ? AND created_at <= ?",
                    log.string("username"), time.minusSeconds(1).format(TIME), time.format(TIME));
            if (revisions.stream().noneMatch(revision -> matches(log.string("detail"), revision))) continue;
            changed += db.update("UPDATE wiki_log SET log_type = ? WHERE id = ? AND log_type = ? AND target = ?",
                    "activity", log.longValue("id", 0), "edit", ActivityStore.PAGE_TITLE);
        }
        return changed;
    }

    private boolean matches(String detail, DbRow revision) {
        String item = revision.intValue("year", 0) + " / " + revision.string("name");
        return switch (revision.string("action")) {
            case "insert" -> detail.equals("新增 " + revision.intValue("year", 0) + " / "
                    + revision.string("category") + " / " + revision.string("name"));
            case "update" -> detail.equals("修改 " + item);
            case "delete" -> detail.equals("删除 " + item);
            case "restore" -> Pattern.matches(Pattern.quote("回滚 " + item + "（版本 #") + "[0-9]+）", detail);
            default -> false;
        };
    }
}

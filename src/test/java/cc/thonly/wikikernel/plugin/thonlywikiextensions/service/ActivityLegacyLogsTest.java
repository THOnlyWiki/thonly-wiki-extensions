package cc.thonly.wikikernel.plugin.thonlywikiextensions.service;

import cc.thonly.wikikernel.common.service.DbRow;
import cc.thonly.wikikernel.common.service.PluginDatabase;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class ActivityLegacyLogsTest {
    @Test void confirmsOldOperationsAndPreservesAllOtherLogFields() throws Exception {
        try (var db = new Database()) {
            db.revision(1, "insert", "活动甲"); db.revision(2, "update", "活动甲");
            db.revision(3, "delete", "活动甲"); db.revision(4, "restore", "活动甲");
            for (var entry : Map.of(1, "新增 2026 / tho / 活动甲", 2, "修改 2026 / 活动甲",
                    3, "删除 2026 / 活动甲", 4, "回滚 2026 / 活动甲（版本 #1）").entrySet()) {
                db.log(entry.getKey(), "edit", ActivityStore.PAGE_TITLE, entry.getValue(), 1,
                        "2026-11-04T22:09:01.300");
            }
            db.log(5, "edit", ActivityStore.PAGE_TITLE, "修正表格页面样式", 1, "2026-11-04T22:09:01.300");
            db.log(6, "edit", "其它词条", "修改 2026 / 活动甲", 1, "2026-11-04T22:09:01.300");
            db.log(7, "edit", ActivityStore.PAGE_TITLE, "修改 2026 / 活动甲", 2, "2026-11-04T22:09:01.300");
            db.log(8, "edit", ActivityStore.PAGE_TITLE, "修改 2026 / 活动甲", 1, "2026-11-05T22:09:01.300");
            db.log(9, "edit", ActivityStore.PAGE_TITLE, "修改 2026 / 活动乙", 1, "2026-11-04T22:09:01.300");
            db.log(10, "activity", ActivityStore.PAGE_TITLE, "新增日志", 1, "2026-11-04T22:09:01.300");
            var before = db.query("SELECT * FROM wiki_log ORDER BY id");
            assertEquals(4, new ActivityLegacyLogs(db).migrate());
            assertEquals(0, new ActivityLegacyLogs(db).migrate());
            var after = db.query("SELECT * FROM wiki_log ORDER BY id");
            assertEquals(before.size(), after.size());
            for (int i = 0; i < before.size(); i++) {
                var expected = new LinkedHashMap<>(before.get(i).values());
                if (i < 4) expected.put("log_type", "activity");
                assertEquals(expected, after.get(i).values());
            }
        }
    }

    @Test void migrationContinuesAcrossBatchesOfUnmatchedAndMatchedLogs() throws Exception {
        try (var db = new Database()) {
            db.revision(1, "update", "活动甲");
            for (int id = 1; id <= 405; id++) db.log(id, "edit", ActivityStore.PAGE_TITLE,
                    id <= 200 ? "普通页面编辑" : "修改 2026 / 活动甲", 1, "2026-11-04T22:09:01.300");
            assertEquals(205, new ActivityLegacyLogs(db).migrate());
            assertEquals(200, db.query("SELECT id FROM wiki_log WHERE log_type = 'edit'").size());
            assertEquals(0, new ActivityLegacyLogs(db).migrate());
        }
    }

    private static final class Database implements PluginDatabase, AutoCloseable {
        private final Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID());
        Database() throws SQLException {
            update("CREATE TABLE wiki_user (id BIGINT PRIMARY KEY, username VARCHAR)");
            update("INSERT INTO wiki_user VALUES (1, '编辑甲'), (2, '编辑乙')");
            update("CREATE TABLE wiki_log (id BIGINT PRIMARY KEY, log_type VARCHAR, target VARCHAR, detail VARCHAR,"
                    + " actor_user_id BIGINT, time TIMESTAMP, hided BOOLEAN, ip VARCHAR, user_agent VARCHAR)");
            update("CREATE TABLE thonly_activity_revision (id BIGINT PRIMARY KEY, \"year\" INT, category VARCHAR,"
                    + " action VARCHAR, name VARCHAR, created_at VARCHAR, revision_user VARCHAR)");
        }
        void revision(int id, String action, String name) {
            update("INSERT INTO thonly_activity_revision VALUES (?, 2026, 'tho', ?, ?, ?, '编辑甲')",
                    id, action, name, "2026-11-04 22:09:00");
        }
        void log(int id, String type, String title, String detail, long actor, String time) {
            update("INSERT INTO wiki_log VALUES (?, ?, ?, ?, ?, ?, TRUE, '127.0.0.1', 'original-agent')",
                    id, type, title, detail, actor, time);
        }
        public String databaseProductName() { return "H2"; }
        public List<String> tableNames() { return List.of(); }
        private PreparedStatement statement(String sql, Object... args) throws SQLException {
            var statement = connection.prepareStatement(sql);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }
        public int update(String sql, Object... args) {
            try (var statement = statement(sql, args)) { return statement.executeUpdate(); }
            catch (SQLException exception) { throw new IllegalStateException(exception); }
        }
        public List<DbRow> query(String sql, Object... args) {
            try (var statement = statement(sql, args); var rows = statement.executeQuery()) {
                var result = new ArrayList<DbRow>(); var metadata = rows.getMetaData();
                while (rows.next()) {
                    var values = new LinkedHashMap<String, Object>();
                    for (int i = 1; i <= metadata.getColumnCount(); i++) values.put(metadata.getColumnLabel(i), rows.getObject(i));
                    result.add(new DbRow(values));
                }
                return result;
            } catch (SQLException exception) { throw new IllegalStateException(exception); }
        }
        public Optional<DbRow> queryOne(String sql, Object... args) { return query(sql, args).stream().findFirst(); }
        public long insert(String sql, Object... args) { throw new UnsupportedOperationException(); }
        public <T> T transaction(Supplier<T> work) {
            try {
                connection.setAutoCommit(false);
                try { T result = work.get(); connection.commit(); return result; }
                catch (RuntimeException exception) { connection.rollback(); throw exception; }
                finally { connection.setAutoCommit(true); }
            } catch (SQLException exception) { throw new IllegalStateException(exception); }
        }
        public void close() throws SQLException { connection.close(); }
    }
}

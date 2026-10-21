package cc.thonly.wikikernel.plugin.thonlywikiextensions.pagegenerator;

import cc.thonly.wikikernel.common.service.DbRow;
import cc.thonly.wikikernel.common.service.PluginDatabase;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 页面生成器的结构化数据仓库。
 * <p>
 * 只保存“由页面生成器创建/更新过的页面”的表单数据与最后一次生成正文：
 * <ul>
 *     <li>{@code payload}：用户可以再次打开表单编辑；</li>
 *     <li>{@code generated_content}：判断页面在生成后是否被手动改过；</li>
 *     <li>{@code revision_id}：最后一次生成后的版本号（排查 / 乐观锁用）。</li>
 * </ul>
 * 页面本身仍然是普通的 wiki 页面，不依赖这里的表才能阅读。
 */
@Service
public class PageGeneratorStore {

    private static final String TABLE = "page_generator_document";

    private final PluginDatabase db;

    public PageGeneratorStore(PluginDatabase db) {
        this.db = db;
    }

    @PostConstruct
    public void initialize() {
        db.update("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + "title VARCHAR(255) PRIMARY KEY,"
                + "kind VARCHAR(16) NOT NULL,"
                + "payload TEXT NOT NULL,"
                + "generated_content TEXT NOT NULL,"
                + "revision_id BIGINT,"
                + "updated_by VARCHAR(255),"
                + "updated_at VARCHAR(32)"
                + ")");
    }

    public Optional<Document> find(String title) {
        return db.queryOne("SELECT * FROM " + TABLE + " WHERE title = ?", title)
                .map(this::toDocument);
    }

    public void save(String title, String kind, String payload, String generatedContent,
                     Long revisionId, String updatedBy) {
        Optional<DbRow> existing = db.queryOne(
                "SELECT title FROM " + TABLE + " WHERE title = ?", title);
        if (existing.isPresent()) {
            db.update("UPDATE " + TABLE
                            + " SET kind = ?, payload = ?, generated_content = ?, revision_id = ?,"
                            + " updated_by = ?, updated_at = ? WHERE title = ?",
                    kind, payload, generatedContent, revisionId, updatedBy,
                    java.time.LocalDateTime.now().toString(), title);
            return;
        }
        db.update("INSERT INTO " + TABLE
                        + " (title, kind, payload, generated_content, revision_id, updated_by, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                title, kind, payload, generatedContent, revisionId, updatedBy,
                java.time.LocalDateTime.now().toString());
    }

    private Document toDocument(DbRow row) {
        return new Document(
                row.string("title"),
                row.string("kind"),
                row.string("payload"),
                row.string("generated_content"),
                row.longOrNull("revision_id"));
    }

    /** 一条页面生成器文档。 */
    public record Document(String title, String kind, String payload, String generatedContent,
                           Long revisionId) {
    }
}

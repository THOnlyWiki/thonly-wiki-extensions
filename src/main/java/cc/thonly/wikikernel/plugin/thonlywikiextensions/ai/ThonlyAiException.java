package cc.thonly.wikikernel.plugin.thonlywikiextensions.ai;

/** AI 整理过程中可直接展示给管理员的错误。 */
public class ThonlyAiException extends RuntimeException {

    public ThonlyAiException(String message) {
        super(message);
    }

    public ThonlyAiException(String message, Throwable cause) {
        super(message, cause);
    }
}

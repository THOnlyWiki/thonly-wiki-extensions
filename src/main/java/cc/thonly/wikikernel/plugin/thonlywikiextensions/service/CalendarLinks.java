package cc.thonly.wikikernel.plugin.thonlywikiextensions.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 活动 → 日历快捷方式（手机/电脑通用）：
 * <ul>
 *     <li>{@link #icsUrl}：本站的 .ics 下载（全天事件，Outlook / Apple 日历 / Android 都认）。</li>
 * </ul>
 * 日期列格式与线上表格一致（{@code 2025/2/8}，也兼容 {@code 2025-02-08}）。
 */
public final class CalendarLinks {

    private static final DateTimeFormatter ICS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private CalendarLinks() {
    }

    /** 解析活动日期；解析不出来返回 null（页面上就不显示日历按钮）。 */
    public static LocalDate parseDate(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim().replace('-', '/').replace('.', '/');
        String[] parts = text.split("/");
        if (parts.length < 3) {
            return null;
        }
        try {
            return LocalDate.of(Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** 本站 .ics 下载地址（找不到日期时返回 null）。 */
    public static String icsUrl(int year, long id, String date) {
        return parseDate(date) == null ? null : "/excel/calendar/" + year + "/" + id + ".ics";
    }

    /** 生成 .ics 文本（全天事件）。 */
    public static String ics(String name, String region, String date, String qq, String status) {
        LocalDate start = parseDate(date);
        if (start == null) {
            return null;
        }
        LocalDate end = start.plusDays(1);
        String title = name == null || name.isBlank() ? "东方Project线下活动" : name;
        StringBuilder description = new StringBuilder();
        if (status != null && !status.isBlank()) {
            description.append("状态：").append(status).append("\\n");
        }
        if (region != null && !region.isBlank()) {
            description.append("地区：").append(region).append("\\n");
        }
        if (qq != null && !qq.isBlank()) {
            description.append("QQ群：").append(qq).append("\\n");
        }
        description.append("来源：东方Project线下活动速查表");
        return "BEGIN:VCALENDAR\r\n"
                + "VERSION:2.0\r\n"
                + "PRODID:-//WikiKernel//thonly-wiki-extensions//CN\r\n"
                + "CALSCALE:GREGORIAN\r\n"
                + "BEGIN:VEVENT\r\n"
                + "UID:thonly-activity-" + start.format(ICS_DATE) + "-" + Math.abs(title.hashCode()) + "@wikikernel\r\n"
                + "DTSTAMP:" + LocalDate.now().format(ICS_DATE) + "T000000Z\r\n"
                + "DTSTART;VALUE=DATE:" + start.format(ICS_DATE) + "\r\n"
                + "DTEND;VALUE=DATE:" + end.format(ICS_DATE) + "\r\n"
                + "SUMMARY:" + escape(title) + "\r\n"
                + "LOCATION:" + escape(region == null ? "" : region) + "\r\n"
                + "DESCRIPTION:" + escape(description.toString()) + "\r\n"
                + "END:VEVENT\r\n"
                + "END:VCALENDAR\r\n";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace(";", "\\;")
                .replace(",", "\\,").replace("\n", "\\n");
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 供页面渲染：一行活动的完整元信息（链接 + 日期）。 */
    public static List<String> columns() {
        return List.of("status", "name", "region", "date", "qq");
    }
}

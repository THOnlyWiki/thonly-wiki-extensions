package cc.thonly.wikikernel.plugin.thonlywikiextensions.hook;

import java.util.List;
import java.util.Locale;

/** 查询选项归并旧国家名称；编辑器仍保留原值，由维护者主动选择是否更改。 */
public final class ActivityRegions {
    public static final List<String> COUNTRIES = List.of(
            "日本国", "大韩民国", "越南社会主义共和国", "新加坡共和国", "泰王国");

    /** GB/T 2260 省级代码升序，香港（81）和澳门（82）相邻。 */
    public static final List<String> PROVINCES = List.of(
            "北京市", "天津市", "河北省", "山西省", "内蒙古自治区",
            "辽宁省", "吉林省", "黑龙江省", "上海市", "江苏省",
            "浙江省", "安徽省", "福建省", "江西省", "山东省",
            "河南省", "湖北省", "湖南省", "广东省", "广西壮族自治区",
            "海南省", "重庆市", "四川省", "贵州省", "云南省",
            "西藏自治区", "陕西省", "甘肃省", "青海省", "宁夏回族自治区",
            "新疆维吾尔自治区", "台湾省", "香港特别行政区", "澳门特别行政区");

    public static String group(String value) {
        if (COUNTRIES.contains(value)) { return "国家"; }
        if ("香港特别行政区".equals(value) || "澳门特别行政区".equals(value)) { return "香港、澳门"; }
        return PROVINCES.contains(value) ? "省级地区" : "其他地区";
    }

    /** 国内按行政区划代码，其他名称按拼音 / 字母，五个国家固定置底。 */
    public static List<String> ordered(java.util.Collection<String> values) {
        java.text.Collator alphabet = java.text.Collator.getInstance(Locale.CHINA);
        return values.stream().sorted((a, b) -> {
            int ra = PROVINCES.indexOf(a), rb = PROVINCES.indexOf(b);
            if (ra >= 0 || rb >= 0) { return ra >= 0 && rb >= 0 ? Integer.compare(ra, rb) : ra >= 0 ? -1 : 1; }
            int ca = COUNTRIES.indexOf(a), cb = COUNTRIES.indexOf(b);
            if (ca >= 0 || cb >= 0) { return ca >= 0 && cb >= 0 ? Integer.compare(ca, cb) : ca >= 0 ? 1 : -1; }
            int result = alphabet.compare(a, b);
            return result != 0 ? result : a.compareTo(b);
        }).toList();
    }

    private ActivityRegions() {}

    public static String canonicalize(String value) {
        String text = value == null ? "" : value.trim();
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "日本", "japan", "日本(japan)" -> COUNTRIES.get(0);
            case "韩国", "korea", "south korea", "韩国(korea)" -> COUNTRIES.get(1);
            case "越南", "vietnam" -> COUNTRIES.get(2);
            case "新加坡", "singapore" -> COUNTRIES.get(3);
            case "泰国", "thailand" -> COUNTRIES.get(4);
            default -> text;
        };
    }
}

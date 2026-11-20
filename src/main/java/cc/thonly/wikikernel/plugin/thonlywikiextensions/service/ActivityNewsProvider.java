package cc.thonly.wikikernel.plugin.thonlywikiextensions.service;

import cc.thonly.wikikernel.common.extension.ActivityProvider;
import cc.thonly.wikikernel.common.web.WikiUrls;
import java.util.List;
import java.util.Map;

public class ActivityNewsProvider implements ActivityProvider {
    private final ActivityStore store;
    public ActivityNewsProvider(ActivityStore store) { this.store = store; }
    public List<String> regions() {
        var regions = new java.util.LinkedHashSet<>(cc.thonly.wikikernel.plugin.thonlywikiextensions.hook.ActivityRegions.PROVINCES);
        regions.add("海外"); regions.add("多地区");
        regions.addAll(cc.thonly.wikikernel.plugin.thonlywikiextensions.hook.ActivityRegions.COUNTRIES);
        return List.copyOf(regions);
    }
    public List<Activity> activities(int year) {
        return store.list(year).stream().map(row -> {
            long id = ((Number) row.get("id")).longValue();
            String revision = text(row, "name") + "|" + text(row, "date") + "|" + text(row, "region") + "|" + text(row, "status");
            return new Activity(year, id, text(row, "name"), text(row, "region"), text(row, "date"), text(row, "status"),
                    WikiUrls.pagePath(ActivityStore.PAGE_TITLE) + "?year=" + year + "&id=" + id,
                    "/excel/calendar/" + year + "/" + id + ".ics", revision);
        }).toList();
    }
    private static String text(Map<String, Object> row, String key) { return String.valueOf(row.getOrDefault(key, "")); }
}

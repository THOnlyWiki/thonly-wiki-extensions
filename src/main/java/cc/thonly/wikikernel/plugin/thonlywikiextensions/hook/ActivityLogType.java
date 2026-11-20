package cc.thonly.wikikernel.plugin.thonlywikiextensions.hook;

import cc.thonly.wikikernel.common.extension.LogType;
import cc.thonly.wikikernel.common.plugin.support.ResourceMessageBundle;

public final class ActivityLogType extends ResourceMessageBundle implements LogType {
    public String id() { return "activity"; }
    public boolean inRecentChanges() { return true; }
    public TargetKind targetKind() { return TargetKind.PAGE; }
}

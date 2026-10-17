/*
 * 「活动日历」（evoCalendar）前端：三种用法共用
 *   1) 解析标签 <touhou-calendar />（任何页面）；
 *   2) 「活动日历」页面正文接管（CalendarPageHook，无需写标签）；
 *   3) 独立页 /calendar（CalendarStandaloneController）。
 *
 * 数据来自本站活动表（读接口 /api/thonly-activity/years + /list，不再依赖 Google 表格）：
 *  - 日期：表内写法 yyyy/M/d → ISO yyyy-mm-dd 后交给 evoCalendar（format: yyyy-mm-dd）；
 *  - 已取消的活动：灰色圆点 + 说明里注明「活动已取消」；
 *  - 正常活动：站点主题色圆点，说明里带 地区 / 状态 / 活动群群号。
 */
(function () {
    'use strict';

    var containers = Array.prototype.slice.call(document.querySelectorAll('[data-touhou-calendar]'));
    if (!containers.length) {
        return;
    }
    var jq = window.jQuery;
    if (!jq || !jq.fn || !jq.fn.evoCalendar) {
        return;
    }

    var NORMAL_COLOR = '#d8638c';
    var CANCELLED_COLOR = '#b0b0b0';

    function pad(value) {
        return ('0' + value).slice(-2);
    }

    /** 表内日期写法 yyyy/M/d → ISO yyyy-mm-dd（非法日期返回空串，例如 2024/8/45）。 */
    function toIsoDate(value) {
        var match = /^(\d{4})\/(\d{1,2})\/(\d{1,2})$/.exec(String(value || '').trim());
        if (!match) {
            return '';
        }
        var iso = match[1] + '-' + pad(match[2]) + '-' + pad(match[3]);
        var probe = new Date(iso + 'T00:00:00');
        var valid = probe.getFullYear() === +match[1]
                && (probe.getMonth() + 1) === +match[2]
                && probe.getDate() === +match[3];
        return valid ? iso : '';
    }

    /** 一条活动记录 → evoCalendar 事件（日期非法时返回 null，直接丢弃）。 */
    function eventFromRow(row, year) {
        var date = toIsoDate(row.date);
        if (!date) {
            return null;
        }
        var cancelled = String(row.status || '').indexOf('取消') >= 0;
        var parts = [];
        if (row.region) {
            parts.push('地区：' + row.region);
        }
        if (row.status) {
            parts.push('状态：' + row.status);
        }
        if (row.qq) {
            parts.push('活动群群号：' + row.qq);
        }
        return {
            id: 'activity-' + year + '-' + row.id,
            name: row.name || '（未命名活动）',
            date: date,
            description: (cancelled ? '活动已取消。' : '') + parts.join(' · '),
            type: 'event',
            color: cancelled ? CANCELLED_COLOR : NORMAL_COLOR
        };
    }

    function setPlaceholder(text) {
        containers.forEach(function (container) {
            container.textContent = text;
            container.classList.add('thonly-calendar-loading');
        });
    }

    function initCalendar(events) {
        containers.forEach(function (container) {
            container.classList.remove('thonly-calendar-loading');
            container.textContent = '';
            updateCompactLayout(container);
            jq(container).evoCalendar({
                theme: null,
                format: 'yyyy-mm-dd',
                titleFormat: 'MM yyyy',
                eventHeaderFormat: 'MM d, yyyy',
                firstDayOfWeek: 0,
                language: 'zh',
                todayHighlight: true,
                sidebarDisplayDefault: true,
                sidebarToggler: true,
                eventDisplayDefault: true,
                eventListToggler: true,
                calendarEvents: events
            });
            updateCompactLayout(container);
            if (window.ResizeObserver) {
                new ResizeObserver(function () {
                    updateCompactLayout(container);
                }).observe(container);
            }
        });
    }

    /** 容器窄（首页 mini-block 等）时用紧凑布局：时间/视口断点管不到卡片宽度，必须看容器自身。 */
    function updateCompactLayout(container) {
        container.classList.toggle('calendar-compact', container.getBoundingClientRect().width < 760);
    }

    function loadYear(year) {
        return fetch('/api/thonly-activity/list?year=' + encodeURIComponent(year), {
            headers: { 'Accept': 'application/json' },
            credentials: 'same-origin'
        }).then(function (response) {
            return response.ok ? response.json() : {};
        }).then(function (data) {
            var rows = (data && data.rows) || [];
            return rows.map(function (row) {
                return eventFromRow(row, year);
            }).filter(Boolean);
        }).catch(function () {
            return [];
        });
    }

    setPlaceholder('日历加载中…');

    fetch('/api/thonly-activity/years', {
        headers: { 'Accept': 'application/json' },
        credentials: 'same-origin'
    }).then(function (response) {
        if (!response.ok) {
            throw new Error('HTTP ' + response.status);
        }
        return response.json();
    }).then(function (data) {
        var years = (data && data.years) || [];
        return Promise.all(years.map(loadYear));
    }).then(function (groups) {
        var events = [];
        groups.forEach(function (group) {
            events = events.concat(group);
        });
        initCalendar(events);
    }).catch(function () {
        setPlaceholder('日历加载失败，请刷新重试。');
    });
})();

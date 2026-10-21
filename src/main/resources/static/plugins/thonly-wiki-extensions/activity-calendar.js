/*
 * 「活动日历」前端（纯原生 JS，无第三方日历库）：三种用法共用
 *   1) 解析标签 <touhou-calendar />（任何页面）；
 *   2) 「活动日历」页面正文接管（CalendarPageHook，无需写标签）；
 *   3) 独立页 /calendar（CalendarStandaloneController）。
 *
 * 数据来自本站活动表：GET /api/thonly-activity/years + /list?year=。
 * 设计目标：
 *  - 中文月份 / 星期 / 日期，不再出现 evoCalendar 的英文标题；
 *  - 顶部直接切换年、月、上个月、下个月和回到今天；
 *  - 日期格里直接显示活动名（窄容器退化为圆点），点击日期在右侧看详情；
 *  - 没有活动的日期给出近期活动，避免只看到「今天没有事件」；
 *  - 同一个页面有多个日历容器时共用一次数据加载。
 */
(function () {
    'use strict';

    var containers = Array.prototype.slice.call(document.querySelectorAll('[data-touhou-calendar]'));
    if (!containers.length) {
        return;
    }

    var WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
    var MONTHS = ['1月', '2月', '3月', '4月', '5月', '6月',
        '7月', '8月', '9月', '10月', '11月', '12月'];
    var CATEGORY_LABELS = {
        tho: 'THO',
        thp: 'THP',
        school: '校联',
        live: 'Live',
        other: '活动'
    };
    /** 活动状态：与现有数据保持一致；未知旧值会作为额外选项保留。 */
    var STATUS_OPTIONS = ['', '计划', '定档', '如期', '延期', '取消'];
    var API_BASE = '/api/thonly-activity';
    var API_YEARS = API_BASE + '/years';
    var API_LIST = API_BASE + '/list?year=';

    var state = {
        years: [],
        events: [],
        byDate: Object.create(null),
        loaded: false,
        loading: false,
        canEdit: false
    };

    function pad(value) {
        return ('0' + value).slice(-2);
    }

    function text(value) {
        return String(value == null ? '' : value).trim();
    }

    function escapeHtml(value) {
        return String(value == null ? '' : value).replace(/[&<>"']/g, function (character) {
            return {
                '&': '&amp;',
                '<': '&lt;',
                '>': '&gt;',
                '"': '&quot;',
                "'": '&#39;'
            }[character];
        });
    }

    /** 支持 yyyy/M/d、yyyy-M-d（活动表里两种都有），非法日期返回空串。 */
    function toIsoDate(value) {
        var match = /^(\d{4})[\/\-](\d{1,2})[\/\-](\d{1,2})$/.exec(text(value));
        if (!match) {
            return '';
        }
        var year = +match[1];
        var month = +match[2];
        var day = +match[3];
        var probe = new Date(year, month - 1, day);
        if (probe.getFullYear() !== year || probe.getMonth() !== month - 1 || probe.getDate() !== day) {
            return '';
        }
        return year + '-' + pad(month) + '-' + pad(day);
    }

    function dateFromIso(iso) {
        var parts = iso.split('-');
        return new Date(+parts[0], +parts[1] - 1, +parts[2]);
    }

    /** HTML date 的 yyyy-mm-dd → 活动表里的 yyyy/M/d（和 /excel 编辑器一致）。 */
    function tableDate(iso) {
        var parts = iso.split('-');
        return (+parts[0]) + '/' + (+parts[1]) + '/' + (+parts[2]);
    }

    function isoOf(date) {
        return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
    }

    function todayIso() {
        return isoOf(new Date());
    }

    function monthTitle(year, month) {
        return year + '年' + MONTHS[month];
    }

    function dateTitle(iso) {
        var date = dateFromIso(iso);
        return (date.getMonth() + 1) + '月' + date.getDate() + '日 ' + WEEKDAYS[date.getDay()];
    }

    function shortDate(iso) {
        var date = dateFromIso(iso);
        return (date.getMonth() + 1) + '/' + date.getDate();
    }

    function categoryKey(value) {
        var key = text(value).toLowerCase();
        return Object.prototype.hasOwnProperty.call(CATEGORY_LABELS, key) ? key : 'other';
    }

    function normalizeEvent(row) {
        var date = toIsoDate(row.date);
        if (!date) {
            return null;
        }
        var status = text(row.status);
        var id = text(row.id);
        var year = text(row.year);
        return {
            id: id,
            uid: year + '-' + id,
            year: year,
            date: date,
            name: text(row.name) || '（未命名活动）',
            region: text(row.region),
            status: status,
            qq: text(row.qq),
            category: categoryKey(row.category),
            categoryLabel: CATEGORY_LABELS[categoryKey(row.category)],
            icsUrl: text(row.icsUrl),
            cancelled: status.indexOf('取消') >= 0
        };
    }

    function eventKey(event) {
        return event.id + '|' + event.date + '|' + event.name;
    }

    function fetchJson(url) {
        return fetch(url, {
            headers: { 'Accept': 'application/json' },
            credentials: 'same-origin'
        }).then(function (response) {
            if (!response.ok) {
                throw new Error('HTTP ' + response.status);
            }
            return response.json();
        });
    }

    function setLoading() {
        state.loaded = false;
        state.loading = true;
        containers.forEach(function (container) {
            container.__thc = null;
            container.classList.remove('thc-compact', 'thc-narrow');
            container.classList.add('thc-loading-state');
            container.textContent = '日历加载中…';
        });
    }

    function setError() {
        state.loading = false;
        containers.forEach(function (container) {
            container.__thc = null;
            container.classList.remove('thc-loading-state', 'thc-compact', 'thc-narrow');
            container.classList.add('thc-error-state');
            container.innerHTML = '<div class="thc-error">'
                    + '<p>日历加载失败，请刷新页面或稍后重试。</p>'
                    + '<button type="button" data-thc-retry>重新加载</button>'
                    + '</div>';
        });
    }

    function load() {
        if (state.loading) {
            return;
        }
        setLoading();
        fetchJson(API_YEARS).then(function (data) {
            // 编辑入口同时要求「已登录」与「edit 权限」；匿名访问永远不渲染编辑按钮。
            state.canEdit = !!(data && data.canEdit && data.loggedIn);
            var years = (data && Array.isArray(data.years) ? data.years : [])
                    .map(Number)
                    .filter(function (year) { return isFinite(year); });
            if (!years.length) {
                years = [new Date().getFullYear()];
            }
            years = Array.from(new Set(years)).sort(function (a, b) { return a - b; });
            state.years = years;
            return Promise.all(years.map(function (year) {
                return fetchJson(API_LIST + encodeURIComponent(year));
            }));
        }).then(function (yearData) {
            var seen = Object.create(null);
            var events = [];
            yearData.forEach(function (data) {
                var rows = data && Array.isArray(data.rows) ? data.rows : [];
                rows.forEach(function (row) {
                    var event = normalizeEvent(row);
                    if (!event) {
                        return;
                    }
                    var key = eventKey(event);
                    if (seen[key]) {
                        return;
                    }
                    seen[key] = true;
                    events.push(event);
                });
            });
            events.sort(function (a, b) {
                if (a.date === b.date) {
                    return a.name.localeCompare(b.name, 'zh-Hans-CN');
                }
                return a.date < b.date ? -1 : 1;
            });
            state.events = events;
            state.byDate = Object.create(null);
            events.forEach(function (event) {
                (state.byDate[event.date] || (state.byDate[event.date] = [])).push(event);
            });
            state.loaded = true;
            state.loading = false;
            containers.forEach(mount);
        }).catch(function () {
            setError();
        });
    }

    function shellHtml() {
        var weekdays = WEEKDAYS.map(function (label) {
            return '<div class="thc-weekday">' + label + '</div>';
        }).join('');
        return '<div class="thc">'
                + '<div class="thc-toolbar">'
                + '<div class="thc-nav">'
                + '<button type="button" class="thc-nav-btn" data-thc-prev aria-label="上个月">‹</button>'
                + '<h2 class="thc-title"></h2>'
                + '<button type="button" class="thc-nav-btn" data-thc-next aria-label="下个月">›</button>'
                + '</div>'
                + '<div class="thc-controls">'
                + '<select class="thc-select" data-thc-year aria-label="年份"></select>'
                + '<select class="thc-select" data-thc-month aria-label="月份"></select>'
                + '<button type="button" class="thc-today" data-thc-today>今天</button>'
                + '</div>'
                + '</div>'
                + '<div class="thc-layout">'
                + '<div class="thc-calendar">'
                + '<div class="thc-weekdays">' + weekdays + '</div>'
                + '<div class="thc-grid" role="grid" aria-label="活动日历"></div>'
                + '</div>'
                + '<aside class="thc-side" aria-live="polite"></aside>'
                + '</div>'
                + '</div>';
    }

    function mount(container) {
        if (!state.loaded) {
            return;
        }
        container.classList.remove('thc-loading-state', 'thc-error-state');
        container.innerHTML = shellHtml();

        var now = new Date();
        var control = {
            root: container,
            viewYear: now.getFullYear(),
            viewMonth: now.getMonth(),
            selectedIso: todayIso(),
            title: container.querySelector('.thc-title'),
            yearSelect: container.querySelector('[data-thc-year]'),
            monthSelect: container.querySelector('[data-thc-month]'),
            grid: container.querySelector('.thc-grid'),
            side: container.querySelector('.thc-side')
        };
        container.__thc = control;
        render(control);
        observeWidth(control);
    }

    function observeWidth(control) {
        function update() {
            var width = control.root.getBoundingClientRect().width;
            control.root.classList.toggle('thc-compact', width > 0 && width < 620);
            control.root.classList.toggle('thc-narrow', width > 0 && width < 860);
        }
        update();
        if (window.ResizeObserver) {
            control.resizeObserver = new ResizeObserver(update);
            control.resizeObserver.observe(control.root);
        }
    }

    function renderYearOptions(control) {
        var years = state.years.slice();
        if (years.indexOf(control.viewYear) < 0) {
            years.push(control.viewYear);
        }
        years.sort(function (a, b) { return a - b; });
        control.yearSelect.innerHTML = years.map(function (year) {
            return '<option value="' + year + '">' + year + '年</option>';
        }).join('');
        control.yearSelect.value = String(control.viewYear);
    }

    function renderMonthOptions(control) {
        if (control.monthSelect.options.length !== 12) {
            control.monthSelect.innerHTML = MONTHS.map(function (label, index) {
                return '<option value="' + index + '">' + label + '</option>';
            }).join('');
        }
        control.monthSelect.value = String(control.viewMonth);
    }

    function chipHtml(event) {
        var title = event.name;
        if (event.region) {
            title += ' · ' + event.region;
        }
        if (event.status) {
            title += ' · ' + event.status;
        }
        return '<span class="thc-chip thc-cat-' + event.category
                + (event.cancelled ? ' thc-cancelled' : '')
                + '" title="' + escapeHtml(title) + '">'
                + escapeHtml(event.name)
                + '</span>';
    }

    function dayHtml(control, dateIso, dayNumber) {
        var events = state.byDate[dateIso] || [];
        var classes = ['thc-cell', 'thc-day'];
        var date = dateFromIso(dateIso);
        if (date.getDay() === 0 || date.getDay() === 6) {
            classes.push('thc-weekend');
        }
        if (dateIso === todayIso()) {
            classes.push('thc-today');
        }
        if (dateIso === control.selectedIso) {
            classes.push('thc-selected');
        }
        if (events.length) {
            classes.push('thc-has-events');
        }
        var chips = events.slice(0, 2).map(chipHtml).join('');
        if (events.length > 2) {
            chips += '<span class="thc-more">+' + (events.length - 2) + '</span>';
        }
        var dots = events.slice(0, 5).map(function (event) {
            return '<i class="thc-dot thc-cat-' + event.category + '"></i>';
        }).join('');
        var label = (date.getMonth() + 1) + '月' + date.getDate() + '日 '
                + WEEKDAYS[date.getDay()]
                + (events.length ? '，' + events.length + ' 场活动' : '');
        return '<button type="button" class="' + classes.join(' ') + '"'
                + ' data-thc-date="' + dateIso + '"'
                + ' aria-pressed="' + (dateIso === control.selectedIso) + '"'
                + ' aria-label="' + escapeHtml(label) + '"'
                + (state.canEdit ? ' title="点击查看当天活动；右键编辑"' : '') + '>'
                + '<span class="thc-daynum">' + dayNumber + '</span>'
                + '<span class="thc-daychips">' + chips + '</span>'
                + '<span class="thc-dots">' + dots + '</span>'
                + '</button>';
    }

    function eventCardHtml(event) {
        var meta = '<span class="thc-badge thc-cat-' + event.category + '">'
                + escapeHtml(event.categoryLabel) + '</span>';
        if (event.region) {
            meta += '<span>' + escapeHtml(event.region) + '</span>';
        }
        if (event.status) {
            meta += '<span class="thc-status' + (event.cancelled ? ' thc-status-cancelled' : '') + '">'
                    + escapeHtml(event.status) + '</span>';
        }
        var qq = event.qq && event.qq !== '暂无'
                ? '<div class="thc-event-qq">活动群：' + escapeHtml(event.qq) + '</div>'
                : '';
        var actions = [];
        if (state.canEdit) {
            actions.push('<button type="button" class="thc-event-edit" data-thc-edit-uid="'
                    + escapeHtml(event.uid) + '">编辑</button>');
        }
        if (event.icsUrl) {
            actions.push('<a class="thc-event-link" href="' + escapeHtml(event.icsUrl)
                    + '">加入日历</a>');
        }
        var actionsHtml = actions.length
                ? '<div class="thc-event-actions">' + actions.join('') + '</div>'
                : '';
        return '<article class="thc-event thc-cat-' + event.category
                + (event.cancelled ? ' thc-cancelled-event' : '') + '"'
                + ' data-thc-uid="' + escapeHtml(event.uid) + '">'
                + '<div class="thc-event-title">' + escapeHtml(event.name) + '</div>'
                + '<div class="thc-event-meta">' + meta + '</div>'
                + qq
                + actionsHtml
                + '</article>';
    }

    function upcomingItemHtml(event) {
        return '<button type="button" class="thc-upcoming-item" data-thc-jump="' + event.date + '">'
                + '<span class="thc-upcoming-date">' + shortDate(event.date) + '</span>'
                + '<span class="thc-upcoming-name">' + escapeHtml(event.name) + '</span>'
                + '</button>';
    }

    function nextEvents(control, limit) {
        var from = control.selectedIso > todayIso() ? control.selectedIso : todayIso();
        return state.events.filter(function (event) {
            return event.date >= from && event.date !== control.selectedIso;
        }).slice(0, limit);
    }

    function renderSide(control) {
        var events = state.byDate[control.selectedIso] || [];
        var isToday = control.selectedIso === todayIso();
        var html = '<div class="thc-side-head">'
                + '<div class="thc-side-date">' + dateTitle(control.selectedIso)
                + (isToday ? '<span class="thc-today-badge">今天</span>' : '')
                + '</div>'
                + '<div class="thc-side-count">'
                + (events.length ? '共 ' + events.length + ' 场活动' : '没有安排活动')
                + '</div>'
                + '</div>';
        if (events.length) {
            html += events.map(eventCardHtml).join('');
        } else {
            html += '<div class="thc-empty">'
                    + '<strong>这一天没有活动</strong>'
                    + '<span>换一个日期看看，或者看看接下来的活动。</span>'
                    + '</div>';
            var upcoming = nextEvents(control, 5);
            if (upcoming.length) {
                html += '<div class="thc-upcoming"><h3>近期活动</h3>'
                        + upcoming.map(upcomingItemHtml).join('')
                        + '</div>';
            }
        }
        control.side.innerHTML = html;
    }

    function render(control) {
        control.title.textContent = monthTitle(control.viewYear, control.viewMonth);
        renderYearOptions(control);
        renderMonthOptions(control);

        var first = new Date(control.viewYear, control.viewMonth, 1);
        var start = first.getDay();
        var days = new Date(control.viewYear, control.viewMonth + 1, 0).getDate();
        // 只渲染实际需要的行数（通常是 5 行，避免底部多出一整行空白）。
        var totalCells = Math.ceil((start + days) / 7) * 7;
        var cells = [];
        for (var index = 0; index < totalCells; index++) {
            var dayNumber = index - start + 1;
            if (dayNumber < 1 || dayNumber > days) {
                cells.push('<div class="thc-cell thc-cell-empty" aria-hidden="true"></div>');
                continue;
            }
            var dateIso = isoOf(new Date(control.viewYear, control.viewMonth, dayNumber));
            cells.push(dayHtml(control, dateIso, dayNumber));
        }
        control.grid.innerHTML = cells.join('');
        renderSide(control);
    }

    function firstEventIsoInMonth(year, month) {
        var prefix = year + '-' + pad(month + 1) + '-';
        for (var index = 0; index < state.events.length; index++) {
            if (state.events[index].date.indexOf(prefix) === 0) {
                return state.events[index].date;
            }
        }
        return '';
    }

    function setMonth(control, year, month) {
        control.viewYear = year;
        control.viewMonth = month;
        var selected = firstEventIsoInMonth(year, month);
        control.selectedIso = selected || isoOf(new Date(year, month, 1));
        render(control);
    }

    function shiftMonth(control, delta) {
        var date = new Date(control.viewYear, control.viewMonth + delta, 1);
        setMonth(control, date.getFullYear(), date.getMonth());
    }

    function goToday(control) {
        var now = new Date();
        control.viewYear = now.getFullYear();
        control.viewMonth = now.getMonth();
        control.selectedIso = todayIso();
        render(control);
    }

    function jumpToDate(control, iso) {
        var date = dateFromIso(iso);
        control.viewYear = date.getFullYear();
        control.viewMonth = date.getMonth();
        control.selectedIso = iso;
        render(control);
    }

    // ==================== 编辑：右键菜单 + 弹窗 ====================

    var contextMenu = null;
    var modalOverlay = null;
    var toastTimer = null;

    function findEvent(uid) {
        for (var index = 0; index < state.events.length; index++) {
            if (state.events[index].uid === uid) {
                return state.events[index];
            }
        }
        return null;
    }

    function rebuildByDate() {
        state.events.sort(function (a, b) {
            if (a.date === b.date) {
                return a.name.localeCompare(b.name, 'zh-Hans-CN');
            }
            return a.date < b.date ? -1 : 1;
        });
        state.byDate = Object.create(null);
        state.events.forEach(function (activity) {
            (state.byDate[activity.date] || (state.byDate[activity.date] = [])).push(activity);
        });
    }

    function renderAllAt(iso) {
        containers.forEach(function (container) {
            var control = container.__thc;
            if (!control) {
                return;
            }
            var date = dateFromIso(iso);
            control.viewYear = date.getFullYear();
            control.viewMonth = date.getMonth();
            control.selectedIso = iso;
            render(control);
        });
    }

    function closeMenu() {
        if (contextMenu) {
            contextMenu.remove();
            contextMenu = null;
        }
    }

    function showDayMenu(control, iso, x, y) {
        if (!state.canEdit) {
            return;
        }
        var events = state.byDate[iso] || [];
        closeMenu();
        contextMenu = document.createElement('div');
        contextMenu.className = 'thc-menu';
        contextMenu.setAttribute('role', 'menu');
        contextMenu.innerHTML = '<div class="thc-menu-title">' + escapeHtml(dateTitle(iso))
                + ' · ' + events.length + ' 场活动</div>'
                + events.map(function (activity) {
                    return '<button type="button" class="thc-menu-item'
                            + (activity.cancelled ? ' is-cancelled' : '') + '" role="menuitem"'
                            + ' data-thc-edit-uid="' + escapeHtml(activity.uid) + '">编辑：'
                            + escapeHtml(activity.name) + '</button>';
                }).join('')
                + '<button type="button" class="thc-menu-item thc-menu-add" role="menuitem"'
                + ' data-thc-add-activity>＋ 添加活动</button>';
        document.body.appendChild(contextMenu);
        var rect = contextMenu.getBoundingClientRect();
        contextMenu.style.left = Math.max(8, Math.min(x, window.innerWidth - rect.width - 8)) + 'px';
        contextMenu.style.top = Math.max(8, Math.min(y, window.innerHeight - rect.height - 8)) + 'px';
        Array.prototype.forEach.call(contextMenu.querySelectorAll('[data-thc-edit-uid]'), function (button) {
            button.addEventListener('click', function () {
                var activity = findEvent(button.getAttribute('data-thc-edit-uid'));
                closeMenu();
                if (activity) {
                    openEditModal(activity);
                }
            });
        });
        var addButton = contextMenu.querySelector('[data-thc-add-activity]');
        if (addButton) {
            addButton.addEventListener('click', function () {
                closeMenu();
                openEditModal(null, iso);
            });
        }
        var first = contextMenu.querySelector('[data-thc-edit-uid], [data-thc-add-activity]');
        if (first) {
            first.focus({ preventScroll: true });
        }
    }

    function categoryOptions(selected) {
        return Object.keys(CATEGORY_LABELS).map(function (key) {
            return '<option value="' + key + '"' + (key === selected ? ' selected' : '') + '>'
                    + escapeHtml(CATEGORY_LABELS[key]) + '</option>';
        }).join('');
    }

    /** 状态下拉框；保留旧数据里的自定义状态，避免编辑时被静默清空。 */
    function statusOptions(selected) {
        var values = STATUS_OPTIONS.slice();
        if (selected && values.indexOf(selected) < 0) {
            values.push(selected);
        }
        return values.map(function (value) {
            var label = value === '' ? '未设置' : value;
            return '<option value="' + escapeHtml(value) + '"'
                    + (value === selected ? ' selected' : '') + '>'
                    + escapeHtml(label) + '</option>';
        }).join('');
    }

    function closeModal() {
        if (modalOverlay) {
            modalOverlay.remove();
            modalOverlay = null;
        }
        document.body.classList.remove('thc-modal-open');
    }

    function modalError(text) {
        if (!modalOverlay) {
            return;
        }
        var box = modalOverlay.querySelector('[data-thc-modal-error]');
        if (box) {
            box.textContent = text || '';
            box.hidden = !text;
        }
    }

    function apiErrorMessage(message) {
        if (!message) {
            return '保存失败，请稍后重试。';
        }
        if (message === 'permissions.editForbidden' || message === 'permissions.readForbidden') {
            return '你没有编辑活动的权限。';
        }
        if (message === '请先登录' || message === 'permissions.loginRequired') {
            return '请先登录后再编辑活动。';
        }
        return String(message);
    }

    function openEditModal(activity, iso) {
        closeMenu();
        closeModal();
        var creating = !activity;
        if (creating) {
            activity = {
                id: '',
                uid: '',
                year: (iso || isoOf(new Date())).slice(0, 4),
                date: iso || isoOf(new Date()),
                name: '',
                region: '',
                status: '计划',
                qq: '',
                category: 'other',
                categoryLabel: CATEGORY_LABELS.other,
                icsUrl: '',
                cancelled: false
            };
        }
        var date = dateFromIso(activity.date || iso);
        modalOverlay = document.createElement('div');
        modalOverlay.className = 'thc-modal-overlay';
        modalOverlay.innerHTML = '<div class="thc-modal" role="dialog" aria-modal="true"'
                + ' aria-labelledby="thc-modal-title">'
                + '<div class="thc-modal-head">'
                + '<h2 id="thc-modal-title">' + (creating ? '添加活动' : '编辑活动') + '</h2>'
                + '<button type="button" class="thc-modal-close" data-thc-modal-cancel'
                + ' aria-label="关闭">×</button>'
                + '</div>'
                + '<form class="thc-modal-form">'
                + '<p class="thc-modal-subtitle">' + escapeHtml(date.getFullYear() + '年'
                    + (date.getMonth() + 1) + '月' + date.getDate() + '日') + '</p>'
                + '<div class="thc-modal-grid">'
                + '<label for="thc-edit-name">活动名称</label>'
                + '<input id="thc-edit-name" name="name" type="text" required maxlength="255" value="'
                    + escapeHtml(activity.name) + '"/>'
                + '<label for="thc-edit-category">类型</label>'
                + '<select id="thc-edit-category" name="category">'
                    + categoryOptions(activity.category) + '</select>'
                + '<label for="thc-edit-status">状态</label>'
                + '<select id="thc-edit-status" name="status">'
                    + statusOptions(activity.status) + '</select>'
                + '<label for="thc-edit-region">地区</label>'
                + '<input id="thc-edit-region" name="region" type="text" maxlength="255" value="'
                    + escapeHtml(activity.region) + '"/>'
                + '<label for="thc-edit-date">日期</label>'
                + '<input id="thc-edit-date" name="date" type="date" required value="'
                    + escapeHtml(activity.date) + '"/>'
                + '<label for="thc-edit-qq">QQ群</label>'
                + '<input id="thc-edit-qq" name="qq" type="text" maxlength="255" value="'
                    + escapeHtml(activity.qq) + '"/>'
                + '</div>'
                + '<p class="thc-modal-error" data-thc-modal-error hidden></p>'
                + '<div class="thc-modal-actions">'
                + '<button type="button" class="thc-modal-button" data-thc-modal-cancel>取消</button>'
                + '<button type="submit" class="thc-modal-button thc-modal-primary">保存</button>'
                + '</div>'
                + '</form>'
                + '</div>';
        document.body.appendChild(modalOverlay);
        document.body.classList.add('thc-modal-open');

        var form = modalOverlay.querySelector('form');
        var saveButton = form.querySelector('.thc-modal-primary');
        modalOverlay.addEventListener('click', function (event) {
            if (event.target === modalOverlay || event.target.closest('[data-thc-modal-cancel]')) {
                closeModal();
            }
        });
        form.addEventListener('submit', function (event) {
            event.preventDefault();
            var name = form.elements.name.value.trim();
            var dateValue = form.elements.date.value;
            if (!name) {
                modalError('活动名称不能为空。');
                form.elements.name.focus();
                return;
            }
            if (!dateValue) {
                modalError('请选择活动日期。');
                form.elements.date.focus();
                return;
            }
            var status = form.elements.status.value.trim();
            var category = categoryKey(form.elements.category.value);
            var year = activity.year || dateValue.slice(0, 4);
            saveButton.disabled = true;
            saveButton.textContent = '保存中…';
            modalError('');
            fetch(API_BASE + '/save', {
                method: 'POST',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'X-Requested-With': 'XMLHttpRequest'
                },
                body: new URLSearchParams({
                    year: year,
                    id: creating ? '' : activity.id,
                    category: category,
                    status: status,
                    name: name,
                    region: form.elements.region.value.trim(),
                    date: tableDate(dateValue),
                    qq: form.elements.qq.value.trim(),
                    comment: creating ? '日历添加' : '日历编辑'
                }),
                credentials: 'same-origin'
            }).then(function (response) {
                return response.json().catch(function () {
                    return { message: 'HTTP ' + response.status };
                });
            }).then(function (result) {
                if (!result || !result.saved) {
                    throw new Error(apiErrorMessage(result && result.message));
                }
                activity.name = name;
                activity.date = dateValue;
                activity.status = status;
                activity.region = form.elements.region.value.trim();
                activity.qq = form.elements.qq.value.trim();
                activity.category = category;
                activity.categoryLabel = CATEGORY_LABELS[category];
                activity.cancelled = status.indexOf('取消') >= 0;
                if (creating) {
                    activity.id = String(result.id || '');
                    activity.uid = activity.year + '-' + activity.id;
                    state.events.push(activity);
                }
                rebuildByDate();
                closeModal();
                renderAllAt(dateValue);
                showToast('活动已保存');
            }).catch(function (error) {
                modalError(error && error.message ? error.message : '保存失败，请稍后重试。');
                saveButton.disabled = false;
                saveButton.textContent = '保存';
            });
        });
        var nameInput = form.elements.name;
        if (nameInput) {
            nameInput.focus();
            nameInput.select();
        }
    }

    function showToast(text) {
        var toast = document.getElementById('thc-toast');
        if (!toast) {
            toast = document.createElement('div');
            toast.id = 'thc-toast';
            toast.className = 'thc-toast';
            document.body.appendChild(toast);
        }
        toast.textContent = text;
        toast.classList.add('is-visible');
        clearTimeout(toastTimer);
        toastTimer = setTimeout(function () {
            toast.classList.remove('is-visible');
        }, 2200);
    }

    document.addEventListener('click', function (event) {
        if (contextMenu && !contextMenu.contains(event.target)) {
            closeMenu();
        }
    });
    document.addEventListener('keydown', function (event) {
        if (event.key !== 'Escape') {
            return;
        }
        if (contextMenu) {
            closeMenu();
        } else if (modalOverlay) {
            closeModal();
        }
    });
    window.addEventListener('scroll', closeMenu, true);

    /** 窄屏 / 手机：点日期后把下方活动详情带进视口，避免“点了没反应”。 */
    function revealMobileDetails(control) {
        if (!control.root.classList.contains('thc-narrow')) {
            return;
        }
        var rect = control.side.getBoundingClientRect();
        if (rect.top < window.innerHeight && rect.bottom > 0) {
            return;
        }
        window.setTimeout(function () {
            control.side.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        }, 0);
    }

    containers.forEach(function (container) {
        container.addEventListener('click', function (event) {
            if (event.target.closest('[data-thc-retry]')) {
                load();
                return;
            }
            var control = container.__thc;
            if (!control) {
                return;
            }
            var editButton = event.target.closest('[data-thc-edit-uid]');
            if (editButton && state.canEdit) {
                event.preventDefault();
                var activity = findEvent(editButton.getAttribute('data-thc-edit-uid'));
                if (activity) {
                    openEditModal(activity);
                }
                return;
            }
            if (event.target.closest('[data-thc-prev]')) {
                shiftMonth(control, -1);
                return;
            }
            if (event.target.closest('[data-thc-next]')) {
                shiftMonth(control, 1);
                return;
            }
            if (event.target.closest('[data-thc-today]')) {
                goToday(control);
                return;
            }
            var day = event.target.closest('[data-thc-date]');
            if (day) {
                control.selectedIso = day.getAttribute('data-thc-date');
                render(control);
                revealMobileDetails(control);
                return;
            }
            var jump = event.target.closest('[data-thc-jump]');
            if (jump) {
                jumpToDate(control, jump.getAttribute('data-thc-jump'));
            }
        });
        container.addEventListener('change', function (event) {
            var control = container.__thc;
            if (!control) {
                return;
            }
            if (event.target.matches('[data-thc-year]')) {
                setMonth(control, parseInt(event.target.value, 10), control.viewMonth);
                return;
            }
            if (event.target.matches('[data-thc-month]')) {
                setMonth(control, control.viewYear, parseInt(event.target.value, 10));
            }
        });
        container.addEventListener('contextmenu', function (event) {
            if (!state.canEdit) {
                return;
            }
            var control = container.__thc;
            if (!control) {
                return;
            }
            var day = event.target.closest('[data-thc-date]');
            var card = event.target.closest('[data-thc-uid]');
            if (!day && !card) {
                return;
            }
            var iso = day ? day.getAttribute('data-thc-date') : '';
            if (!iso && card) {
                var activity = findEvent(card.getAttribute('data-thc-uid'));
                if (activity) {
                    iso = activity.date;
                }
            }
            if (!iso) {
                return;
            }
            event.preventDefault();
            showDayMenu(control, iso, event.clientX, event.clientY);
        });
    });

    load();
})();

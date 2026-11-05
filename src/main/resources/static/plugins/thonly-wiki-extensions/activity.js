/* 活动正文交互：组合查询、详情、编辑抽屉与变更历史。
 * 年份切换沿用服务端数据，独立只读 /excel 继续共用搜索与类型筛选。
 * 写操作仍调用原 API，由服务端校验登录、编辑权限和同源请求。
 */
(function () {
    'use strict';

    var BASE = '/api/thonly-activity';
    var app = document.querySelector('.thonly-activity-app');
    var quickFilter = 'all';
    var filterRows;
    var detailRow = null;
    var writing = false;
    // 正文的容器查询与皮肤动画会建立定位上下文，遮罩统一挂到 body。
    if (app) {
        Array.prototype.forEach.call(app.querySelectorAll('.wiki-dialog-overlay'), function (overlay) {
            overlay.classList.add('thonly-activity-overlay');
            document.body.appendChild(overlay);
        });
    }

    function byId(id) {
        return document.getElementById(id);
    }

    function table() {
        return document.querySelector('table.thonly-excel');
    }

    function currentYear() {
        var element = table();
        return element ? (element.getAttribute('data-year') || '') : '';
    }

    function message(text, ok) {
        var edit = byId('thonly-dialog');
        var box = edit && !edit.hidden ? byId('thonly-edit-message') : byId('thonly-message');
        if (!box) {
            return;
        }
        box.textContent = text || '';
        box.classList.toggle('is-error', !ok);
        box.hidden = !text;
    }

    function show(element, visible) {
        if (!element || element.hidden === !visible) {
            return;
        }
        if (visible) {
            element.__thonlyReturnFocus = document.activeElement;
        }
        element.hidden = !visible;
        if (element.classList.contains('thonly-modal')) {
            document.body.classList.toggle('thonly-dialog-open', !!document.querySelector('.thonly-modal:not([hidden])'));
            if (visible) {
                var first = element.querySelector('button, input, select, a[href]');
                if (first) { first.focus(); }
            } else if (element.__thonlyReturnFocus && element.__thonlyReturnFocus.isConnected) {
                element.__thonlyReturnFocus.focus();
            }
        }
    }

    function set(id, value) {
        var element = byId(id);
        if (element) {
            element.value = value == null ? '' : value;
        }
    }

    function fieldValue(id) {
        var element = byId(id);
        return element ? element.value.trim() : '';
    }

    // ---------- 查询：全部在已渲染的当前年份数据中筛选，不额外轮询接口 ----------
    var filterKeys = {
        'thonly-region': 'activity-region', 'thonly-type': 'activity-type',
        'thonly-status': 'activity-status', 'thonly-month': 'activity-month'
    };

    function queryValue(id, fallback) {
        var element = byId(id);
        return element ? element.value : fallback;
    }

    function indexedRows() {
        if (!filterRows) {
            var element = table();
            filterRows = element ? Array.prototype.map.call(element.querySelectorAll('tbody tr'), function (row) {
                function value(key, index) {
                    var raw = row.getAttribute('data-' + key);
                    return raw == null ? (row.cells[index] ? row.cells[index].textContent.trim() : '') : raw;
                }
                var name = value('name', 1), region = value('region', 2), qq = value('qq', 4);
                return { row: row, region: region, status: value('status', 0),
                    category: (row.getAttribute('data-category') || '').toUpperCase(),
                    date: toInputDate(value('date', 3)),
                    search: [name, region, qq, value('status', 0)].join(' ').toLowerCase() };
            }) : [];
        }
        return filterRows;
    }

    function updateFilterUrl() {
        if (!app) { return; }
        var url = new URL(window.location.href);
        Object.keys(filterKeys).forEach(function (id) {
            var value = queryValue(id, '');
            if (value && value !== 'ALL') { url.searchParams.set(filterKeys[id], value); }
            else { url.searchParams.delete(filterKeys[id]); }
        });
        var search = app.querySelector('.thonly-search').value.trim();
        if (search) { url.searchParams.set('activity-search', search); }
        else { url.searchParams.delete('activity-search'); }
        if (quickFilter !== 'all') { url.searchParams.set('activity-when', quickFilter); }
        else { url.searchParams.delete('activity-when'); }
        window.history.replaceState(window.history.state, '', url);
    }

    function updateMoreFilters() {
        var button = byId('thonly-more-filters');
        if (!button) { return; }
        var count = (queryValue('thonly-type', 'ALL') !== 'ALL' ? 1 : 0)
                + (queryValue('thonly-status', '') ? 1 : 0) + (queryValue('thonly-month', '') ? 1 : 0);
        button.textContent = button.getAttribute('aria-expanded') === 'true' ? '收起更多筛选'
                : '更多筛选 · 类型 / 状态 / 月份' + (count ? '（已选 ' + count + ' 项）' : '');
    }

    function applyFilters(updateUrl) {
        var input = document.querySelector('input.thonly-search');
        var query = input ? input.value.trim().toLowerCase() : '';
        var type = queryValue('thonly-type', 'ALL');
        var region = queryValue('thonly-region', '');
        var status = queryValue('thonly-status', '');
        var month = queryValue('thonly-month', '');
        var types = {TOUHOU_ONLY:'THO', TOUHOU_PARTY:'THP', SCHOOL_PARTY:'SCHOOL', LIVE_OR_MUSIC:'LIVE'};
        var now = new Date();
        var today = now.getFullYear() + '-' + ('0' + (now.getMonth() + 1)).slice(-2) + '-' + ('0' + now.getDate()).slice(-2);
        var count = 0;
        indexedRows().forEach(function (item) {
            var match = (!type || type === 'ALL' || item.category === types[type])
                && (!query || item.search.indexOf(query) >= 0)
                && (!region || (window.ThonlyActivityRegions
                    ? window.ThonlyActivityRegions.canonicalize(item.region) === window.ThonlyActivityRegions.canonicalize(region)
                    : item.region === region)) && (!status || item.status === status)
                && (!month || (item.date && +item.date.slice(5, 7) === +month))
                && (quickFilter !== 'pending' || !item.date)
                && (quickFilter !== 'upcoming' || (item.date >= today && item.status.indexOf('取消') < 0));
            item.row.hidden = !match;
            if (match) { count++; }
        });
        var counter = byId('thonly-result-count');
        if (counter) { counter.textContent = count + ' 项'; }
        var empty = byId('thonly-empty');
        if (empty) { empty.hidden = count > 0; }
        if (app) {
            Array.prototype.forEach.call(app.querySelectorAll('[data-thonly-quick]'), function (button) {
                button.setAttribute('aria-pressed', String(button.getAttribute('data-thonly-quick') === quickFilter));
            });
        }
        updateMoreFilters();
        if (updateUrl) { updateFilterUrl(); }
    }

    function restoreFilters() {
        if (!app) { return; }
        var params = new URL(window.location.href).searchParams;
        Object.keys(filterKeys).forEach(function (id) {
            var select = byId(id), value = params.get(filterKeys[id]);
            if (select && value != null && Array.prototype.some.call(select.options, function (o) { return o.value === value; })) {
                select.value = value;
            }
        });
        app.querySelector('.thonly-search').value = params.get('activity-search') || '';
        var when = params.get('activity-when');
        quickFilter = when === 'upcoming' || when === 'pending' ? when : 'all';
    }

    function resetFilters() {
        set('thonly-region', ''); set('thonly-type', 'ALL'); set('thonly-status', ''); set('thonly-month', '');
        var input = document.querySelector('.thonly-search');
        if (input) { input.value = ''; }
        quickFilter = 'all';
        applyFilters(true);
    }

    document.addEventListener('input', function (event) {
        if (event.target && event.target.classList.contains('thonly-search')) { applyFilters(true); }
    });
    document.addEventListener('change', function (event) {
        if (!event.target) { return; }
        if (event.target.id === 'thonly-year') {
            var url = new URL(window.location.href);
            url.searchParams.set('year', event.target.value);
            window.location.href = url.toString();
        } else if (filterKeys[event.target.id]) {
            applyFilters(true);
        } else if (event.target.id === 'thonly-f-date-none') {
            syncDateField(event.target.checked);
            setDateHint(event.target.checked ? '' : editingDateRaw);
        } else if (event.target.id === 'thonly-f-date' && event.target.value) {
            var dateNone = byId('thonly-f-date-none');
            if (dateNone) { dateNone.checked = false; }
        }
    });

    // ---------- 写接口 ----------
    function post(url, params) {
        return fetch(url, {
            method: 'POST',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/x-www-form-urlencoded',
                'X-Requested-With': 'XMLHttpRequest'
            },
            body: params,
            credentials: 'same-origin'
        }).then(function (response) {
            return response.json().catch(function () {
                return { message: 'HTTP ' + response.status };
            });
        });
    }

    // ---------- 编辑 / 添加弹窗 ----------
    var dialog = byId('thonly-dialog');
    var historyDialog = byId('thonly-history-dialog');
    var detailDialog = byId('thonly-detail-dialog');
    var editingRow = null;
    /** 打开弹窗时该行的原始日期文本（不是标准日期的原样保留，例如旧数据里的 2024/8/45）。 */
    var editingDateRaw = '';

    /** 表内写法 yyyy/M/d → date 控件的 yyyy-MM-dd（非法日期如 2024/8/45 返回空串）。 */
    function toInputDate(value) {
        var m = /^(\d{4})[/.\-](\d{1,2})[/.\-](\d{1,2})$/.exec((value || '').trim());
        if (!m) {
            return '';
        }
        var text = m[1] + '-' + ('0' + m[2]).slice(-2) + '-' + ('0' + m[3]).slice(-2);
        var probe = new Date(text + 'T00:00:00');
        var valid = probe.getFullYear() === +m[1] && (probe.getMonth() + 1) === +m[2] && probe.getDate() === +m[3];
        return valid ? text : '';
    }

    /** date 控件的 yyyy-MM-dd → 表内写法 yyyy/M/d（不带前导零，和原表一致）。 */
    function fromInputDate(value) {
        var m = /^(\d{4})-(\d{2})-(\d{2})$/.exec((value || '').trim());
        return m ? (+m[1]) + '/' + (+m[2]) + '/' + (+m[3]) : '';
    }

    /** 「暂无」勾选时禁用日期控件；日期控件有值时取消勾选由 change 事件处理。 */
    function syncDateField(none) {
        var input = byId('thonly-f-date');
        if (input) {
            input.disabled = !!none;
            if (none) {
                input.value = '';
            }
        }
    }

    /** 旧数据里不是标准日期的值（如 2024/8/45 / 待定）：显示提示，不动就保留原值。 */
    function setDateHint(raw) {
        var hint = byId('thonly-f-date-hint');
        if (!hint) {
            return;
        }
        var text = (raw || '').trim();
        var show = text !== '' && text !== '暂无' && !toInputDate(text);
        hint.hidden = !show;
        hint.textContent = show ? '原值：' + text + '（不改日期则保留）' : '';
    }

    /** 保存时用的日期：暂无 > 新选的日期 > 原值。 */
    function resolveDate() {
        var none = byId('thonly-f-date-none');
        if (none && none.checked) {
            return '暂无';
        }
        return fromInputDate(fieldValue('thonly-f-date')) || editingDateRaw;
    }

    function openEdit(row) {
        if (!dialog || !row) {
            return;
        }
        editingRow = row;
        var error = byId('thonly-edit-message');
        if (error) { error.hidden = true; }
        var title = byId('thonly-dialog-title');
        if (title) {
            title.textContent = '编辑活动';
        }
        set('thonly-f-status', row.getAttribute('data-status'));
        set('thonly-f-category', (row.getAttribute('data-category') || 'tho').toLowerCase());
        set('thonly-f-name', row.getAttribute('data-name'));
        window.ThonlyActivityRegions.populate(byId('thonly-f-region'), row.getAttribute('data-region'));
        editingDateRaw = (row.getAttribute('data-date') || '').trim();
        var none = editingDateRaw === '暂无';
        set('thonly-f-date', toInputDate(editingDateRaw));
        var dateNone = byId('thonly-f-date-none');
        if (dateNone) {
            dateNone.checked = none;
        }
        syncDateField(none);
        setDateHint(editingDateRaw);
        set('thonly-f-qq', row.getAttribute('data-qq'));
        show(byId('thonly-delete'), true);
        show(dialog, true);
        var nameInput = byId('thonly-f-name');
        if (nameInput) {
            nameInput.focus();
        }
    }

    function openAdd() {
        if (!dialog) {
            return;
        }
        editingRow = null;
        var error = byId('thonly-edit-message');
        if (error) { error.hidden = true; }
        var title = byId('thonly-dialog-title');
        if (title) {
            title.textContent = '添加活动';
        }
        set('thonly-f-status', '计划');
        set('thonly-f-category', 'tho');
        set('thonly-f-name', '');
        window.ThonlyActivityRegions.populate(byId('thonly-f-region'), '');
        editingDateRaw = '';
        set('thonly-f-date', '');
        var dateNone = byId('thonly-f-date-none');
        if (dateNone) {
            dateNone.checked = false;
        }
        syncDateField(false);
        setDateHint('');
        set('thonly-f-qq', '');
        show(byId('thonly-delete'), false);
        show(dialog, true);
        var nameInput = byId('thonly-f-name');
        if (nameInput) {
            nameInput.focus();
        }
    }

    function save() {
        if (writing) { return; }
        var name = fieldValue('thonly-f-name');
        if (!name) {
            message('「名字」不能为空。', false);
            return;
        }
        writing = true;
        byId('thonly-save').disabled = true;
        byId('thonly-save').textContent = '保存中…';
        post(BASE + '/save', new URLSearchParams({
            year: currentYear(),
            id: editingRow ? (editingRow.getAttribute('data-id') || '') : '',
            category: fieldValue('thonly-f-category'),
            status: fieldValue('thonly-f-status'),
            name: name,
            region: fieldValue('thonly-f-region'),
            date: resolveDate(),
            qq: fieldValue('thonly-f-qq'),
            comment: editingRow ? '表格编辑' : '表格新增'
        })).then(function (result) {
            if (result && result.saved) {
                window.location.reload();
                return;
            }
            message((result && result.message) || '保存失败。', false);
        }).catch(function () {
            message('网络错误。', false);
        }).finally(function () {
            writing = false;
            byId('thonly-save').disabled = false;
            byId('thonly-save').textContent = '保存活动';
        });
    }

    function removeRow() {
        if (!editingRow || writing) {
            return;
        }
        if (!window.confirm('确定删除「' + (editingRow.getAttribute('data-name') || '') + '」吗？')) {
            return;
        }
        post(BASE + '/delete', new URLSearchParams({
            year: currentYear(),
            id: editingRow.getAttribute('data-id') || '',
            comment: '表格删除'
        })).then(function (result) {
            if (result && result.deleted) {
                window.location.reload();
                return;
            }
            message((result && result.message) || '删除失败。', false);
        }).catch(function () {
            message('网络错误。', false);
        });
    }

    // ---------- 变更历史弹窗 ----------
    function loadHistory() {
        if (!historyDialog) {
            return;
        }
        fetch(BASE + '/revisions?year=' + encodeURIComponent(currentYear()) + '&limit=50', {
            headers: { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
            credentials: 'same-origin'
        }).then(function (response) {
            return response.json();
        }).then(function (data) {
            var list = byId('thonly-history-list');
            if (!list) {
                return;
            }
            list.textContent = '';
            (data && data.revisions ? data.revisions : []).forEach(function (revision) {
                var item = document.createElement('li');
                item.appendChild(document.createTextNode('#' + revision.id + ' ' + (revision.action || '')
                    + ' ' + (revision.name || '') + ' / ' + (revision.date || '')
                    + (revision.revision_user ? '（' + revision.revision_user + '）' : '') + ' '));
                var rollback = document.createElement('button');
                rollback.type = 'button';
                rollback.className = 'thonly-rollback';
                rollback.setAttribute('data-revision', String(revision.id));
                rollback.textContent = '回滚';
                item.appendChild(rollback);
                list.appendChild(item);
            });
            if (!list.childNodes.length) {
                var empty = document.createElement('li');
                empty.textContent = '（这一年份还没有变更记录）';
                list.appendChild(empty);
            }
            show(historyDialog, true);
        }).catch(function () {
            message('读取历史失败。', false);
        });
    }

    function openDetail(row) {
        if (!detailDialog || !row) { return; }
        detailRow = row;
        var labels = {date:'日期待定', region:'未填写', status:'未填写', qq:'暂未公布'};
        byId('thonly-detail-title').textContent = row.getAttribute('data-name') || '活动详情';
        Object.keys(labels).forEach(function (key) {
            byId('thonly-detail-' + key).textContent = row.getAttribute('data-' + key) || labels[key];
        });
        byId('thonly-detail-type').textContent = row.querySelector('.thonly-type-badge').textContent;
        var source = row.querySelector('.thonly-ics');
        var link = byId('thonly-detail-ics');
        link.hidden = !source;
        if (source) { link.href = source.getAttribute('href'); }
        else { link.removeAttribute('href'); }
        byId('thonly-copy-qq').hidden = !row.getAttribute('data-qq');
        show(detailDialog, true);
    }

    var editForm = byId('thonly-edit-form');
    if (editForm) {
        editForm.addEventListener('submit', function (event) { event.preventDefault(); save(); });
    }

    document.addEventListener('click', function (event) {
        var target = event.target;
        if (!target || !target.closest) { return; }
        var overlay = target.closest('.thonly-modal');
        if (overlay && (target.closest('.wiki-dialog-close, .thonly-dialog-cancel') || target === overlay)) {
            show(overlay, false); return;
        }
        if (target.closest('[data-thonly-reset]')) { resetFilters(); return; }
        var quick = target.closest('[data-thonly-quick]');
        if (quick) { quickFilter = quick.getAttribute('data-thonly-quick'); applyFilters(true); return; }
        if (target.id === 'thonly-more-filters') {
            var expanded = target.getAttribute('aria-expanded') !== 'true';
            target.setAttribute('aria-expanded', String(expanded));
            target.closest('.thonly-filters').classList.toggle('is-expanded', expanded);
            updateMoreFilters(); return;
        }
        var detail = target.closest('.thonly-detail');
        if (detail) { openDetail(detail.closest('tr')); return; }
        if (target.id === 'thonly-detail-edit') { show(detailDialog, false); openEdit(detailRow); return; }
        if (target.id === 'thonly-copy-qq' && detailRow) {
            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(detailRow.getAttribute('data-qq') || '')
                    .then(function () { message('已复制联络信息。', true); })
                    .catch(function () { message('复制失败，请选择联络信息后手动复制。', false); });
            } else { message('请选择联络信息后手动复制。', false); }
            return;
        }
        var editLink = target.closest('.thonly-edit');
        if (editLink) { event.preventDefault(); openEdit(editLink.closest('tr')); return; }
        if (target.id === 'thonly-add') { openAdd(); return; }
        if (target.id === 'thonly-delete') { removeRow(); return; }
        if (target.id === 'thonly-history') { loadHistory(); return; }
        var rollback = target.closest('.thonly-rollback');
        if (rollback) {
            if (!window.confirm('确认回滚到这条历史记录？')) { return; }
            var revisionId = rollback.getAttribute('data-revision') || '';
            post(BASE + '/restore', new URLSearchParams({revisionId:revisionId, comment:'历史回滚'}))
                .then(function (result) {
                    if (result && result.restored) { window.location.reload(); return; }
                    message((result && result.message) || '回滚失败。', false);
                }).catch(function () { message('回滚失败，请稍后重试。', false); });
        }
    });

    document.addEventListener('keydown', function (event) {
        var open = document.querySelector('.thonly-modal:not([hidden])');
        if (!open) { return; }
        if (event.key === 'Escape') { event.preventDefault(); show(open, false); }
        if (event.key === 'Tab') {
            var controls = Array.prototype.filter.call(open.querySelectorAll('button,input,select,a[href]'), function (e) {
                return !e.disabled && e.getClientRects().length;
            });
            var first = controls[0], last = controls[controls.length - 1];
            if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
            else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
        }
    });
    restoreFilters();
    applyFilters(false);
})();

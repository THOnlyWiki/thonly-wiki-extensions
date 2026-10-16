/*
 * 活动速查表交互（独立页 /excel 与 wiki 页面共用）：
 *  - 搜索框 / 类型下拉：客户端筛选（等价原站 searchTable 的行为，可按省份筛）；
 *  - 年份下拉：整页跳转（?year=）；
 *  - 编辑（只在 /wiki/线下活动速查表，且只有有权限的人才会渲染出这些控件）：
 *      「操作」列的「编辑」文字链接 → 弹窗表单；「添加活动」→ 同一个弹窗；
 *      「变更历史」→ 弹窗列表，可回滚；全部调 /api/thonly-activity/*，服务端校验登录 + edit 权限。
 */
(function () {
    'use strict';

    var BASE = '/api/thonly-activity';

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
        var box = byId('thonly-message');
        if (!box) {
            return;
        }
        box.textContent = text || '';
        box.className = 'thonly-message' + (ok ? '' : ' is-error');
        box.hidden = !text;
    }

    function show(element, visible) {
        if (element) {
            element.hidden = !visible;
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

    // ---------- 搜索 + 类型筛选（等价原站 searchTable / 类型下拉） ----------
    function applyFilters() {
        var element = table();
        if (!element) {
            return;
        }
        var input = document.querySelector('input.thonly-search');
        var query = input ? input.value.trim().toLowerCase() : '';
        var typeSelect = byId('thonly-type');
        var type = typeSelect ? typeSelect.value : 'ALL';
        Array.prototype.forEach.call(element.querySelectorAll('tbody tr'), function (row) {
            var text = row.textContent.toLowerCase();
            var rowType = (row.getAttribute('data-category') || '').toUpperCase();
            var matchType = (type === 'ALL')
                || (type === 'TOUHOU_ONLY' && rowType === 'THO')
                || (type === 'TOUHOU_PARTY' && rowType === 'THP')
                || (type === 'SCHOOL_PARTY' && rowType === 'SCHOOL')
                || (type === 'LIVE_OR_MUSIC' && rowType === 'LIVE');
            row.hidden = !(matchType && (query === '' || text.indexOf(query) >= 0));
        });
    }

    document.addEventListener('input', function (event) {
        if (event.target && event.target.classList && event.target.classList.contains('thonly-search')) {
            applyFilters();
        }
    });

    document.addEventListener('change', function (event) {
        if (!event.target) {
            return;
        }
        if (event.target.id === 'thonly-year') {
            var year = event.target.value;
            var url = new URL(window.location.href);
            if (year === 'ALL') {
                url.searchParams.delete('year');
            } else {
                url.searchParams.set('year', year);
            }
            window.location.href = url.toString();
        } else if (event.target.id === 'thonly-type') {
            applyFilters();
        } else if (event.target.id === 'thonly-f-date-none') {
            syncDateField(event.target.checked);
            // 勾上「暂无」就把旧值提示收起来，取消勾选再显示
            setDateHint(event.target.checked ? '' : editingDateRaw);
        } else if (event.target.id === 'thonly-f-date' && event.target.value) {
            // 选了日期就取消「暂无」
            var dateNone = byId('thonly-f-date-none');
            if (dateNone) {
                dateNone.checked = false;
            }
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
    var editingRow = null;
    /** 打开弹窗时该行的原始日期文本（不是标准日期的原样保留，例如旧数据里的 2024/8/45）。 */
    var editingDateRaw = '';

    /** 表内写法 yyyy/M/d → date 控件的 yyyy-MM-dd（非法日期如 2024/8/45 返回空串）。 */
    function toInputDate(value) {
        var m = /^(\d{4})\/(\d{1,2})\/(\d{1,2})$/.exec((value || '').trim());
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
        var title = byId('thonly-dialog-title');
        if (title) {
            title.textContent = '编辑活动';
        }
        set('thonly-f-status', row.getAttribute('data-status'));
        set('thonly-f-category', (row.getAttribute('data-category') || 'tho').toLowerCase());
        set('thonly-f-name', row.getAttribute('data-name'));
        set('thonly-f-region', row.getAttribute('data-region'));
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
        var title = byId('thonly-dialog-title');
        if (title) {
            title.textContent = '添加活动';
        }
        set('thonly-f-status', '计划');
        set('thonly-f-category', 'tho');
        set('thonly-f-name', '');
        set('thonly-f-region', '');
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
        var name = fieldValue('thonly-f-name');
        if (!name) {
            message('「名字」不能为空。', false);
            return;
        }
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
        });
    }

    function removeRow() {
        if (!editingRow) {
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

    // ---------- 点击 / 键盘 ----------
    document.addEventListener('click', function (event) {
        var target = event.target;
        if (!target || !target.closest) {
            return;
        }

        if (target.closest('.wiki-dialog-close') || target.closest('.thonly-dialog-cancel')) {
            show(dialog, false);
            show(historyDialog, false);
            return;
        }
        if (dialog && !dialog.hidden && target === dialog) {
            show(dialog, false);
            return;
        }
        if (historyDialog && !historyDialog.hidden && target === historyDialog) {
            show(historyDialog, false);
            return;
        }

        var editLink = target.closest('.thonly-edit');
        if (editLink) {
            event.preventDefault();
            openEdit(editLink.closest('tr'));
            return;
        }
        if (target.id === 'thonly-add') {
            openAdd();
            return;
        }
        if (target.id === 'thonly-save') {
            save();
            return;
        }
        if (target.id === 'thonly-delete') {
            removeRow();
            return;
        }
        if (target.id === 'thonly-history') {
            loadHistory();
            return;
        }

        var rollback = target.closest('.thonly-rollback');
        if (rollback) {
            var revisionId = rollback.getAttribute('data-revision') || '';
            post(BASE + '/restore', new URLSearchParams({
                revisionId: revisionId,
                comment: '历史回滚'
            })).then(function (result) {
                if (result && result.restored) {
                    window.location.reload();
                    return;
                }
                message((result && result.message) || '回滚失败。', false);
            });
        }
    });

    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape') {
            show(dialog, false);
            show(historyDialog, false);
        }
    });

    applyFilters();
})();

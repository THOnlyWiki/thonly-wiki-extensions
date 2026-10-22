/*
 * 「线下活动速查表」管理员 AI 整理：
 *  - 只有页面渲染时通过 PROTECT 权限的人才会看到按钮；
 *  - 配置 / endpoint / model / key 都由服务端保存，前端只提交年份、宣传文案和候选操作；
 *  - AI 只生成候选，必须管理员在预览里确认后才会写入；没有删除操作。
 */
(function () {
    'use strict';

    var BASE = '/api/thonly-activity/ai';
    var MAX_TEXT = 120000;
    var proposed = [];

    function byId(id) {
        return document.getElementById(id);
    }

    function show(element, visible) {
        if (element) {
            element.hidden = !visible;
        }
    }

    function text(element, value) {
        if (element) {
            element.textContent = value == null ? '' : value;
        }
    }

    function showMessage(id, message, ok) {
        var element = byId(id);
        if (!element) {
            return;
        }
        element.textContent = message || '';
        element.className = 'form-message' + (ok ? ' success' : '');
        element.hidden = !message;
    }

    function currentYear() {
        var table = document.querySelector('table.thonly-excel');
        return table ? (table.getAttribute('data-year') || '') : '';
    }

    function getJson(url) {
        return fetch(url, {
            headers: {'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest'},
            credentials: 'same-origin'
        }).then(readResponse);
    }

    function postForm(url, params) {
        return fetch(url, {
            method: 'POST',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/x-www-form-urlencoded',
                'X-Requested-With': 'XMLHttpRequest'
            },
            body: params,
            credentials: 'same-origin'
        }).then(readResponse);
    }

    function postJson(url, body) {
        return fetch(url, {
            method: 'POST',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json',
                'X-Requested-With': 'XMLHttpRequest'
            },
            body: JSON.stringify(body),
            credentials: 'same-origin'
        }).then(readResponse);
    }

    function readResponse(response) {
        return response.json().catch(function () {
            return {message: 'HTTP ' + response.status};
        }).then(function (data) {
            if (!response.ok) {
                throw new Error((data && data.message) || ('HTTP ' + response.status));
            }
            return data;
        });
    }

    // ---------- 配置 ----------

    var configDialog = byId('thonly-ai-config-dialog');
    var configButton = byId('thonly-ai-config');

    function setChecked(id, value) {
        var input = byId(id);
        if (input) {
            input.checked = !!value;
        }
    }

    function openConfig() {
        if (!configDialog) {
            return;
        }
        show(configDialog, true);
        showMessage('thonly-ai-config-message', '', false);
        getJson(BASE + '/config').then(function (data) {
            setChecked('thonly-ai-config-enabled', data.enabled);
            setChecked('thonly-ai-config-allow-private', data.allowPrivate);
            setChecked('thonly-ai-config-clear-key', false);
            var label = byId('thonly-ai-config-label');
            var base = byId('thonly-ai-config-base-url');
            var model = byId('thonly-ai-config-model');
            var key = byId('thonly-ai-config-key');
            if (label) { label.value = data.buttonLabel || 'AI 整理导入'; }
            if (base) { base.value = data.baseUrl || ''; }
            if (model) { model.value = data.model || ''; }
            if (key) { key.value = ''; }
            text(byId('thonly-ai-key-hint'),
                data.keyConfigured ? '已保存 API Key；留空表示不修改。' : '还没有保存 API Key（不需要密钥的本地接口可以留空）。');
        }).catch(function (error) {
            showMessage('thonly-ai-config-message', error.message || '读取 AI 配置失败。', false);
        });
    }

    function saveConfig() {
        var enabled = byId('thonly-ai-config-enabled');
        var label = byId('thonly-ai-config-label');
        var base = byId('thonly-ai-config-base-url');
        var model = byId('thonly-ai-config-model');
        var key = byId('thonly-ai-config-key');
        var clearKey = byId('thonly-ai-config-clear-key');
        var allowPrivate = byId('thonly-ai-config-allow-private');
        var params = new URLSearchParams({
            enabled: enabled && enabled.checked ? '1' : '0',
            buttonLabel: label ? label.value.trim() : '',
            baseUrl: base ? base.value.trim() : '',
            model: model ? model.value.trim() : '',
            allowPrivate: allowPrivate && allowPrivate.checked ? '1' : '0',
            clearKey: clearKey && clearKey.checked ? '1' : '0'
        });
        if (key && key.value.trim()) {
            params.set('apiKey', key.value.trim());
        }
        if (enabled && enabled.checked
                && (!base || !base.value.trim() || !model || !model.value.trim())) {
            showMessage('thonly-ai-config-message', '启用 AI 前请填写接口地址和模型名称。', false);
            return;
        }
        postForm(BASE + '/config', params).then(function () {
            window.location.reload();
        }).catch(function (error) {
            showMessage('thonly-ai-config-message', error.message || '保存失败。', false);
        });
    }

    if (configButton) {
        configButton.addEventListener('click', openConfig);
    }
    var configSave = byId('thonly-ai-config-save');
    if (configSave) {
        configSave.addEventListener('click', saveConfig);
    }

    // ---------- AI 整理 ----------

    var aiDialog = byId('thonly-ai-dialog');
    var importButton = byId('thonly-ai-import');
    var aiText = byId('thonly-ai-text');
    var status = byId('thonly-ai-status');
    var result = byId('thonly-ai-result');
    var applyButton = byId('thonly-ai-apply');
    var organizeButton = byId('thonly-ai-organize');

    function updateCount() {
        var element = byId('thonly-ai-count');
        if (!aiText || !element) {
            return;
        }
        element.textContent = aiText.value.length + ' / ' + MAX_TEXT;
    }

    function openAi() {
        if (!aiDialog) {
            return;
        }
        show(aiDialog, true);
        showMessage('thonly-ai-status', '', false);
        show(result, false);
        show(applyButton, false);
        if (aiText) {
            aiText.value = '';
            aiText.focus();
        }
        proposed = [];
        updateCount();
    }

    function el(tag, className, content) {
        var element = document.createElement(tag);
        if (className) {
            element.className = className;
        }
        if (content != null) {
            element.textContent = content;
        }
        return element;
    }

    function renderResult(data) {
        if (!result) {
            return;
        }
        result.textContent = '';
        var warnings = (data && data.warnings) ? data.warnings : [];
        var operations = (data && data.operations) ? data.operations : [];
        if (warnings.length) {
            var warningBox = el('div', 'thonly-ai-warnings');
            warningBox.appendChild(el('strong', '', 'AI 提示：'));
            var list = el('ul');
            warnings.forEach(function (warning) {
                list.appendChild(el('li', '', warning));
            });
            warningBox.appendChild(list);
            result.appendChild(warningBox);
        }
        if (!operations.length) {
            result.appendChild(el('p', 'wiki-hint', '没有可写入的候选活动。可以补充文案后重试。'));
            show(result, true);
            show(applyButton, false);
            return;
        }
        var heading = el('p', 'thonly-ai-heading',
            'AI 找到了 ' + operations.length + ' 条候选操作，请核对后勾选要应用的项：');
        result.appendChild(heading);
        var table = el('table', 'thonly-ai-preview');
        var thead = el('thead');
        var headRow = el('tr');
        ['应用', '操作', '名字', '类型', '状态', '地区', '日期', 'QQ群'].forEach(function (title) {
            headRow.appendChild(el('th', '', title));
        });
        thead.appendChild(headRow);
        table.appendChild(thead);
        var tbody = el('tbody');
        operations.forEach(function (operation, index) {
            var row = el('tr');
            var checkCell = el('td');
            var check = document.createElement('input');
            check.type = 'checkbox';
            check.checked = true;
            check.setAttribute('data-ai-index', String(index));
            checkCell.appendChild(check);
            row.appendChild(checkCell);
            row.appendChild(el('td', '', operation.type === 'update' ? '修改' : '新增'));
            row.appendChild(el('td', '', operation.name || ''));
            row.appendChild(el('td', '', (operation.category || '').toUpperCase()));
            row.appendChild(el('td', '', operation.status || ''));
            row.appendChild(el('td', '', operation.region || ''));
            row.appendChild(el('td', '', operation.date || ''));
            row.appendChild(el('td', '', operation.qq || ''));
            tbody.appendChild(row);
        });
        table.appendChild(tbody);
        result.appendChild(table);
        proposed = operations;
        show(result, true);
        show(applyButton, true);
    }

    function organize() {
        if (!aiText) {
            return;
        }
        var value = aiText.value.trim();
        if (!value) {
            showMessage('thonly-ai-status', '请先粘贴宣传文案。', false);
            return;
        }
        var year = currentYear();
        if (!year) {
            showMessage('thonly-ai-status', '没有找到当前年份。', false);
            return;
        }
        showMessage('thonly-ai-status', '', false);
        show(result, false);
        show(applyButton, false);
        if (organizeButton) {
            organizeButton.disabled = true;
        }
        postJson(BASE + '/organize', {year: Number(year), text: value}).then(function (data) {
            showMessage('thonly-ai-status', 'AI 已整理，尚未写入。', true);
            renderResult(data);
        }).catch(function (error) {
            showMessage('thonly-ai-status', error.message || 'AI 整理失败。', false);
        }).finally(function () {
            if (organizeButton) {
                organizeButton.disabled = false;
            }
        });
    }

    function apply() {
        if (!result) {
            return;
        }
        var checks = result.querySelectorAll('input[data-ai-index]:checked');
        var selected = [];
        Array.prototype.forEach.call(checks, function (check) {
            var index = Number(check.getAttribute('data-ai-index'));
            if (proposed[index]) {
                selected.push(proposed[index]);
            }
        });
        if (!selected.length) {
            showMessage('thonly-ai-status', '没有勾选任何候选操作。', false);
            return;
        }
        if (applyButton) {
            applyButton.disabled = true;
        }
        postJson(BASE + '/apply', {year: Number(currentYear()), operations: selected}).then(function (data) {
            if (data && data.errors && data.errors.length) {
                showMessage('thonly-ai-status', '已应用 ' + (data.applied || 0) + ' 条；'
                    + data.errors.join('；'), false);
            } else {
                showMessage('thonly-ai-status', '已应用 ' + (data.applied || 0) + ' 条，正在刷新…', true);
            }
            if (data && data.applied > 0) {
                window.setTimeout(function () {
                    window.location.reload();
                }, 700);
            } else if (applyButton) {
                applyButton.disabled = false;
            }
        }).catch(function (error) {
            showMessage('thonly-ai-status', error.message || '应用失败。', false);
            if (applyButton) {
                applyButton.disabled = false;
            }
        });
    }

    if (importButton) {
        importButton.addEventListener('click', openAi);
    }
    if (organizeButton) {
        organizeButton.addEventListener('click', organize);
    }
    if (applyButton) {
        applyButton.addEventListener('click', apply);
    }
    if (aiText) {
        aiText.addEventListener('input', updateCount);
    }

    // ---------- 关闭弹窗 ----------

    function closeDialogs() {
        show(configDialog, false);
        show(aiDialog, false);
    }

    Array.prototype.forEach.call(document.querySelectorAll('.thonly-ai-cancel'), function (button) {
        button.addEventListener('click', closeDialogs);
    });
    [configDialog, aiDialog].forEach(function (dialog) {
        if (!dialog) {
            return;
        }
        Array.prototype.forEach.call(dialog.querySelectorAll('.wiki-dialog-close'), function (button) {
            button.addEventListener('click', closeDialogs);
        });
        dialog.addEventListener('click', function (event) {
            if (event.target === dialog) {
                closeDialogs();
            }
        });
    });
    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape') {
            closeDialogs();
        }
    });
})();

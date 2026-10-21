/*
 * 活动页面生成器（Phase 1）
 * - 两个帮助页共用：活动系列主页 / 某一届子页面
 * - 创建和编辑使用同一套表单
 * - 预览调用服务端 Wikitext 渲染；保存走 /api/page-generator/save
 * - 图片上传复用 core 的 /api/files/upload
 */
(function () {
    'use strict';

    var app = document.querySelector('.page-generator-app');
    if (!app) {
        return;
    }

    var kind = app.getAttribute('data-kind') === 'sub' ? 'sub' : 'main';
    var initialTitle = app.getAttribute('data-initial-title') || '';
    var state = {
        editing: Boolean(initialTitle),
        imported: false,
        importedNotice: false,
        gallery: [],
        loaded: false,
        previewMode: 'rendered'
    };

    function escape(value) {
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

    function byField(name) {
        return app.querySelector('[data-pg-field="' + name + '"]');
    }

    function value(name) {
        var field = byField(name);
        return field ? field.value.trim() : '';
    }

    function checked(name) {
        var field = byField(name);
        return !!(field && field.checked);
    }

    function notice(text, ok) {
        var box = app.querySelector('[data-pg-notice]');
        if (!box) {
            return;
        }
        box.hidden = !text;
        box.className = 'pg-notice' + (ok ? ' is-success' : ' is-error');
        box.textContent = text || '';
    }

    function noticeWithLink(text, url, ok, label) {
        var box = app.querySelector('[data-pg-notice]');
        if (!box) {
            return;
        }
        box.hidden = false;
        box.className = 'pg-notice' + (ok ? ' is-success' : ' is-error');
        box.innerHTML = escape(text || '')
                + (url ? ' <a href="' + escape(url) + '">' + escape(label || '查看页面') + '</a>' : '');
    }

    function setLoading(text) {
        var box = app.querySelector('[data-pg-loading]');
        if (box) {
            box.hidden = !text;
            box.textContent = text || '';
        }
    }

    function updateFullTitle() {
        var target = app.querySelector('[data-pg-full-title]');
        if (!target) {
            return;
        }
        target.textContent = computedTitle();
    }

    function computedTitle() {
        if (kind === 'sub') {
            var parent = value('parent').replace(/\/+$/, '');
            var sub = value('subName').replace(/^\/+/, '');
            return parent && sub ? parent + '/' + sub : '（填写父标题和子标题后生成）';
        }
        return value('title') || '（填写页面标题后生成）';
    }

    function inputField(name, label, placeholder, wide) {
        return '<label class="pg-field' + (wide ? ' pg-field-wide' : '') + '">'
                + '<span>' + escape(label) + '</span>'
                + '<input type="text" data-pg-field="' + name + '" value="" placeholder="'
                + escape(placeholder || '') + '"/>'
                + '</label>';
    }

    function textArea(name, label, placeholder) {
        return '<label class="pg-field pg-field-wide">'
                + '<span>' + escape(label) + '</span>'
                + '<textarea data-pg-field="' + name + '" rows="6" placeholder="'
                + escape(placeholder || '') + '"></textarea>'
                + '</label>';
    }

    function commonInfoFields() {
        return '<div class="pg-grid">'
                + inputField('shortName', '活动简称', '例如：桂T')
                + inputField('type', '活动类型', '例如：展会类活动 / 聚会类活动')
                + inputField('region', '地区', '例如：广西壮族自治区南宁市')
                + inputField('host', '主办方', '例如：XX组委会')
                + inputField('groupNo', 'QQ 群', '群号或群链接')
                + inputField('bilibili', '哔哩哔哩', '官方账号链接')
                + inputField('weibo', '微博', '官方账号链接')
                + inputField('website', '官方网站', 'https://')
                + inputField('market', '东方市场', '活动社团名称或链接')
                + '</div>';
    }

    function logoField() {
        return '<div class="pg-field pg-field-wide">'
                + '<span>活动 Logo</span>'
                + '<div class="pg-upload-row">'
                + '<input type="text" data-pg-field="logo" placeholder="文件名，例如：广西THO-LOGO.png"/>'
                + '<label class="pg-upload-button">上传图片'
                + '<input type="file" accept="image/*" data-pg-logo-input hidden/>'
                + '</label>'
                + '</div>'
                + '<small class="pg-hint">上传成功后会自动填入文件名；也可以直接使用已有文件名。</small>'
                + '</div>';
    }

    function galleryField() {
        return '<div class="pg-field pg-field-wide">'
                + '<span>宣传图 / 返图</span>'
                + '<div class="pg-upload-row">'
                + '<label class="pg-upload-button">选择多张图片'
                + '<input type="file" accept="image/*" multiple data-pg-gallery-input hidden/>'
                + '</label>'
                + '<span class="pg-hint" data-pg-gallery-status></span>'
                + '</div>'
                + '<div class="pg-gallery-list" data-pg-gallery-list></div>'
                + '</div>';
    }

    function needsWorkField() {
        return '<label class="pg-check">'
                + '<input type="checkbox" data-pg-field="needsWork"/>'
                + '<span>标记为需要补充（生成 {{需要补充}}）</span>'
                + '</label>';
    }

    function mainForm(payload) {
        return '<div class="pg-card">'
                + '<h2>活动系列信息</h2>'
                + '<div class="pg-grid">'
                + '<label class="pg-field pg-field-wide"><span>页面标题</span>'
                + '<input type="text" data-pg-field="title" data-lock-on-edit value="" placeholder="例如：广西THO"/>'
                + '<small class="pg-hint">页面访问地址，创建后不建议修改。</small></label>'
                + '<label class="pg-field pg-field-wide"><span>活动显示名称</span>'
                + '<input type="text" data-pg-field="displayName" value="" placeholder="留空则使用页面标题"/>'
                + '</label>'
                + commonInfoFields()
                + '</div>'
                + logoField()
                + '</div>'
                + '<div class="pg-card">'
                + '<div class="pg-card-head"><h2>历届活动</h2>'
                + '<button type="button" class="pg-button pg-button-small" data-pg-add-edition>+ 添加一届</button></div>'
                + '<p class="pg-hint">不要求全部填完；只填名称和日期也能生成一个标准列表。</p>'
                + '<div class="pg-table-wrap"><table class="pg-table"><thead><tr>'
                + '<th>活动名称</th><th>日期</th><th>地点</th><th>摊位数 / 状态</th><th></th>'
                + '</tr></thead><tbody data-pg-editions></tbody></table></div>'
                + '</div>'
                + '<div class="pg-card">' + galleryField() + '</div>'
                + '<div class="pg-card">' + textArea('customContent', '自定义内容', '可选：历史、视频、活动特色等 wikitext') + '</div>'
                + '<div class="pg-card">' + needsWorkField() + '</div>';
    }

    function subForm(payload) {
        return '<div class="pg-card">'
                + '<h2>届次信息</h2>'
                + '<div class="pg-grid">'
                + '<label class="pg-field"><span>父活动页面标题</span>'
                + '<input type="text" data-pg-field="parent" data-lock-on-edit placeholder="例如：广西THO"/></label>'
                + '<label class="pg-field"><span>子页面标题</span>'
                + '<input type="text" data-pg-field="subName" data-lock-on-edit placeholder="例如：广西THO-幻想桂风颂3"/></label>'
                + '<label class="pg-field pg-field-wide"><span>生成后的页面标题</span>'
                + '<strong class="pg-full-title" data-pg-full-title>（填写后生成）</strong></label>'
                + '</div>'
                + logoField()
                + '</div>'
                + '<div class="pg-card">'
                + '<div class="pg-card-head"><h2>日程</h2>'
                + '<button type="button" class="pg-button pg-button-small" data-pg-add-day>+ 添加一天</button></div>'
                + '<div class="pg-table-wrap"><table class="pg-table"><thead><tr>'
                + '<th>标题</th><th>类型</th><th>日期</th><th>地址</th><th>摊位数</th><th></th>'
                + '</tr></thead><tbody data-pg-days></tbody></table></div>'
                + '</div>'
                + '<div class="pg-card">' + galleryField() + '</div>'
                + '<div class="pg-card">' + textArea('customContent', '自定义内容', '可选：交通、注意事项、返图说明等 wikitext') + '</div>'
                + '<div class="pg-card">' + needsWorkField() + '</div>';
    }

    function shell() {
        var title = kind === 'sub' ? '新建 / 编辑活动子页面' : '新建 / 编辑活动系列页';
        var subtitle = kind === 'sub'
                ? '填写这一届的基本信息和日程，预览后直接创建。'
                : '只填核心信息就能生成结构标准的活动主页。';
        return '<div class="pg-head">'
                + '<div><h1>' + title + '</h1><p>' + subtitle + '</p></div>'
                + '<a class="pg-back" href="' + (kind === 'sub'
                    ? '/wiki/%E5%B8%AE%E5%8A%A9%3A%E9%A1%B5%E9%9D%A2%E7%94%9F%E6%88%90%E5%99%A8'
                    : '/wiki/%E5%B8%AE%E5%8A%A9%3A%E5%AD%90%E9%A1%B5%E9%9D%A2%E7%94%9F%E6%88%90%E5%99%A8')
                + '">切换到' + (kind === 'sub' ? '活动主页' : '子页面') + '</a>'
                + '</div>'
                + '<div class="pg-load-existing">'
                + '<label>编辑已有页面</label>'
                + '<input type="text" data-pg-load-title placeholder="输入页面标题，例如：广西THO"/>'
                + '<button type="button" class="pg-button pg-button-small" data-pg-load>加载</button>'
                + '</div>'
                + '<div class="pg-notice" data-pg-notice hidden></div>'
                + '<div class="pg-loading" data-pg-loading hidden></div>'
                + '<div class="pg-layout" data-pg-layout hidden>'
                + '<div class="pg-form">' + (kind === 'sub' ? subForm() : mainForm()) + '</div>'
                + '<aside class="pg-preview">'
                + '<div class="pg-preview-head"><h2>预览</h2>'
                + '<div class="pg-preview-tabs">'
                + '<button type="button" data-pg-preview-mode="rendered" class="selected">页面</button>'
                + '<button type="button" data-pg-preview-mode="source">源码</button>'
                + '</div></div>'
                + '<div class="pg-preview-body" data-pg-preview-body><p class="pg-preview-placeholder">填写内容后点击「预览」。</p></div>'
                + '<div class="pg-actions">'
                + '<button type="button" class="pg-button" data-pg-preview>预览</button>'
                + '<button type="button" class="pg-button pg-button-primary" data-pg-save>'
                + (state.editing ? '保存修改' : '创建页面') + '</button>'
                + '</div>'
                + '</aside>'
                + '</div>';
    }

    function fieldHtml(row, field, placeholder, type) {
        return '<input type="' + (type || 'text') + '" data-pg-row-field="' + field + '" value="'
                + escape(row[field] || '') + '" placeholder="' + escape(placeholder || '') + '"/>';
    }

    function editionRow(row) {
        row = row || {};
        var tr = document.createElement('tr');
        tr.setAttribute('data-pg-edition-row', '');
        tr.innerHTML = '<td>' + fieldHtml(row, 'name', '例如：广西THO-幻想桂风颂3') + '</td>'
                + '<td>' + fieldHtml(row, 'date', '2026年8月16日') + '</td>'
                + '<td>' + fieldHtml(row, 'place', '城市 / 场馆') + '</td>'
                + '<td>' + fieldHtml(row, 'booths', '数字 / 不适用 / 招募中') + '</td>'
                + '<td><button type="button" class="pg-remove" data-pg-remove-edition aria-label="删除这一届">×</button></td>';
        return tr;
    }

    function dayRow(row) {
        row = row || {};
        var tr = document.createElement('tr');
        tr.setAttribute('data-pg-day-row', '');
        tr.innerHTML = '<td>' + fieldHtml(row, 'title', 'Day 1') + '</td>'
                + '<td>' + fieldHtml(row, 'type', '即卖会 / 茶会') + '</td>'
                + '<td>' + fieldHtml(row, 'date', '2026年8月16日') + '</td>'
                + '<td>' + fieldHtml(row, 'address', '城市 / 场馆') + '</td>'
                + '<td>' + fieldHtml(row, 'booths', '摊位数 / 不适用') + '</td>'
                + '<td><button type="button" class="pg-remove" data-pg-remove-day aria-label="删除这一天">×</button></td>';
        return tr;
    }

    function renderGallery() {
        var list = app.querySelector('[data-pg-gallery-list]');
        if (!list) {
            return;
        }
        list.innerHTML = state.gallery.length
                ? state.gallery.map(function (name, index) {
                    return '<span class="pg-gallery-item">' + escape(name)
                            + '<button type="button" data-pg-remove-gallery="' + index
                            + '" aria-label="移除">×</button></span>';
                }).join('')
                : '<span class="pg-hint">还没有图片。</span>';
    }

    function setField(name, value) {
        var field = byField(name);
        if (field) {
            if (field.type === 'checkbox') {
                field.checked = Boolean(value);
            } else {
                field.value = value == null ? '' : String(value);
            }
        }
    }

    function populate(payload) {
        var fields = ['title', 'displayName', 'shortName', 'type', 'region', 'host',
            'groupNo', 'bilibili', 'weibo', 'website', 'market', 'logo',
            'parent', 'subName', 'customContent'];
        fields.forEach(function (name) {
            setField(name, payload[name]);
        });
        setField('needsWork', payload.needsWork);
        state.gallery = Array.isArray(payload.gallery) ? payload.gallery.slice() : [];
        renderGallery();

        var editionBody = app.querySelector('[data-pg-editions]');
        if (editionBody) {
            editionBody.innerHTML = '';
            var editions = Array.isArray(payload.editions) ? payload.editions : [];
            editions.forEach(function (row) { editionBody.appendChild(editionRow(row)); });
            if (!editions.length) {
                editionBody.appendChild(editionRow({}));
            }
        }
        var dayBody = app.querySelector('[data-pg-days]');
        if (dayBody) {
            dayBody.innerHTML = '';
            var days = Array.isArray(payload.days) ? payload.days : [];
            days.forEach(function (row) { dayBody.appendChild(dayRow(row)); });
            if (!days.length) {
                dayBody.appendChild(dayRow({}));
            }
        }
        if (state.editing) {
            Array.prototype.forEach.call(app.querySelectorAll('[data-lock-on-edit]'), function (field) {
                field.readOnly = true;
            });
        }
        updateFullTitle();
    }

    function collect() {
        var payload = { kind: kind, imported: state.imported };
        var fields = ['title', 'displayName', 'shortName', 'type', 'region', 'host',
            'groupNo', 'bilibili', 'weibo', 'website', 'market', 'logo',
            'parent', 'subName', 'customContent'];
        fields.forEach(function (name) { payload[name] = value(name); });
        payload.needsWork = checked('needsWork');
        payload.gallery = state.gallery.slice();

        if (kind === 'sub') {
            payload.days = Array.prototype.map.call(
                    app.querySelectorAll('[data-pg-day-row]'), function (row) {
                        return {
                            title: row.querySelector('[data-pg-row-field="title"]').value.trim(),
                            type: row.querySelector('[data-pg-row-field="type"]').value.trim(),
                            date: row.querySelector('[data-pg-row-field="date"]').value.trim(),
                            address: row.querySelector('[data-pg-row-field="address"]').value.trim(),
                            booths: row.querySelector('[data-pg-row-field="booths"]').value.trim()
                        };
                    }).filter(function (row) {
                        return row.title || row.type || row.date || row.address || row.booths;
                    });
        } else {
            payload.editions = Array.prototype.map.call(
                    app.querySelectorAll('[data-pg-edition-row]'), function (row) {
                        return {
                            name: row.querySelector('[data-pg-row-field="name"]').value.trim(),
                            date: row.querySelector('[data-pg-row-field="date"]').value.trim(),
                            place: row.querySelector('[data-pg-row-field="place"]').value.trim(),
                            booths: row.querySelector('[data-pg-row-field="booths"]').value.trim()
                        };
                    }).filter(function (row) {
                        return row.name || row.date || row.place || row.booths;
                    });
        }
        return payload;
    }

    function request(url, options) {
        return fetch(url, Object.assign({
            credentials: 'same-origin',
            headers: { 'Accept': 'application/json' }
        }, options || {})).then(function (response) {
            return response.json().catch(function () {
                return { ok: false, message: 'HTTP ' + response.status };
            });
        });
    }

    function postJson(url, payload) {
        return request(url, {
            method: 'POST',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(payload)
        });
    }

    function showPreview(data) {
        var body = app.querySelector('[data-pg-preview-body]');
        if (!body) {
            return;
        }
        if (!data.ok) {
            body.innerHTML = '<p class="pg-preview-placeholder">' + escape(data.message || '预览失败。') + '</p>';
            notice(data.message || '预览失败。', false);
            return;
        }
        state.previewMode = state.previewMode || 'rendered';
        if (state.previewMode === 'source') {
            body.innerHTML = '<pre class="pg-source">' + escape(data.source || '') + '</pre>';
        } else {
            body.innerHTML = data.html
                    ? '<div class="pg-rendered">' + data.html + '</div>'
                    : '<pre class="pg-source">' + escape(data.source || '') + '</pre>';
        }
    }

    function preview(scrollToPreview) {
        var data = collect();
        notice('正在生成预览…', true);
        postJson('/api/page-generator/preview', data).then(function (result) {
            showPreview(result);
            if (result.ok) {
                if (state.importedNotice) {
                    notice('已识别已有页面；保存时只更新模板区域，手写内容会保留。', true);
                } else {
                    notice('', true);
                }
                if (scrollToPreview && app.classList.contains('pg-narrow')) {
                    window.setTimeout(function () {
                        var panel = app.querySelector('.pg-preview');
                        if (panel) {
                            panel.scrollIntoView({ behavior: 'smooth', block: 'start' });
                        }
                    }, 50);
                }
            }
        });
    }

    function save() {
        var data = collect();
        notice('正在保存…', true);
        postJson('/api/page-generator/save', data).then(function (result) {
            if (!result.ok) {
                notice(result.message || '保存失败。', false);
                return;
            }
            noticeWithLink(result.message || '保存成功。', result.pageUrl, true);
            var noticeBox = app.querySelector('[data-pg-notice]');
            if (noticeBox) {
                noticeBox.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            }
            if (!state.editing) {
                state.editing = true;
                Array.prototype.forEach.call(app.querySelectorAll('[data-lock-on-edit]'), function (field) {
                    field.readOnly = true;
                });
                var button = app.querySelector('[data-pg-save]');
                if (button) {
                    button.textContent = '保存修改';
                }
            }
        });
    }

    function uploadFiles(files, onUploaded) {
        if (!files || !files.length) {
            return;
        }
        var jobs = Array.prototype.map.call(files, function (file) {
            var form = new FormData();
            form.append('file', file);
            return fetch('/api/files/upload', {
                method: 'POST',
                headers: { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
                body: form,
                credentials: 'same-origin'
            }).then(function (response) {
                return response.json().catch(function () {
                    return { code: response.status, message: 'HTTP ' + response.status };
                });
            }).then(function (result) {
                if (result.code !== 200 || !result.data || !result.data.name) {
                    throw new Error(result.message || '图片上传失败。');
                }
                return result.data.name;
            });
        });
        Promise.all(jobs).then(function (names) {
            onUploaded(names);
            notice('图片上传完成。', true);
        }).catch(function (error) {
            notice(error.message || '图片上传失败。', false);
        });
    }

    function updateLayout() {
        // 站点正文区在 1440 视口下通常只有约 900px；不能只看 viewport 的媒体查询。
        if (app.clientWidth > 0) {
            app.classList.toggle('pg-narrow', app.clientWidth < 1100);
        }
    }

    function bind() {
        app.addEventListener('click', function (event) {
            var target = event.target;
            if (target.closest('[data-pg-preview]')) {
                preview(true);
                return;
            }
            if (target.closest('[data-pg-save]')) {
                save();
                return;
            }
            if (target.closest('[data-pg-preview-mode]')) {
                state.previewMode = target.closest('[data-pg-preview-mode]').getAttribute('data-pg-preview-mode');
                Array.prototype.forEach.call(app.querySelectorAll('[data-pg-preview-mode]'), function (button) {
                    button.classList.toggle('selected', button === target.closest('[data-pg-preview-mode]'));
                });
                // 源码 / 页面切换重新取一次预览，避免前端自己保存两份渲染结果。
                preview(false);
                return;
            }
            if (target.closest('[data-pg-load]')) {
                var loadTitle = app.querySelector('[data-pg-load-title]');
                var nextTitle = loadTitle ? loadTitle.value.trim() : '';
                if (!nextTitle) {
                    notice('请输入要编辑的页面标题。', false);
                    return;
                }
                window.location.href = window.location.pathname + '?title=' + encodeURIComponent(nextTitle);
                return;
            }
            if (target.closest('[data-pg-add-edition]')) {
                var editions = app.querySelector('[data-pg-editions]');
                if (editions) {
                    editions.appendChild(editionRow({}));
                }
                return;
            }
            if (target.closest('[data-pg-add-day]')) {
                var days = app.querySelector('[data-pg-days]');
                if (days) {
                    days.appendChild(dayRow({}));
                }
                return;
            }
            if (target.closest('[data-pg-remove-edition]')) {
                target.closest('[data-pg-edition-row]').remove();
                return;
            }
            if (target.closest('[data-pg-remove-day]')) {
                target.closest('[data-pg-day-row]').remove();
                return;
            }
            var removeGallery = target.closest('[data-pg-remove-gallery]');
            if (removeGallery) {
                state.gallery.splice(Number(removeGallery.getAttribute('data-pg-remove-gallery')), 1);
                renderGallery();
            }
        });
        app.addEventListener('input', function (event) {
            if (event.target.matches('[data-pg-field="title"], [data-pg-field="parent"], [data-pg-field="subName"]')) {
                updateFullTitle();
            }
        });
        app.addEventListener('change', function (event) {
            if (event.target.matches('[data-pg-logo-input]')) {
                uploadFiles(event.target.files, function (names) {
                    if (names.length) {
                        setField('logo', names[0]);
                    }
                });
                return;
            }
            if (event.target.matches('[data-pg-gallery-input]')) {
                uploadFiles(event.target.files, function (names) {
                    state.gallery = state.gallery.concat(names);
                    renderGallery();
                });
            }
        });
    }

    function load() {
        updateLayout();
        if (!initialTitle) {
            app.querySelector('[data-pg-layout]').hidden = false;
            populate(kind === 'sub' ? { kind: kind } : { kind: kind });
            bind();
            return;
        }
        setLoading('正在加载页面数据…');
        request('/api/page-generator/load?kind=' + encodeURIComponent(kind)
                + '&title=' + encodeURIComponent(initialTitle)).then(function (result) {
            setLoading('');
            if (!result.ok) {
                app.querySelector('[data-pg-layout]').hidden = true;
                var message = result.message || '无法加载页面生成器数据。';
                noticeWithLink(message, result.editUrl, false, '使用普通编辑');
                return;
            }
            state.editing = Boolean(result.editing);
            state.imported = Boolean(result.imported
                    || (result.payload && result.payload.imported));
            state.importedNotice = state.imported;
            state.gallery = [];
            app.innerHTML = shell();
            bind();
            app.querySelector('[data-pg-layout]').hidden = false;
            populate(result.payload || {});
            preview(false);
        });
    }

    app.innerHTML = shell();
    load();
    if (window.ResizeObserver) {
        new ResizeObserver(updateLayout).observe(app);
    } else {
        window.addEventListener('resize', updateLayout);
    }
})();

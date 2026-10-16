/*
 * 「活动分布地图」前端（页面正文接管，**不嵌 iframe**；线上原版是 iframe 指向 /map/ 的 ECharts 页）。
 * 数据源与线上一致：Wiki 固定分类 分类:<地图名>活动（如 分类:福建活动），由服务端渲染进
 * <script type="application/json" id="thonly-map-data">（见 MapPageHook），前端不再发请求。
 *  - 本地内置 ECharts + china.js 画中国地图；省份按分类词条数着色（visualMap）；
 *  - 点击省份 → 弹窗列出 分类:X活动 的词条（标题链到分类页，词条链到各自页面；不含模板）；
 *  - 「全屏预览」：Fullscreen API（失败退回 CSS 假全屏），进入 / 退出后 chart.resize()。
 */
(function () {
    'use strict';

    var dataElement = document.getElementById('thonly-map-data');
    var canvas = document.getElementById('thonly-map-canvas');
    if (!dataElement || !canvas || typeof window.echarts === 'undefined') {
        return;
    }

    var data = {};
    try {
        data = JSON.parse(dataElement.textContent || '{}') || {};
    } catch (error) {
        return;
    }

    var provinces = data.provinces || [];
    var byName = {};
    provinces.forEach(function (item) {
        byName[item.name] = item;
    });
    var maxValue = 0;
    provinces.forEach(function (item) {
        maxValue = Math.max(maxValue, item.value || 0);
    });

    var chart = window.echarts.init(canvas);
    chart.setOption({
        tooltip: {
            trigger: 'item',
            formatter: function (params) {
                var item = byName[params.name];
                return params.name + '：' + (item ? item.value : 0) + ' 个活动词条';
            }
        },
        visualMap: {
            min: 0,
            max: Math.max(maxValue, 1),
            left: 16,
            bottom: 24,
            text: ['多', '少'],
            calculable: true,
            inRange: { color: ['#fff0f7', '#ffc9e5', '#ff7cc4', '#ff008e'] }
        },
        geo: {
            map: 'china',
            roam: true,
            label: { emphasis: { show: true } },
            itemStyle: {
                normal: { areaColor: '#f7f9fc', borderColor: '#9aa4b2' },
                emphasis: { areaColor: '#ffd6ec' }
            }
        },
        series: [{
            name: '活动词条',
            type: 'map',
            map: 'china',
            geoIndex: 0,
            emphasis: { label: { show: true } },
            data: provinces
        }]
    });

    chart.on('click', function (params) {
        openDialog(params.name, byName[params.name]);
    });

    // ---------- 省份词条弹窗（复用 core 的 .wiki-dialog 样式） ----------
    var dialog = document.getElementById('thonly-map-dialog');

    function openDialog(name, province) {
        if (!dialog) {
            return;
        }
        var members = province && province.members ? province.members : [];
        var titleElement = document.getElementById('thonly-map-dialog-title');
        if (titleElement) {
            titleElement.textContent = '';
            var titleLink = document.createElement('a');
            titleLink.href = province && province.categoryUrl ? province.categoryUrl : '#';
            titleLink.title = '进入分类页：' + (province && province.category ? province.category : name + '活动');
            titleLink.textContent = name + '东方Project线下活动';
            titleElement.appendChild(titleLink);
            titleElement.appendChild(document.createTextNode('（' + members.length + ' 个词条）'));
        }
        var listElement = document.getElementById('thonly-map-dialog-list');
        if (listElement) {
            listElement.textContent = '';
            if (!members.length) {
                var empty = document.createElement('li');
                empty.textContent = '该分类还没有词条（点标题可进入分类页）。';
                listElement.appendChild(empty);
            }
            members.forEach(function (member) {
                var listItem = document.createElement('li');
                var link = document.createElement('a');
                link.href = member.url;
                link.textContent = member.title;
                listItem.appendChild(link);
                listElement.appendChild(listItem);
            });
        }
        dialog.hidden = false;
    }

    // ---------- 「全屏预览」= 大弹窗（不是浏览器真全屏）：画布移进大弹窗，关闭时移回 ----------
    var wrap = document.getElementById('thonly-map-wrap');
    var modal = document.getElementById('thonly-map-modal');
    var modalBody = document.getElementById('thonly-map-modal-body');
    var button = document.getElementById('thonly-map-fullscreen');

    function resize() {
        chart.resize();
    }

    function modalOpen() {
        return !!(modal && !modal.hidden);
    }

    function setButton() {
        if (button) {
            button.textContent = modalOpen() ? '退出全屏' : '全屏预览';
        }
    }

    function closeCategoryDialog() {
        if (dialog) {
            dialog.hidden = true;
        }
    }

    function openModal() {
        if (!modal || !modalBody) {
            return;
        }
        modalBody.appendChild(canvas);
        modal.hidden = false;
        setButton();
        window.setTimeout(resize, 30);
    }

    function closeModal() {
        if (!modal || modal.hidden) {
            return;
        }
        closeCategoryDialog();
        if (wrap) {
            wrap.appendChild(canvas);
        }
        modal.hidden = true;
        setButton();
        window.setTimeout(resize, 30);
    }

    // ---------- 事件分发 ----------
    document.addEventListener('click', function (event) {
        var target = event.target;
        if (!target || !target.closest) {
            return;
        }
        // 分类词条弹窗（层级最高）：点关闭 / 点遮罩 → 关它
        if (dialog && !dialog.hidden) {
            if (target.closest('.wiki-dialog-close') || target === dialog) {
                closeCategoryDialog();
                return;
            }
            if (target.closest('#thonly-map-dialog')) {
                return; // 弹窗内部其它点击不处理
            }
        }
        // 大弹窗：点关闭 / 点遮罩 → 退出大弹窗
        if (modalOpen() && (target === modal || target.closest('#thonly-map-modal .wiki-dialog-close'))) {
            closeModal();
            return;
        }
        if (target.closest('#thonly-map-fullscreen')) {
            if (modalOpen()) {
                closeModal();
            } else {
                openModal();
            }
        }
    });

    document.addEventListener('keydown', function (event) {
        if (event.key !== 'Escape') {
            return;
        }
        if (dialog && !dialog.hidden) {
            closeCategoryDialog();
            return;
        }
        if (modalOpen()) {
            closeModal();
        }
    });

    window.setTimeout(resize, 50);
})();

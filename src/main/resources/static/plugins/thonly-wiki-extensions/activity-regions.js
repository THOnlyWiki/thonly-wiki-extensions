/* 速查表与日历共用省级地区选项；保留旧记录中的非标准地区值。 */
(function () {
    'use strict';

    // GB/T 2260 省级行政区划代码升序（11—65、71、81、82）。
    var provinces = [
        '北京市', '天津市', '河北省', '山西省', '内蒙古自治区',
        '辽宁省', '吉林省', '黑龙江省', '上海市', '江苏省',
        '浙江省', '安徽省', '福建省', '江西省', '山东省',
        '河南省', '湖北省', '湖南省', '广东省', '广西壮族自治区',
        '海南省', '重庆市', '四川省', '贵州省', '云南省',
        '西藏自治区', '陕西省', '甘肃省', '青海省', '宁夏回族自治区',
        '新疆维吾尔自治区', '台湾省', '香港特别行政区', '澳门特别行政区'
    ];

    var countries = ['日本国', '大韩民国', '越南社会主义共和国', '新加坡共和国', '泰王国'];
    var extras = ['海外', '多地区'];
    var regions = provinces.concat(extras, countries);

    var aliases = {
        '日本': '日本国', 'japan': '日本国', '日本(japan)': '日本国',
        '韩国': '大韩民国', 'korea': '大韩民国', 'south korea': '大韩民国', '韩国(korea)': '大韩民国',
        '越南': '越南社会主义共和国', 'vietnam': '越南社会主义共和国',
        '新加坡': '新加坡共和国', 'singapore': '新加坡共和国',
        '泰国': '泰王国', 'thailand': '泰王国'
    };

    function canonicalize(value) {
        var text = String(value == null ? '' : value).trim();
        return aliases[text.toLowerCase()] || text;
    }

    function populate(select, currentValue) {
        if (!select) {
            return;
        }
        var value = currentValue == null ? '' : String(currentValue);
        select.textContent = '';

        function addOption(parent, value, label) {
            var option = document.createElement('option');
            option.value = value;
            option.textContent = label;
            parent.appendChild(option);
        }
        function group(label, values) {
            if (!values.length) { return; }
            var container = document.createElement('optgroup');
            container.label = label;
            values.forEach(function (region) {
                addOption(container, region, region === value && regions.indexOf(value) < 0 ? region + '（原有值）' : region);
            });
            select.appendChild(container);
        }
        addOption(select, '', '请选择省份 / 地区');
        group('省级地区', provinces.slice(0, -2));
        group('香港、澳门', provinces.slice(-2));
        var other = extras.slice();
        if (value && regions.indexOf(value) < 0) { other.push(value); }
        other.sort(function (a, b) { return a.localeCompare(b, 'zh-CN-u-co-pinyin'); });
        group('其他地区', other);
        // 五个国家必须作为最后一组；旧值也放在该组之前。
        group('国家', countries);
        select.value = value;
    }

    window.ThonlyActivityRegions = { populate: populate, canonicalize: canonicalize };
}());

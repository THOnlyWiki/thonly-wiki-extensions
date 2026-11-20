/* The original selects remain the source of truth for table filtering and browser history. */
(function () {
    'use strict';

    if (typeof HTMLElement.prototype.showPopover !== 'function') return;

    let openMenu;
    function reposition() {
        if (openMenu) openMenu();
    }
    window.addEventListener('scroll', reposition, {capture: true, passive: true});
    window.addEventListener('resize', reposition);
    window.visualViewport?.addEventListener('resize', reposition);
    window.visualViewport?.addEventListener('scroll', reposition);

    function enhance(select) {
        const label = select.labels[0];
        const button = document.createElement('button');
        button.type = 'button';
        button.id = select.id + '-trigger';
        button.className = 'thonly-filter-trigger';

        const value = document.createElement('span');
        value.id = select.id + '-value';
        label.id = select.id + '-label';
        label.htmlFor = button.id;
        button.setAttribute('aria-labelledby', label.id + ' ' + value.id);
        button.setAttribute('aria-expanded', 'false');
        button.append(value);

        const panel = document.createElement('div');
        panel.id = select.id + '-menu';
        panel.className = 'thonly-filter-menu';
        panel.setAttribute('popover', 'auto');
        button.setAttribute('popovertarget', panel.id);
        button.setAttribute('aria-haspopup', select.id === 'thonly-region' ? 'dialog' : 'listbox');

        const list = document.createElement('div');
        list.id = select.id + '-options';
        list.className = 'thonly-filter-options';
        list.setAttribute('role', 'listbox');
        list.setAttribute('aria-labelledby', label.id);
        const empty = document.createElement('p');
        empty.className = 'thonly-filter-no-match';
        empty.setAttribute('role', 'status');
        empty.textContent = '没有匹配的地区';
        empty.hidden = true;

        let search;
        if (select.id === 'thonly-region') {
            panel.setAttribute('role', 'dialog');
            panel.setAttribute('aria-labelledby', label.id);
            search = document.createElement('input');
            search.type = 'search';
            search.setAttribute('aria-label', '搜索省份或地区');
            search.autocomplete = 'off';
            search.placeholder = '搜索省份或地区';
            search.addEventListener('input', renderOptions);
            panel.append(search);
        }
        panel.append(list, empty);
        button.setAttribute('aria-controls', search ? panel.id : list.id);

        function renderOptions() {
            const query = search ? search.value.trim().toLowerCase() : '';
            const options = Array.from(select.options).filter(option =>
                !option.hidden && !option.disabled && option.textContent.toLowerCase().includes(query));
            list.replaceChildren(...options.map(option => {
                const row = document.createElement('button');
                row.type = 'button';
                row.className = 'thonly-filter-option';
                row.setAttribute('role', 'option');
                row.setAttribute('aria-selected', String(option.selected));
                row.tabIndex = -1;
                row.textContent = option.textContent;
                row.addEventListener('click', () => {
                    panel.hidePopover();
                    button.focus({preventScroll: true});
                    if (!option.selected) {
                        select.value = option.value;
                        select.dispatchEvent(new Event('change', {bubbles: true}));
                    }
                });
                return row;
            }));
            const entry = list.querySelector('[aria-selected="true"]') || list.firstElementChild;
            if (entry) entry.tabIndex = 0;
            empty.hidden = Boolean(entry);
        }

        function place() {
            const anchor = button.getBoundingClientRect();
            const viewport = window.visualViewport;
            const left = (viewport?.offsetLeft || 0) + 8;
            const right = left + (viewport?.width || document.documentElement.clientWidth) - 16;
            const top = (viewport?.offsetTop || 0) + 8;
            const bottom = top + (viewport?.height || window.innerHeight) - 16;
            if (!anchor.width || anchor.bottom < top || anchor.top > bottom) {
                if (panel.matches(':popover-open')) panel.hidePopover();
                return;
            }
            const width = Math.min(Math.max(anchor.width, search ? 208 : 0), right - left);
            const below = bottom - anchor.bottom - 4;
            const above = anchor.top - top - 4;
            const naturalHeight = Math.min(300, select.options.length * 37 + (search ? 56 : 12));
            const upwards = below < naturalHeight && above > below;
            panel.classList.toggle('opens-up', upwards);
            panel.style.width = width + 'px';
            panel.style.left = Math.max(left, Math.min(anchor.left, right - width)) + 'px';
            panel.style.top = upwards ? 'auto' : anchor.bottom + 4 + 'px';
            panel.style.bottom = upwards ? (window.innerHeight - anchor.top + 4) + 'px' : 'auto';
            panel.style.maxHeight = Math.max(0, Math.min(300, upwards ? above : below)) + 'px';
        }

        function sync() {
            value.textContent = select.selectedOptions[0]?.textContent || '';
            button.disabled = select.disabled;
            if (panel.matches(':popover-open')) {
                const hadListFocus = list.contains(document.activeElement);
                renderOptions();
                if (hadListFocus) focusOption(list.querySelector('[tabindex="0"]'));
            }
        }

        function focusOption(row) {
            if (!row) { search?.focus({preventScroll: true}); return; }
            const previous = list.querySelector('[tabindex="0"]');
            if (previous) previous.tabIndex = -1;
            row.tabIndex = 0;
            row.focus({preventScroll: true});
            // Keep the active row visible without scrolling the page behind the menu.
            const item = row.getBoundingClientRect();
            const bounds = list.getBoundingClientRect();
            if (item.top < bounds.top) list.scrollTop += item.top - bounds.top;
            else if (item.bottom > bounds.bottom) list.scrollTop += item.bottom - bounds.bottom;
        }

        panel.addEventListener('beforetoggle', event => {
            const opening = event.newState === 'open';
            button.setAttribute('aria-expanded', String(opening));
            if (!opening) {
                if (openMenu === place) openMenu = null;
                return;
            }
            if (search) search.value = '';
            renderOptions();
            place();
        });
        panel.addEventListener('toggle', event => {
            if (event.newState !== 'open') return;
            openMenu = place;
            focusOption(list.querySelector('[tabindex="0"]'));
        });

        button.addEventListener('keydown', event => {
            if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return;
            event.preventDefault();
            if (!panel.matches(':popover-open')) panel.showPopover();
        });
        panel.addEventListener('keydown', event => {
            if (event.key === 'Escape') {
                event.preventDefault();
                panel.hidePopover();
                button.focus({preventScroll: true});
                return;
            }
            const key = event.key;
            if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(key)
                    || (event.target === search && !key.startsWith('Arrow'))) return;
            const rows = Array.from(list.children);
            if (!rows.length) return;
            event.preventDefault();
            const current = rows.indexOf(document.activeElement);
            let next;
            if (key === 'Home') next = 0;
            else if (key === 'End' || (key === 'ArrowUp' && current < 0)) next = rows.length - 1;
            else if (key === 'ArrowDown') next = Math.min(rows.length - 1, current + 1);
            else next = current - 1;
            if (next < 0 && search) search.focus({preventScroll: true});
            else focusOption(rows[Math.max(0, next)]);
        });
        panel.addEventListener('focusout', event => {
            if (event.relatedTarget && event.relatedTarget !== button && !panel.contains(event.relatedTarget)
                    && panel.matches(':popover-open')) panel.hidePopover();
        });

        select.addEventListener('change', sync);
        select.addEventListener('thonly:filter-sync', sync);
        select.after(button, panel);
        select.hidden = true;
        sync();
    }

    document.querySelectorAll('.thonly-activity-app .thonly-filter-field select').forEach(enhance);
})();

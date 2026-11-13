"use strict";

import { refreshProducts, setProductView } from './products/products-helper.js';
import { sortModes } from './products/product-order.js';

const search = document.querySelector('.toolbar > .search input[type="search"]');
const dropdown = document.querySelector('.toolbar > .dropdown');
const summary = dropdown.querySelector('summary');
const buttons = [...dropdown.querySelectorAll(':scope > .menu > button')];
const label = document.createElement('span');
for (const node of [...summary.childNodes]) {
    if (node.nodeType === Node.TEXT_NODE) node.remove();
}
summary.prepend(label);
let order = "Alphabetically";

function applyView() {
    label.textContent = `Sort: ${order} `;
    for (const button of buttons) {
        const selected = button.textContent.trim() === order;
        button.classList.toggle('selected', selected);
        button.setAttribute('aria-pressed', String(selected));
    }
    return setProductView(order, search.value);
}

for (const button of buttons) {
    button.addEventListener('click', () => {
        const choice = button.textContent.trim();
        if (!sortModes.includes(choice)) return;
        order = choice;
        dropdown.open = false;
        applyView().catch(console.error);
    });
}
search.addEventListener('input', () => applyView().catch(console.error));
document.querySelector('.header-actions [title="Refresh"]')
    .addEventListener('click', () => refreshProducts().catch(console.error));
document.addEventListener('products:changed', () => refreshProducts().catch(console.error));
applyView().catch(console.error);
refreshProducts().catch(console.error);


const tagOptions = dropdown.querySelector('.tag-sort-list');
const tagSearch = dropdown.querySelector('.tag-sort-menu input[type="search"]');
let tagLabels = [];
function drawTagOptions() {
    tagOptions.replaceChildren();
    for (const label of tagLabels.filter(label => label.toLowerCase().includes(tagSearch.value.toLowerCase()))) {
        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = label;
        button.addEventListener('click', () => {
            order = "Tag: " + label;
            dropdown.open = false;
            applyView().catch(console.error);
        });
        tagOptions.append(button);
    }
}
tagOptions.replaceChildren();
tagSearch.addEventListener('input', drawTagOptions);
document.addEventListener('tags:loaded', event => {
    tagLabels = [...new Set(event.detail)].sort((a, b) => a.localeCompare(b));
    drawTagOptions();
});

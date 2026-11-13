const TAG_COLORS = ["green", "blue", "purple", "pink", "red", "orange", "yellow", "gray"];
export function applyTagColor(element, color) {
    element.classList.remove(...TAG_COLORS.map(value => `tag-${value}`));
    element.classList.add(`tag-${TAG_COLORS.includes(color) ? color : "green"}`);
}

// Uses the existing chosen-tag and option templates from Claire's tag form.
export async function fetchTagCatalog() {
    const response = await fetch('/api/tags');
    if (!response.ok) throw new Error("Could not load tags. Please try again.");
    const data = await response.json();
    if (!Array.isArray(data.labels) || !data.products) throw new Error("Invalid tag response.");
    return data;
}

export function createTagEditor(form) {
    const field = form.querySelector('.tag-field');
    const dropdown = field.querySelector('.add-tag');
    const options = field.querySelector('.tag-option-list');
    const search = dropdown.querySelector('input[type="search"]');
    const name = dropdown.querySelector('.new-tag-form input[type="text"]');
    const create = dropdown.querySelector('.new-tag-form button');
    const status = document.createElement('p');
    status.className = 'menu-message';
    status.setAttribute('role', 'status');
    dropdown.querySelector('.new-tag-form').append(status);
    dropdown.querySelector('.color-types').hidden = false;
    let colors = new Map();
    let labels = [];
    let selected = [];
    let disabled = true;
    const key = label => label.trim().toLowerCase();

    function draw() {
        field.querySelectorAll('.tag-chosen').forEach(node => node.remove());
        for (const label of selected) {
            const chip = document.getElementById('template-tag-chosen').content.cloneNode(true);
            chip.querySelector('.tag-name').textContent = label;
            applyTagColor(chip.querySelector('.tag'), colors.get(key(label)));
            const remove = chip.querySelector('button');
            remove.disabled = disabled;
            remove.addEventListener('click', () => {
                if (disabled) return;
                selected = selected.filter(value => key(value) !== key(label));
                draw();
            });
            field.insertBefore(chip, dropdown);
        }
        options.replaceChildren();
        for (const label of labels.filter(label =>
            key(label).includes(key(search.value)) && !selected.some(value => key(value) === key(label)))) {
            const entry = document.getElementById('template-tag-option').content.cloneNode(true);
            entry.querySelector('.tag-name').textContent = label;
            applyTagColor(entry.querySelector('.tag-dot'), colors.get(key(label)));
            const button = entry.querySelector('button');
            button.disabled = disabled;
            button.addEventListener('click', () => choose(label));
            options.append(entry);
        }
    }
    function choose(value) {
        if (disabled) return;
        const label = value.trim();
        if (!label || label.length > 100 || /[\x00-\x1f\x7f]/.test(label)) {
            status.textContent = "Enter a tag name with 1 to 100 characters.";
            return;
        }
        if (selected.some(value => key(value) === key(label))) {
            status.textContent = "That tag is already selected.";
            return;
        }
        if (selected.length >= 50) { status.textContent = "Choose at most 50 tags."; return; }
        const existing = labels.find(value => key(value) === key(label));
        selected.push(existing || label);
        if (!existing) {
            labels.push(label);
            colors.set(key(label), dropdown.querySelector('[name="tag-color"]:checked')?.value || "green");
        }
        status.textContent = "";
        name.value = "";
        draw();
    }
    create.addEventListener('click', () => choose(name.value));
    name.addEventListener('keydown', event => {
        if (event.key === 'Enter') { event.preventDefault(); choose(name.value); }
    });
    search.addEventListener('input', draw);
    return {
        reset() { colors = new Map();
            dropdown.querySelector('[name="tag-color"][value="green"]').checked = true;
            labels = []; selected = []; search.value = ""; name.value = ""; status.textContent = ""; draw(); },
        async load(id = null) {
            const data = await fetchTagCatalog();
            colors = new Map(Object.entries(data.colors || {}).map(([label, color]) => [key(label), color]));
            labels = [...new Set(data.labels)];
            selected = id === null ? [] : [...new Set(data.products[id] || [])];
            draw();
        },
        values() { return [...selected]; },
        colors() { return Object.fromEntries(selected.map(label => [label, colors.get(key(label)) || "green"])); },
        setDisabled(value) {
            disabled = value;
            field.querySelectorAll('input, button').forEach(control => { control.disabled = value; });

        }
    };
}

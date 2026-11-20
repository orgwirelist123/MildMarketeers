"use strict";

(() => {
    const cards = [
        document.querySelector(".stat-sales"),
        document.querySelector(".stat-revenue"),
        document.querySelector(".stat-profit")
    ];
    const graphs = [...document.querySelectorAll(".summary-graphs .graph")];
    const list = document.querySelector(".top-list");
    const title = document.querySelector(".top-title");
    const arrows = [...document.querySelectorAll(".top-arrow")];
    const modes = [
        { key: "units", title: "Top selling products" },
        { key: "revenue", title: "Highest revenue products" },
        { key: "profit", title: "Most profitable products" }
    ];
    const money = new Intl.NumberFormat("en-US", {
        style: "currency", currency: "USD"
    });
    const numbers = new Intl.NumberFormat("en-US");
    const status = document.createElement("p");
    status.className = "summary-status";
    status.setAttribute("role", "status");
    document.querySelector(".stat-cards").after(status);
    cards[0].previousElementSibling.textContent = "Total sales (units)";
    document.querySelector(".summary-graphs .summary-heading").textContent =
        "Sales (units)";

    let data = null;
    let mode = 0;
    let requestVersion = 0;
    let activeRequest;

    function format(value, key) {
        if (value === null) return "Unavailable";
        return key === "units"
            ? numbers.format(Number(value))
            : money.format(Number(value));
    }

    function dateString(date) {
        return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
    }

    function listMessage(message) {
        const item = document.createElement("li");
        item.className = "summary-empty";
        item.textContent = message;
        list.replaceChildren(item);
    }

    function renderRanking() {
        const selected = modes[mode];
        title.textContent = selected.title;
        if (!data) return;

        if (selected.key === "profit" && data.totals.missingCostSales > 0) {
            listMessage("Profit ranking is unavailable because some sales have no recorded production cost.");
            return;
        }

        const products = [...data.products].sort((a, b) =>
            Number(b[selected.key]) - Number(a[selected.key]) ||
            a.name.localeCompare(b.name) ||
            a.productId - b.productId
        ).slice(0, 10);

        list.replaceChildren();
        if (!products.length) {
            listMessage("No sales in this date range.");
            return;
        }
        products.forEach((product, index) => {
            const item = document.createElement("li");
            item.className = "top-item";
            [
                ["top-rank", index + 1],
                ["top-name", product.name],
                ["top-value", format(product[selected.key], selected.key)]
            ].forEach(([className, value]) => {
                const span = document.createElement("span");
                span.className = className;
                span.textContent = value;
                if (className === "top-name") span.title = product.name;
                item.append(span);
            });
            list.append(item);
        });
    }

    function svgElement(name, attributes = {}, text) {
        const element = document.createElementNS("http://www.w3.org/2000/svg", name);
        Object.entries(attributes).forEach(([key, value]) =>
            element.setAttribute(key, value));
        if (text !== undefined) element.textContent = text;
        return element;
    }

    function renderChart(container, key, label) {
        container.replaceChildren();
        if (!data.totals.transactions) {
            container.textContent = "No sales in this date range.";
            return;
        }

        const days = data.daily;
        const values = days.map(day =>
            day[key] === null ? null : Number(day[key]));
        const known = values.filter(value => value !== null);
        if (!known.length) {
            container.textContent = "Production costs were not recorded for these sales.";
            return;
        }

        const width = Math.max(640, days.length * 24 + 110);
        const height = 210;
        const left = 90;
        const right = width - 20;
        const top = 18;
        const bottom = 165;
        const minimum = Math.min(0, ...known);
        const maximum = Math.max(0, ...known);
        const range = maximum - minimum || 1;
        const y = value => bottom - (value - minimum) / range * (bottom - top);
        const step = (right - left) / days.length;
        const svg = svgElement("svg", {
            viewBox: `0 0 ${width} ${height}`,
            width, height, role: "img",
            "aria-label": `${label} by day, ${data.start} through ${data.end}`
        });

        [maximum, minimum].forEach(value => {
            svg.append(svgElement("line", {
                x1: left, x2: right, y1: y(value), y2: y(value),
                stroke: "var(--border)"
            }));
            svg.append(svgElement("text", {
                x: left - 8, y: y(value) + 4, "text-anchor": "end"
            }, format(value, key)));
        });
        svg.append(svgElement("line", {
            x1: left, x2: right, y1: y(0), y2: y(0),
            stroke: "var(--text-secondary)"
        }));

        days.forEach((day, index) => {
            const value = values[index];
            const x = left + index * step + step * 0.15;
            if (value !== null) {
                const bar = svgElement("rect", {
                    x, y: Math.min(y(value), y(0)),
                    width: step * 0.7,
                    height: Math.max(1, Math.abs(y(value) - y(0))),
                    fill: value < 0 ? "#b42318" : "var(--primary)",
                    tabindex: "0",
                    "aria-label": `${day.date}: ${format(value, key)}`
                });
                bar.append(svgElement("title", {},
                    `${day.date}: ${format(value, key)}`));
                svg.append(bar);
            } else {
                svg.append(svgElement("text", {
                    x: x + step * 0.35, y: bottom - 5,
                    "text-anchor": "middle"
                }, "?"));
            }
            if (index % Math.max(1, Math.ceil(days.length / 8)) === 0 ||
                    index === days.length - 1) {
                svg.append(svgElement("text", {
                    x: x + step * 0.35, y: bottom + 25,
                    "text-anchor": "middle"
                }, day.date.slice(5)));
            }
        });
        container.append(svg);
    }

    function clearDisplay(message) {
        data = null;
        cards.forEach(card => card.textContent = "—");
        graphs.forEach(graph => graph.textContent = message);
        listMessage(message);
        arrows.forEach(arrow => arrow.disabled = true);
    }

    async function load(first, last) {
        const version = ++requestVersion;
        activeRequest?.abort();
        activeRequest = new AbortController();
        clearDisplay("Loading…");
        status.textContent = "Loading summary…";

        try {
            const query = new URLSearchParams({
                start: dateString(first), end: dateString(last)
            });
            const response = await fetch(`/api/summary?${query}`, {
                signal: activeRequest.signal,
                cache: "no-store"
            });
            const result = await response.json();
            if (!response.ok) {
                throw new Error(result.message || "Could not load summary.");
            }
            if (version !== requestVersion) return;
            data = result;
            ["units", "revenue", "profit"].forEach((key, index) => {
                cards[index].textContent = format(data.totals[key], key);
                renderChart(graphs[index], key, modes[index].key);
            });
            status.textContent = data.totals.missingCostSales > 0
                ? `${data.totals.missingCostSales} sale record(s) have no saved production cost. Total profit and profit ranking are unavailable; ? marks affected days.`
                : "Profit is revenue minus recorded component costs.";
            arrows.forEach(arrow => arrow.disabled = false);
            renderRanking();
        } catch (error) {
            if (version !== requestVersion || error.name === "AbortError") return;
            clearDisplay("Summary unavailable.");
            status.textContent = error.message || "Could not load summary. Please refresh.";
        }
    }

    arrows.forEach((arrow, index) => {
        arrow.setAttribute("aria-label", index === 0
            ? "Previous product ranking" : "Next product ranking");
        arrow.addEventListener("click", () => {
            mode = (mode + (index === 0 ? 2 : 1)) % modes.length;
            renderRanking();
        });
    });

    const today = new Date();
    const weekAgo = new Date(today);
    weekAgo.setDate(today.getDate() - 6);
    clearDisplay("Loading…");

    if (typeof flatpickr !== "function") {
        clearDisplay("Date picker unavailable.");
        status.textContent = "Could not load the date picker. Check your connection and refresh.";
        return;
    }

    flatpickr("#date-range", {
        mode: "range",
        dateFormat: "M j, Y",
        defaultDate: [weekAgo, today],
        maxDate: "today",
        position: "below right",
        positionElement: document.querySelector(".date-range"),
        locale: { rangeSeparator: " - " },
        onChange: selectedDates => {
            if (selectedDates.length === 2) {
                load(selectedDates[0], selectedDates[1]);
            } else {
                ++requestVersion;
                activeRequest?.abort();
                clearDisplay("Select an end date.");
                status.textContent = "Select an end date to load the summary.";
            }
        },
        onClose: selectedDates => {
            if (selectedDates.length === 1) {
                load(selectedDates[0], selectedDates[0]);
            }
        }
    });
    load(weekAgo, today);
})();

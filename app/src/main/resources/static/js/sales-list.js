"use strict";

(() => {
  const list = document.querySelector(".sales-list");
  const template = document.querySelector("#template-sale");
  const refreshButton = document.querySelector(
    '.header-actions [title="Refresh"]'
  );
  const searchInput = document.querySelector('.search input[type="search"]');
  const sortDropdown = document.querySelector(".toolbar .dropdown");
  const sortSummary = sortDropdown.querySelector("summary");
  const sortButtons = [...sortDropdown.querySelectorAll(".menu button")];

  const status = document.createElement("p");
  status.setAttribute("role", "status");
  status.setAttribute("aria-live", "polite");
  list.before(status);

  searchInput.setAttribute("aria-label", "Search sales");

  // Keep the existing arrow while updating the sort label.
  const sortLabel = document.createElement("span");
  sortSummary.childNodes.forEach((node) => {
    if (node.nodeType === Node.TEXT_NODE) node.textContent = "";
  });
  sortSummary.prepend(sortLabel);

  let sales = [];
  let sortOrder = "Newest";
  let loading = false;
  let loaded = false;
  let loadError = "";

  const dateFormatter = new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric"
  });

  function formatDate(value) {
    // Parse as a local calendar date to avoid a timezone date shift.
    const [year, month, day] = value.split("-").map(Number);
    return dateFormatter.format(new Date(year, month - 1, day));
  }

  function updateSortControls() {
    sortLabel.textContent = `Sort: ${sortOrder} `;
    sortButtons.forEach((button) => {
      const selected = button.textContent.trim() === sortOrder;
      button.classList.toggle("selected", selected);
      button.setAttribute("aria-pressed", String(selected));
    });
  }

  // Total sorting becomes available once sale totals are stored.
  sortButtons.forEach((button) => {
    const label = button.textContent.trim();

    if (label === "Highest total" || label === "Lowest total") {
      button.disabled = true;
      button.title = "Sale totals are not available yet.";
      return;
    }

    button.addEventListener("click", () => {
      sortOrder = label;
      updateSortControls();
      sortDropdown.open = false;
      render();
    });
  });

  function render() {
    const query = searchInput.value.trim().toLowerCase();
    const filtered = sales.filter((sale) => {
      const searchable = [
        `#${sale.saleId}`,
        sale.productName,
        sale.date,
        formatDate(sale.date)
      ].join(" ").toLowerCase();

      return searchable.includes(query);
    });

    filtered.sort((first, second) => {
      const firstDate = `${first.date}T${first.time}`;
      const secondDate = `${second.date}T${second.time}`;
      const comparison =
        firstDate.localeCompare(secondDate) || first.saleId - second.saleId;

      return sortOrder === "Oldest" ? comparison : -comparison;
    });

    const fragment = document.createDocumentFragment();

    filtered.forEach((sale) => {
      const entry = template.content.cloneNode(true);
      entry.querySelector(".sales-id").textContent = `#${sale.saleId}`;
      entry.querySelector(".sales-date").textContent = formatDate(sale.date);
      entry.querySelector(".sales-product-name").textContent = sale.productName;

      // Do not invent quantities or calculate totals using today's price.
      entry.querySelector(".sales-quantity").textContent = "";
      const total = entry.querySelector(".sales-total");
      total.textContent = "—";
      total.setAttribute("aria-label", "Total unavailable");
      total.title = "Quantity and price at the time of sale are not stored yet.";

      fragment.appendChild(entry);
    });

    list.replaceChildren(fragment);

    if (loading) {
      status.textContent = loaded ? "Refreshing sales…" : "Loading sales…";
    } else if (loadError) {
      status.textContent = loaded
        ? `${loadError} Previously loaded sales are still displayed.`
        : loadError;
    } else if (sales.length === 0) {
      status.textContent = "No sales have been recorded yet.";
    } else if (filtered.length === 0) {
      status.textContent = "No sales match your search.";
    } else {
      status.textContent =
        `${filtered.length} sale${filtered.length === 1 ? "" : "s"} displayed.`;
    }
  }

  async function refresh() {
    if (loading) return;

    loading = true;
    loadError = "";
    refreshButton.disabled = true;
    list.setAttribute("aria-busy", "true");
    render();

    try {
      const response = await fetch("/api/sales/list", {
        headers: { Accept: "application/json" },
        cache: "no-store"
      });

      const result = await response.json();

      if (!response.ok) {
        throw new Error(result.message || "Could not load sales. Please try again.");
      }

      if (!Array.isArray(result) || !result.every((sale) =>
        sale !== null &&
        Number.isInteger(sale.saleId) &&
        typeof sale.productName === "string" &&
        typeof sale.date === "string" &&
        /^\d{4}-\d{2}-\d{2}$/.test(sale.date) &&
        typeof sale.time === "string"
      )) {
        throw new Error("The server returned an unexpected sales response.");
      }

      sales = result;
      loaded = true;
    } catch (error) {
      loadError = "Could not load sales. Please try Refresh.";
      console.error("Sales loading failed:", error);
    } finally {
      loading = false;
      refreshButton.disabled = false;
      list.setAttribute("aria-busy", "false");
      render();
    }
  }

  searchInput.addEventListener("input", render);
  refreshButton.addEventListener("click", refresh);

  updateSortControls();
  refresh();
})();

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
  status.className = "status-message";
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

    // Keep money exact, including totals larger than JavaScript's safe integer range.
  function totalInCents(value) {
    const [whole, fraction = ""] = value.split(".");
    return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, "0"));
  }

  function formatMoney(value) {
    const cents = totalInCents(value);
    const dollars = (cents / 100n).toLocaleString("en-US");
    const remainder = String(cents % 100n).padStart(2, "0");
    return `$${dollars}.${remainder}`;
  }

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

      button.disabled = false;
      button.removeAttribute("title");

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
      const dateComparison =
        firstDate.localeCompare(secondDate) || first.saleId - second.saleId;

      if (sortOrder === "Highest total" || sortOrder === "Lowest total") {
        const firstTotal = totalInCents(first.total);
        const secondTotal = totalInCents(second.total);
        const comparison =
          firstTotal < secondTotal ? -1 : firstTotal > secondTotal ? 1 : 0;

        // Equal totals are ordered newest first.
        return (sortOrder === "Lowest total" ? comparison : -comparison)
          || -dateComparison;
      }

      return sortOrder === "Oldest" ? dateComparison : -dateComparison;
    });

    const fragment = document.createDocumentFragment();

    filtered.forEach((sale) => {
      const entry = template.content.cloneNode(true);
      entry.querySelector(".sales-id").textContent = `#${sale.saleId}`;
      entry.querySelector(".sales-date").textContent = formatDate(sale.date);
      entry.querySelector(".sales-product-name").textContent = sale.productName;

      const quantity = entry.querySelector(".sales-quantity");
      quantity.textContent = `× ${sale.quantity}`;
      quantity.setAttribute("aria-label", `${sale.quantity} units sold`);

      const total = entry.querySelector(".sales-total");
      total.textContent = formatMoney(sale.total);
      total.setAttribute("aria-label", `Sale total ${formatMoney(sale.total)}`);
      total.removeAttribute("title");

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
      status.textContent = "";
    }

    status.hidden = !status.textContent;
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
        typeof sale.time === "string" &&
        Number.isInteger(sale.quantity) &&
        sale.quantity > 0 &&
        typeof sale.total === "string" &&
        /^\d+(\.\d{1,2})?$/.test(sale.total)
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
  refreshButton.addEventListener("click", () => {
    sendNotification("Refreshing sales!");
    refresh();
  });

  updateSortControls();
  refresh();
})();

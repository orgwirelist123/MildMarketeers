"use strict";

import { refreshProducts } from './products-helper.js';

(() => {
    const pending = new Set();
    const status = document.createElement("p");
    status.setAttribute("role", "status");
    document.querySelector(".product-list").before(status);

    document.addEventListener("click", async (event) => {
        const button = event.target.closest(".archive");
        if (!button) return;

        event.preventDefault();

        const productId = Number(button.dataset.productId);
        const action = button.dataset.action;

        if (!Number.isInteger(productId) ||
            !["archive", "restore"].includes(action)) {
            status.textContent = "Refresh Products before trying again.";
            return;
        }

        if (pending.has(productId)) return;
        pending.add(productId);
        button.disabled = true;
        status.textContent = action === "restore"
            ? "Restoring product…"
            : "Archiving product…";

        try {
            const response = await fetch(`/api/products/${action}`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ productId })
            });

            const text = await response.text();
            let result = {};
            try {
                result = text ? JSON.parse(text) : {};
            } catch {
                throw new Error("The server returned an unexpected response.");
            }

            if (!response.ok) {
                throw new Error(
                    result.message || "Could not complete the request."
                );
            }

            status.textContent = action === "restore"
                ? "Product restored. Archived subproducts can be restored individually."
                : "Product archived.";

            await refreshProducts();
        } catch (error) {
            status.textContent = error.message || "Could not reach the server.";
        } finally {
            pending.delete(productId);
            button.disabled = false;
        }
    });
})();

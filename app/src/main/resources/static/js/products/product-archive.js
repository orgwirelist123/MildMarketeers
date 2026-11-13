"use strict";

import { refreshProducts } from './products-helper.js';

(() => {
    const pending = new Set();

    document.addEventListener("click", async (event) => {
        const button = event.target.closest(".archive");
        if (!button) return;

        event.preventDefault();

        const productId = Number(button.dataset.productId);
        const action = button.dataset.action;

        if (!Number.isInteger(productId) ||
            !["archive", "restore"].includes(action)) {
            sendNotification("Refresh Products before trying again.");
            return;
        }

        if (pending.has(productId)) return;
        pending.add(productId);
        button.disabled = true;

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

            sendNotification(action === "restore"
                ? "Product restored! Archived subproducts can be restored individually."
                : "Product archived!");

            await refreshProducts();
        } catch (error) {
            sendNotification(error.message || "Could not reach the server.");
        } finally {
            pending.delete(productId);
            button.disabled = false;
        }
    });
})();

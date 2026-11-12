"use strict";

import { refreshProducts } from './products-helper.js';

(() => {
    // For duplicate buttons that are clicked, send a duplicate request
    document.addEventListener("click", (event) => {
        const button = event.target.closest(".duplicate");
        if (!button) return;

        event.preventDefault();

        const targetRow = button.parentElement.parentElement.parentElement.querySelector(".btn-view").closest("[data-product-id]");
        const productId = targetRow?.dataset.productId;

        duplicateProduct(productId);
    });

    async function readResponse(response) {
        const text = await response.text();
        console.log(text);
        let result = {};

        try {
            result = text ? JSON.parse(text) : {};
        } catch {
            throw new Error("The server returned an unexpected response.");
        }

        if (!response.ok) {
            throw new Error(
                result.message ||
                (response.status === 404
                ? "This product no longer exists."
                : "Could not complete the request.")
            );
        }

        return result;
    }

    async function duplicateProduct(productId) {
        const payload = {
            productId: productId
        };

        try {
            const response = await fetch(
                "/api/products/duplicate",
                {
                    method: "PUT",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify(payload)
                }
            );

            await readResponse(response);

            refreshProducts();
        } catch (error) {
            // Preserve entries after an unsuccessful save.
            // message.textContent = error.message || "Could not reach the server.";
        } finally {

        }
    }

})();
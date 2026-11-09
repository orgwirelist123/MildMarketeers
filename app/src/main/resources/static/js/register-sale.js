"use strict";

// Notes:
// 1. Add discount & email field in regsiter sale form UI
(() => {
    // Retrieve 'Register Sale' form elements (inventory, quantity, price, total price)
    const panel = document.querySelector("#panel-register-sale");
    const form = panel.querySelector(".sale-form");

    const productName = form.querySelector(".sale-product-name");
    const productStock = form.querySelector("#sale-stock-num");
    const quantityInput = form.elements.namedItem("quantity");
    const priceInput = form.elements.namedItem("price");
    const totalPrice = form.querySelector(".sale-total-amount");
    const submitButtons = form.querySelectorAll('button[type="submit"]');

    const message = document.createElement("p");
    message.setAttribute("role", "status");
    form.querySelector(".form-footer").before(message);

    let busy = false;
    let ready = true;

    quantityInput.required = true;
    quantityInput.max = "2147483647";
    priceInput.max = "9999999999";

    form.querySelectorAll('button[type="button"]').forEach((button) => {
        button.disabled = true;
    }); 

    function updateControls() {
        submitButtons.forEach((button) => {
            button.disabled = busy || !ready;
        });
        addButton.disabled = busy;
    }

    function showPanel() {
        if (!panel.matches(":popover-open")) panel.showPopover();
    }

    async function readResponse(response) {
        const text = await response.text();
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

    async function openRegister(productId) {
        if (busy) return;

        // Accept IDs supplied by a product row or another page component.
        const rawId = String(productId ?? "").trim();
        const id = Number(rawId);

        if (!/^-?\d+$/.test(rawId) ||
            !Number.isInteger(id) ||
            id < -2147483648 ||
            id > 2147483647) {
                ready = false;
                editingId = null;
                form.reset();
                message.textContent = "This product row needs a valid product ID.";
                updateControls();
                showPanel();
                return;
        }

        ready = false;
        busy = true;
        form.reset();
        updateControls();
        showPanel();

        try {
            // const response = await fetch(`/api/sales/${id}`);
            // const product = await readResponse(response);

            productStock.textContent = 200;

            quantityInput.value = 10;
            priceInput.value = 90;

            ready = true;
            message.textContent = "";
        } catch (error) {
            message.textContent = error.message || "Could not load the product.";
        } finally {
            busy = false;
            updateControls();
        }
    }

    // Opens 'Register Sale' form with corresponding details of selected product
    // Works with rows added later by the Products Page integration.
    document.addEventListener("click", (event) => {
        const button = event.target.closest(".btn-sale");
        if (!button) return;

        event.preventDefault();

        const row = button.closest("[data-product-id]");
        openRegister(button.dataset.productId ?? row?.dataset.productId);
    });

    form.addEventListener("submit", async (event) => {
        event.preventDefault();

        if (busy || !ready || !form.reportValidity()) return;

        const id = editingId;
        const isEditing = id !== null;
        const payload = {
            name: nameInput.value.trim(),
            sellPrice: priceInput.value,
            inventory: Number(quantityInput.value)
        };

        busy = true;
        message.textContent = "Registering sale…";
        updateControls();

        try {
            const response = await fetch(
                isEditing ? `/api/products/${id}` : "/api/products",
                {
                    method: isEditing ? "PUT" : "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify(payload)
                }
            );

            const result = await readResponse(response);

            if (!isEditing) form.reset();
            message.textContent = result.message;

            document.dispatchEvent(
                new CustomEvent("products:changed", {
                    detail: {
                        action: isEditing ? "updated" : "added",
                        productId: id
                    }
                })
            );
        } catch (error) {
            // Preserve entries after an unsuccessful save.
            message.textContent = error.message || "Could not reach the server.";
        } finally {
            busy = false;
            updateControls();
        }
    });

    // Get the products details from the ProductController
    async function getProductDetails(productId) {     
        // Accept IDs supplied by a product row or another page component.
        const rawId = String(productId ?? "").trim();
        const id = Number(rawId);

        try {
            const response = await fetch(`/api/products/${id}`);
            const products = await readResponse(response);
        } catch (error) {
            message.textContent = error.message || "Could not load the product.";
        } finally {
            busy = false;
            updateControls();
        }
        
    }
})();
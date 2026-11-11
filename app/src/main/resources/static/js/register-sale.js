"use strict";

(() => {
    // Retrieve HTML 'Register Sale' pop-up form
    const panel = document.querySelector("#panel-register-sale");
    const form = panel.querySelector(".sale-form");

    // Retrieve HTML 'Register Sale' form element fields (name, inventory, quantity, price, total_price)
    const productName = form.querySelector(".sale-product-name");
    const productStock = form.querySelector("#sale-stock-num");
    const quantityInput = form.elements.namedItem("quantity");
    const priceInput = form.elements.namedItem("price");
    const totalPrice = form.querySelector(".sale-total-amount");
    
    // Retrieve HTML 'Register Sale' form input fields (quantity_inc, quantity_dec, submit_btn)
    const inputButtons = form.querySelectorAll('button[type="button"]');
    const submitButtons = form.querySelectorAll('button[type="submit"]');

    let editingId = null;
    let busy = false;
    let ready = true;

    let inventory = 0;
    let currentUnitPrice = 0;
    let pastUnitsSold = 0;

    quantityInput.required = true;
    quantityInput.max = "2147483647";
    priceInput.max = "9999999999";

    form.querySelectorAll('button[type="button"]').forEach((button) => {
        button.disabled = true;
    }); 

    // Enables or disables input fields/buttons depending on if 'Register Sale' form is ready or not
    function updateControls() {
        inputButtons.forEach((button) => {
            button.disabled = busy || !ready;
        });

        submitButtons.forEach((button) => {
            button.disabled = busy || !ready;
        });        
    }

    // Opens 'Register Sale' form
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

    // Opens 'Sale Register' form and updates form details to match the selected product's details
    async function openRegister(productId) {
        if (busy) return;

        // Accept IDs supplied by a product row or another page component.
        const rawId = String(productId ?? "").trim();
        const id = Number(rawId);

        if (!/^-?\d+$/.test(rawId) || !Number.isInteger(id) ||
            id < -2147483648 || id > 2147483647) {
                editingId = null;
                ready = false;
                form.reset();
                updateControls();
                showPanel();

                return;
        }

        editingId = id;
        ready = false;
        busy = true;
        
        form.reset();
        updateControls();
        showPanel();

        try {
            const response = await fetch(`/api/products/${id}`);
            const product = await readResponse(response);

            productName.textContent = product.name;
            productStock.textContent = product.inventory;
            priceInput.value = product.sellPrice;

            const totalPriceNum = Number(priceInput.value) * Number(quantityInput.value);
            totalPrice.textContent = totalPriceNum.toFixed(2);

            inventory = product.inventory;
            currentUnitPrice = product.sellPrice;
            pastUnitsSold = product.unitsSold;
            
            ready = true;
        } catch (error) {
            console.log(error.message);
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

    // Increases or decreases quantity by 1 by clicking quantity buttons
    form.addEventListener("click", (event) => {
        const button = event.target.closest(".quantity-btn");

        if(!button) return;

        event.preventDefault();

        const numQuantity = Number(quantityInput.value);

        if(button.title === "Increase" && numQuantity < Number(productStock.textContent)) {
            quantityInput.value = numQuantity + 1;
        }
        else if (button.title === "Decrease" && numQuantity > 1) {
            quantityInput.value = numQuantity - 1;
        }      

        updateTotalPrice();
    });

    form.addEventListener("input", (event) => {
        event.preventDefault();
        updateTotalPrice();        
    });

    // Saves the 'Sale' registered and edits product accordingly
    form.addEventListener("submit", async (event) => {
        event.preventDefault();

        if (busy || !ready || !form.reportValidity()) return;

        const id = editingId;
        const isRegistering = id !== null;
        const payload = {
            soldQuantity: Number(quantityInput.value),
            inventory: Number(inventory),
            originalUnitPrice: currentUnitPrice, 
            sellPrice: priceInput.value,
            totalPrice: Number(totalPrice.textContent),
            pastUnitsSold: pastUnitsSold, 
            email: null // Change to take email input when it's added
        };

        busy = true;
        updateControls();

        try {
            if(!isRegistering) return;

            const saleResponse = await fetch(
                `/api/sales/${id}`,
                {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify(payload)
                }
            );

            const saleResult = await readResponse(saleResponse);

            const productResponse = await fetch(
                `/api/sales/update/${id}`,
                {
                    method: "PUT",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify(payload)
                }
            );

            const productResult = await readResponse(productResponse); 

        } catch (error) {
            console.log(error.message);
        } finally {
            busy = false;
            panel.hidePopover();
            form.reset();
            refresh();
            updateControls();
        }
    });

    // Updates total price displayed to match price * quantity
    function updateTotalPrice() {
        const totalPriceUpdate = Number(priceInput.value) * Number(quantityInput.value);
        totalPrice.textContent = totalPriceUpdate.toFixed(2);
    }
})();
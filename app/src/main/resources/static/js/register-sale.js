"use strict";

(() => {
	const panel = document.querySelector("#panel-register-sale");
	const form = panel.querySelector(".sale-form");
	const productName = form.querySelector(".sale-product-name");
	const productThumb = form.querySelector(".thumb");
	const productStock = form.querySelector("#sale-stock-num");
	const quantityInput = form.elements.namedItem("quantity");
	const priceInput = form.elements.namedItem("price");
	const discountInput = form.elements.namedItem("discount");
	const totalPrice = form.querySelector(".sale-total-amount");
	const submitButton = form.querySelector('button[type="submit"]');
	const controls = [...form.querySelectorAll("input, button")];

	const message = document.createElement("p");
	message.className = "status-message";

	message.setAttribute("role", "status");
	message.setAttribute("aria-live", "polite");
	submitButton.before(message);

	let productId = null;
	let stock = 0;
	let busy = false;
	let ready = false;

	priceInput.max = "9999999999.99";
	priceInput.setAttribute("aria-label", "Price per unit before discount");
	quantityInput.required = true;

	// First letter of the product name shown in the thumbnail
	function thumbLetter(name) {
		return Array.from(name.trim())[0]?.toUpperCase() ?? '?';
	}

	// Use integer cents to avoid floating-point rounding.
	function cents(value) {
		if (!/^\d+(\.\d{1,2})?$/.test(value)) return null;
		const [whole, fraction = ""] = value.split(".");
		return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, "0"));
	}

	function decimal(value) {
		return `${value / 100n}.${String(value % 100n).padStart(2, "0")}`;
	}

	function updateControls() {
		controls.forEach((control) => {
			control.disabled = busy || !ready;
		});
	}

	function updateTotal() {
		discountInput.setCustomValidity("");

		const quantity = Number(quantityInput.value);
		const price = cents(priceInput.value);
		const discount = cents(discountInput.value);

		if (!Number.isInteger(quantity) || quantity < 1 ||
				quantity > 2147483647 || price === null || discount === null) {
			totalPrice.textContent = "—";
			return;
		}

		const subtotal = BigInt(quantity) * price;
		if (discount > subtotal) {
			discountInput.setCustomValidity("Discount cannot exceed the sale subtotal.");
			totalPrice.textContent = "—";
			return;
		}

		totalPrice.textContent = decimal(subtotal - discount);
	}

	async function readResponse(response) {
		const text = await response.text();
		let result;

		try {
			result = text ? JSON.parse(text) : {};
		} catch {
			throw new Error("The server returned an unexpected response.");
		}

		if (!response.ok) {
			throw new Error(result.message || "Could not complete the request.");
		}

		return result;
	}

	async function openRegister(rawId) {
		if (busy) return;

		const value = String(rawId ?? "").trim();
		const id = Number(value);

		form.reset();
		discountInput.setCustomValidity("");
		ready = false;
		productId = null;
		productName.textContent = "";
		productThumb.textContent = "";
		productStock.textContent = "—";
		totalPrice.textContent = "—";
		message.textContent = "";
		updateControls();

		if (!panel.matches(":popover-open")) panel.showPopover();

		if (!/^-?\d+$/.test(value) || !Number.isInteger(id) ||
				id < -2147483648 || id > 2147483647) {
			message.textContent = "This product row needs a valid product ID.";
			return;
		}

		busy = true;
		message.textContent = "Loading product…";
		updateControls();

		try {
			const response = await fetch(`/api/products/${id}`, { cache: "no-store" });
			const product = await readResponse(response);

			if (!Number.isInteger(product.inventory) || product.inventory < 0 ||
					cents(String(product.sellPrice)) === null) {
				throw new Error("The server returned invalid product details.");
			}

			productId = id;
			stock = product.inventory;
			productName.textContent = product.name;
			productThumb.textContent = thumbLetter(product.name);
			productStock.textContent = stock;
			quantityInput.max = String(stock);
			quantityInput.value = "1";
			priceInput.value = Number(product.sellPrice).toFixed(2);
			discountInput.value = "0.00";

			ready = stock > 0;
			message.textContent = ready ? "" : "This product is out of stock.";
			updateTotal();
		} catch (error) {
			message.textContent = error.message || "Could not load the product.";
		} finally {
			busy = false;
			updateControls();
		}
	}

	document.addEventListener("click", (event) => {
		const button = event.target.closest(".btn-sale");
		if (!button) return;

		event.preventDefault();
		const row = button.closest("[data-product-id]");
		openRegister(button.dataset.productId ?? row?.dataset.productId);
	});

	form.addEventListener("click", (event) => {
		const button = event.target.closest(".quantity-btn");
		if (!button || busy || !ready) return;

		event.preventDefault();
		const quantity = Number(quantityInput.value);
		if (!Number.isInteger(quantity)) return;

		if (button.title === "Increase" && quantity < stock) {
			quantityInput.value = quantity + 1;
		} else if (button.title === "Decrease" && quantity > 1) {
			quantityInput.value = quantity - 1;
		}

		updateTotal();
	});

	form.addEventListener("input", updateTotal);

	form.addEventListener("submit", async (event) => {
		event.preventDefault();
		if (busy || !ready || productId === null) return;

		updateTotal();
		if (!form.reportValidity()) return;

		const payload = {
			quantity: Number(quantityInput.value),
			unitPrice: priceInput.value,
			discount: discountInput.value,
			email: null
		};

		busy = true;
		message.textContent = "Registering sale…";
		updateControls();

		try {
			const response = await fetch(`/api/sales/${productId}`, {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify(payload)
			});

			await readResponse(response);
		} catch (error) {
			// Preserve entered values when saving fails.
			message.textContent =
				`${error.message || "Could not reach the server."} ` +
				"If the result is uncertain, check Sales before submitting again.";
			busy = false;
			updateControls();
			return;
		}

		// Saving has succeeded; UI notification errors must not imply save failure.
		busy = false;
		ready = false;
		updateControls();
		if (panel.matches(":popover-open")) panel.hidePopover();
		form.reset();
		message.textContent = "";

		document.dispatchEvent(new CustomEvent("products:changed"));
		document.dispatchEvent(new CustomEvent("sales:changed"));

		try {
			if (typeof sendNotification === "function") {
				sendNotification("Sale registered successfully!");
			}
		} catch (error) {
			console.error("Could not display sale notification:", error);
		}
	});

	updateControls();
})();

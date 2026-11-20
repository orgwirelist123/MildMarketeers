"use strict";

import { refreshProducts } from './products-helper.js';
import { confirmAction } from '../util/confirm.js';

(() => {
	// For delete buttons that are clicked, send a delete request
	document.addEventListener("click", async (event) => {
		const button = event.target.closest(".delete");
		if (!button) return;

		event.preventDefault();

		const targetRow = button.parentElement.parentElement.parentElement.querySelector(".btn-view").closest("[data-product-id]");
		const productId = targetRow?.dataset.productId;
		const name = button.closest(".row").querySelector(".name").textContent;

		const hasSales = Number(button.dataset.unitsSold) > 0;
		const hasSubproducts = button.closest(".product").classList.contains("product-group");

		button.closest("details").open = false;

		if (hasSales) {
			sendNotification(`"${name}" has recorded sales, so it cannot be deleted. Archive it instead.`);
			return;
		}

		if (hasSubproducts) {
			sendNotification(`"${name}" has subproducts, so it cannot be deleted. Archive it instead.`);
			return;
		}

		const confirmed = await confirmAction({
			title: "Delete Product",
			message: `Delete "${name}"? This cannot be undone.`,
			confirmLabel: "Delete product",
			danger: true
		});

		if (confirmed) {
			deleteProduct(productId);
		}
	});

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

	async function deleteProduct(productId) {
		const payload = {
			productId: productId
		};

		try {
			const response = await fetch(
				"/api/products/delete",
				{
					method: "PUT",
					headers: { "Content-Type": "application/json" },
					body: JSON.stringify(payload)
				}
			);

			await readResponse(response);

			refreshProducts();
			sendNotification("Product deleted successfully!");
		} catch (error) {
			sendNotification(error.message || "Could not reach the server.");
		} finally {

		}
	}

})();
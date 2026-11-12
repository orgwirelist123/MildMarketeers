"use strict";

import { makeRequest, readResponse, GetDefaultHeaderType, ERROR_MESSAGES } from './util/network-helper.js';

(() => {
  const parentProductTemplate = document.getElementById('template-parent-option');

  const panel = document.querySelector("#panel-product");
  const form = panel.querySelector(".product-form");
  const addButton = document.querySelector(
    '.header-actions [popovertarget="panel-product"]'
  );
  const parentList = document.querySelector(".parent-list");
  const parentSelected = document.querySelector(".parent-selected");

  const nameInput = form.elements.namedItem("name");
  const quantityInput = form.elements.namedItem("quantity");
  const priceInput = form.elements.namedItem("price");
  const subproductInput = form.elements.namedItem("is-subproduct");

  const submitButtons = form.querySelectorAll('button[type="submit"]');

  const message = document.createElement("p");
  message.setAttribute("role", "status");
  form.querySelector(".form-footer").before(message);

  let editingId = null;
  let busy = false;
  let ready = true;
  let availableParents = {};
  let parentId = 0;

  nameInput.maxLength = 50;
  quantityInput.max = "2147483647";
  priceInput.required = true;
  priceInput.step = "0.01";
  priceInput.max = "9999999999.99";

  // These are the fields currently supported by the backend.
  const supportedInputs = [nameInput, quantityInput, priceInput, subproductInput];

  form.querySelectorAll("input").forEach((input) => {
    if (!supportedInputs.includes(input)) {
      input.disabled = true;
    }
  });

  form.querySelectorAll('button[type="button"]').forEach((button) => {
    button.disabled = true;
  });

  const note = document.createElement("p");
  note.textContent =
    "Currently saves name, quantity, and price. Prices can have up to " +
    "two decimal places. Other product details are not connected yet.";

  function updateControls() {
    supportedInputs.forEach((input) => {
      input.disabled = busy || !ready;
    });
    submitButtons.forEach((button) => {
      button.disabled = busy || !ready;
    });
    addButton.disabled = busy;
  }

  function showPanel(productId = 0) {
    if (!panel.matches(":popover-open")) {
      panel.showPopover();

      // When displaying the panel, also refresh the subproduct dropdown in the product panel
      // Get the valid parent products
      try {
        const url = '/api/products/listAvailableParents/' + productId;
        fetch(url).then((response) => {
          readResponse(
            response,
            ERROR_MESSAGES.GENERAL.INVALID_RESPONSE, 
            ERROR_MESSAGES.PRODUCT.MISSING_PRODUCT, 
            ERROR_MESSAGES.GENERAL.REQUEST_FAILURE
          ).then((products) => {
            // Clear the previous .parent-list
            parentList.innerHTML = "";
            availableParents = {};

            // Include a 'none' option for products without parents
            products.unshift({
              productId: 0,
              name: "None"
            });

            // Generate the new button entries
            products.forEach(product => {
              availableParents[product.productId] = product.name;

              // Create a new template instance for this product
              const parentEntry = parentProductTemplate.content.cloneNode(true);
              const parentOption = parentEntry.querySelector(".parent-option");
              
              // Attach the parent's product id
              parentOption.dataset.productId = product.productId;

              // Fill in the name
              parentOption.textContent = product.name;

              // Append the new template instance to the product-list
              parentList.appendChild(parentEntry);
            });
          })
        })
      } catch (error) {
        message.textContent = error.message || "Could not load the product.";
      } finally {
        busy = false;
        updateControls();
      }
    }
  }

  // async function readResponse(response) {
  //   const text = await response.text();
  //   let result = {};

  //   try {
  //     result = text ? JSON.parse(text) : {};
  //   } catch {
  //     throw new Error("The server returned an unexpected response.");
  //   }

  //   if (!response.ok) {
  //     throw new Error(
  //       result.message ||
  //       (response.status === 404
  //         ? "This product no longer exists."
  //         : "Could not complete the request.")
  //     );
  //   }

  //   return result;
  // }

  function setTargetParent(productId) {
    parentSelected.textContent = availableParents[productId];
    parentId = productId;
  }

  function clearTargetParent() {
    setTargetParent(0);
  }

  function openAdd() {
    if (busy) return;

    editingId = null;
    ready = true;
    panel.classList.remove("is-viewing");
    form.reset();
    message.textContent = "";
    clearTargetParent();
    updateControls();
    showPanel();
  }

  async function openEdit(productId) {
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
      panel.classList.add("is-viewing");
      message.textContent = "This product row needs a valid product ID.";
      updateControls();
      showPanel(productId);
      return;
    }

    editingId = id;
    ready = false;
    busy = true;
    panel.classList.add("is-viewing");
    form.reset();
    message.textContent = "Loading product…";
    updateControls();
    showPanel(productId);

    try {
      const response = await fetch(`/api/products/${id}`);
      const product = await readResponse(
        response, 
        ERROR_MESSAGES.GENERAL.INVALID_RESPONSE, 
        ERROR_MESSAGES.PRODUCT.MISSING_PRODUCT, 
        ERROR_MESSAGES.GENERAL.REQUEST_FAILURE
      );

      nameInput.value = product.name;
      quantityInput.value = product.inventory;
      priceInput.value = product.sellPrice;

      setTargetParent(product.parentId);

      ready = true;
      message.textContent = "";
    } catch (error) {
      message.textContent = error.message || "Could not load the product.";
    } finally {
      busy = false;
      updateControls();
    }
  }

  addButton.addEventListener("click", (event) => {
    event.preventDefault();
    openAdd();
  });

  // Works with rows added later by the Products Page integration.
  document.addEventListener("click", (event) => {
    const button = event.target.closest(".btn-view");
    if (!button) return;

    event.preventDefault();

    const row = button.closest("[data-product-id]");
    openEdit(button.dataset.productId ?? row?.dataset.productId);
  });

  document.addEventListener("click", (event) => {
    const button = event.target.closest(".parent-option");
    if (!button) return;

    event.preventDefault();

    setTargetParent(button.dataset.productId);
  })

  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    if (busy || !ready || !form.reportValidity()) return;

    const id = editingId;
    const isEditing = id !== null;
    const payload = {
      name: nameInput.value.trim(),
      sellPrice: priceInput.value,
      inventory: Number(quantityInput.value),
      parentId: parentId
    };

    busy = true;
    message.textContent = "Saving product…";
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

      await readResponse(response);

      // Close the Add/Edit popup only after the server confirms success.
      if (panel.matches(":popover-open")) {
        panel.hidePopover();
      }

      form.reset();
      editingId = null;
      message.textContent = "";

      // Notification failure must not turn a successful save into a save error.
      try {
        sendNotification(
          isEditing
            ? "Product successfully updated!"
            : "Product successfully added!"
        );
      } catch (notificationError) {
        console.error("Could not display the success notification:", notificationError);
      }

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

  // Allows other page components to open the shared product form.
  window.productFormIntegration = {
    openAdd,
    openEdit
  };
})();
"use strict";

(() => {
  const panel = document.querySelector("#panel-product");
  const form = panel.querySelector(".product-form");
  const addButton = document.querySelector(
    '.header-actions [popovertarget="panel-product"]'
  );

  const nameInput = form.elements.namedItem("name");
  const quantityInput = form.elements.namedItem("quantity");
  const priceInput = form.elements.namedItem("price");
  const submitButtons = form.querySelectorAll('button[type="submit"]');

  const message = document.createElement("p");
  message.setAttribute("role", "status");
  form.querySelector(".form-footer").before(message);

  let editingId = null;
  let busy = false;
  let ready = true;

  nameInput.maxLength = 50;
  quantityInput.max = "2147483647";
  priceInput.required = true;
  priceInput.step = "0.01";
  priceInput.max = "9999999999.99";

  // These are the fields currently supported by the backend.
  const supportedInputs = [nameInput, quantityInput, priceInput];

  form.querySelectorAll("input").forEach((input) => {
    if (!supportedInputs.includes(input)) input.disabled = true;
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

  function openAdd() {
    if (busy) return;

    editingId = null;
    ready = true;
    panel.classList.remove("is-viewing");
    form.reset();
    message.textContent = "";
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
      showPanel();
      return;
    }

    editingId = id;
    ready = false;
    busy = true;
    panel.classList.add("is-viewing");
    form.reset();
    message.textContent = "Loading product…";
    updateControls();
    showPanel();

    try {
      const response = await fetch(`/api/products/${id}`);
      const product = await readResponse(response);

      nameInput.value = product.name;
      quantityInput.value = product.inventory;
      priceInput.value = product.sellPrice;

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
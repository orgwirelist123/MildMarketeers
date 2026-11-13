"use strict";
import { createTagEditor } from "./tag-form.js";

import { makeRequest, readResponse, GetDefaultHeaderType, ERROR_MESSAGES } from './util/network-helper.js';

(() => {
  const parentProductTemplate = document.getElementById('template-parent-option');
  const componentTemplate = document.getElementById('template-component');

  const panel = document.querySelector("#panel-product");
  const form = panel.querySelector(".product-form");
  const addButton = document.querySelector(
    '.header-actions [popovertarget="panel-product"]'
  );
  const parentList = document.querySelector(".parent-list");
  const parentSelected = document.querySelector(".parent-selected");
  const parentSearch = form.querySelector('.parent-picker input[type="search"]');
  const parentSearchStatus = document.createElement("p");
  parentSearchStatus.setAttribute("role", "status");
  parentSearchStatus.hidden = true;
  parentList.after(parentSearchStatus);

  const nameInput = form.elements.namedItem("name");
  const quantityInput = form.elements.namedItem("quantity");
  const priceInput = form.elements.namedItem("price");
  const subproductInput = form.elements.namedItem("is-subproduct");

  const componentList = document.querySelector(".component-list");

  // Suggestions use a 50% markup, rounded to the nearest cent.
  const componentTotal = document.querySelector(".component-total");
  // const componentNameInput = document.querySelector("[name=component-name]");
  // const componentPriceInput = document.querySelector("[name=component-price-label]");
  // const componentPriceActualInput = document.querySelector("[name=component-price]");
  const addComponentButton = document.querySelector(".add-component");
  const suggestionAmount = document.querySelector(".suggestion-amount");
  const suggestionUseButton = document.querySelector(".suggestion-use");

  const submitButtons = form.querySelectorAll('button[type="submit"]');

  const message = document.createElement("p");
  message.setAttribute("role", "status");
  form.querySelector(".form-footer").before(message);

  const tagEditor = createTagEditor(form);
  tagEditor.reset();

  let editingId = null;
  let busy = false;
  let ready = true;
  let availableParents = {};
  let parentId = 0;
  let components = [];
  let suggestedAmount = 0;
  let inheritedCost = 0n;
  let parentCostLoading = false;
  let parentCostError = false;
  let parentRequest = 0;

  const inheritedNote = document.createElement("p");
  inheritedNote.setAttribute("role", "status");
  inheritedNote.hidden = true;
  componentList.before(inheritedNote);

  nameInput.maxLength = 50;
  quantityInput.max = "2147483647";
  priceInput.required = true;
  priceInput.step = "0.01";
  priceInput.max = "9999999999.99";

  // Enable saved fields and the local parent-search control when the form is ready.
  const supportedInputs = [nameInput, quantityInput, priceInput, subproductInput, parentSearch];

  form.querySelectorAll("input").forEach((input) => {
    if (!supportedInputs.includes(input)) {
      input.disabled = true;
    }
  });

  form.querySelectorAll('button[type="button"]').forEach((button) => {
    button.disabled = true;
  });

  suggestionUseButton.disabled = false;

  const note = document.createElement("p");
  note.textContent =
    "Currently saves name, quantity, and price. Prices can have up to " +
    "two decimal places. Other product details are not connected yet.";

  function updateControls() {
    tagEditor.setDisabled(busy || !ready);
    supportedInputs.forEach((input) => {
      input.disabled = busy || !ready;
    });
    submitButtons.forEach((button) => {
      button.disabled = busy || !ready || parentCostLoading || parentCostError;
    });
    addButton.disabled = busy;
    addComponentButton.disabled = busy || !ready;
    suggestionUseButton.disabled = busy || !ready || parentCostLoading || parentCostError || suggestedAmount === null;
    componentList.querySelectorAll("input, button").forEach((control) => {
      control.disabled = busy || !ready;
    });
    parentList.querySelectorAll("button").forEach((button) => {
      button.disabled = busy || !ready;
    });
  }


  function filterParentProducts() {
    const query = parentSearch.value.trim().toLowerCase();
    let matches = 0;
    parentList.querySelectorAll(".parent-option").forEach((option) => {
      const isNone = Number(option.dataset.productId) === 0;
      const matchesQuery = option.textContent.toLowerCase().includes(query);
      // Keep the explicit no-parent option available while searching.
      option.style.display = isNone || matchesQuery ? "" : "none";
      if (!isNone && matchesQuery) matches++;
    });
    parentSearchStatus.textContent = matches ? "" : "No matching parent products.";
    parentSearchStatus.hidden = matches > 0;
  }

  parentSearch.addEventListener("input", filterParentProducts);
  parentSearch.addEventListener("keydown", (event) => {
    if (event.key === "Enter") event.preventDefault();
  });

  async function refreshParentProducts(productId = 0) {
    const response = await fetch(`/api/products/listAvailableParents/${productId}`);
    const products = await readResponse(response);
    if (!Array.isArray(products)) {
      throw new Error("Could not load available parent products.");
    }
    parentList.innerHTML = "";
    availableParents = {0: "None"};
    [{productId: 0, name: "None"}, ...products].forEach((product) => {
      availableParents[product.productId] = product.name;
      const entry = parentProductTemplate.content.cloneNode(true);
      const option = entry.querySelector(".parent-option");
      option.dataset.productId = product.productId;
      option.textContent = product.name;
      parentList.appendChild(entry);
    });
    filterParentProducts();
  }

  function showPanel() {
    if (!panel.matches(":popover-open")) panel.showPopover();
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

  // Keep inherited costs separate: only editable, local components are saved.
  async function setTargetParent(productId) {
    const request = ++parentRequest;
    parentId = Number(productId ?? 0);
    subproductInput.checked = parentId !== 0;
    subproductInput.setCustomValidity("");
    parentSelected.textContent = availableParents[parentId] ?? `Product #${parentId}`;
    inheritedCost = 0n;
    parentCostError = false;
    parentCostLoading = parentId !== 0;
    inheritedNote.hidden = parentId === 0;
    inheritedNote.textContent = parentCostLoading ? "Loading inherited component costs…" : "";
    calculateCostPerUnit();
    updateControls();
    if (!parentCostLoading) return;

    try {
      const response = await fetch(`/api/materials/list/${parentId}`);
      const materials = await readResponse(response);
      if (!Array.isArray(materials)) throw new Error("Invalid parent materials response.");
      const total = materials.reduce((sum, material) => sum + moneyToCents(material.materialCost), 0n);
      if (request !== parentRequest) return;
      inheritedCost = total;
      inheritedNote.textContent = `Inherited from ${parentSelected.textContent}: ${formatMoney(total)} per unit. Edit these components on the parent product.`;
    } catch (error) {
      if (request !== parentRequest) return;
      parentCostError = true;
      inheritedNote.textContent = "Could not load the parent's component costs. Select the parent again to retry.";
    } finally {
      if (request === parentRequest) {
        parentCostLoading = false;
        calculateCostPerUnit();
        updateControls();
      }
    }
  }

  function resetComponentState() {
    parentSearch.value = "";
    parentSearchStatus.hidden = true;
    ++parentRequest; // Ignore responses belonging to a previous form session.
    parentId = 0;
    subproductInput.checked = false;
    subproductInput.setCustomValidity("");
    parentSelected.textContent = "None";
    inheritedCost = 0n;
    parentCostLoading = false;
    parentCostError = false;
    inheritedNote.hidden = true;
    inheritedNote.textContent = "";
    clearComponents();
    calculateCostPerUnit();
  }

  function clearComponents() {
    componentList.innerHTML = "";
    components = [];
  }

  function moneyToCents(value) {
    const text = String(value ?? "").trim();
    if (text === "") return 0n;
    if (!/^\d+(?:\.\d{0,2})?$/.test(text)) {
      throw new Error("Component costs must be nonnegative amounts with up to two decimal places.");
    }
    const [whole, fraction = ""] = text.split(".");
    return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, "0"));
  }

  function decimalMoney(cents) {
    return `${cents / 100n}.${String(cents % 100n).padStart(2, "0")}`;
  }

  function formatMoney(cents) {
    const [whole, fraction] = decimalMoney(cents).split(".");
    return `$${BigInt(whole).toLocaleString("en-US")}.${fraction}`;
  }

  function calculateCostPerUnit() {
    let sum = inheritedCost;
    let valid = !parentCostLoading && !parentCostError;
    components.forEach((entry) => {
      const input = entry.querySelector('[name="component-price"]');
      input.setCustomValidity("");
      try {
        if (input.validity.badInput) throw new Error("Enter a valid component cost.");
        sum += moneyToCents(input.value);
      } catch (error) {
        input.setCustomValidity(error.message);
        valid = false;
      }
    });
    suggestedAmount = valid ? decimalMoney((sum * 3n + 1n) / 2n) : null;
    componentTotal.firstElementChild.textContent = valid ? formatMoney(sum) : "—";
    suggestionAmount.textContent = valid ? formatMoney((sum * 3n + 1n) / 2n) : "—";
    suggestionUseButton.disabled = busy || !ready || !valid;
  }

  function renderComponents(componentDatas) {
    componentDatas.forEach(componentData => {
      // Create an entry
      let entry = addComponent();

      // Fill in the data
      let name = entry.querySelector('[name="component-name"]');
      name.value = componentData.materialName;

      let price = entry.querySelector('[name="component-price"]');
      price.value = componentData.materialCost;
      price.placeholder = componentData.materialCost;

      console.log(componentDatas);
    });

    calculateCostPerUnit();
  }

  function refreshComponents(componentDatas) {
    clearComponents();
    renderComponents(componentDatas);
  }

  function getComponentsData() {
    let datas = []

    components.forEach(component => {
      let name = component.querySelector('[name="component-name"]').value;
      let price = component.querySelector('[name="component-price"]').value;

      if (name.length != 0) {
        let data = {
          materialName: name,
          materialCost: price
        };

        datas.push(data);
      }
    });

    // console.log("Components Data:");
    // console.log(datas);

    return datas;
  }

  function addComponent() {
    // Add a new component entry
    const componentEntry = componentTemplate.content.cloneNode(true);
    let child = componentEntry.firstElementChild
    components.push(child);
    componentList.appendChild(componentEntry);
    return child;
  }

  function removeComponent(button) {
    let parent = button.parentElement;
    let index = components.indexOf(parent);
    if (index == -1) {
      console.log("Attempted to remove a component that doesn't exist.");
      return false;
    }
    console.log(index);
    components.splice(index, 1);
    componentList.removeChild(parent);

    calculateCostPerUnit();

    // refreshComponents();
  }

  function setPriceToSuggestion() {
    if (busy || !ready || parentCostLoading || parentCostError || suggestedAmount === null) return;
    priceInput.value = suggestedAmount;
  }

  async function openAdd(targetParentId = 0) {
    if (busy) return;
    editingId = null;
    ready = false;
    busy = true;
    panel.classList.remove("is-viewing");
    form.reset();
    resetComponentState();
    tagEditor.reset();
    message.textContent = "Loading product form…";
    updateControls();
    showPanel();
    try {
      await tagEditor.load();
      await refreshParentProducts();
      await setTargetParent(targetParentId);
      ready = true;
      message.textContent = "";
    } catch (error) {
      message.textContent = error.message || "Could not load the product form.";
    } finally {
      busy = false;
      updateControls();
    }
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
    resetComponentState();
    tagEditor.reset();
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

      await tagEditor.load(id);
      await refreshParentProducts(id);
      await setTargetParent(product.parentId);

      const responseComponents = await fetch(`/api/materials/list/${id}`);
      const productComponents = await readResponse(
        responseComponents,
        ERROR_MESSAGES.GENERAL.INVALID_RESPONSE,
        ERROR_MESSAGES.PRODUCT.MISSING_PRODUCT,
        ERROR_MESSAGES.GENERAL.REQUEST_FAILURE
      );
      console.log(productComponents);
      if (!Array.isArray(productComponents)) throw new Error("Could not load product components.");
      refreshComponents(productComponents);

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

    if (busy || !ready) return;
    void setTargetParent(button.dataset.productId);
    parentSelected.closest("details").open = false;
  });

  document.addEventListener("click", (event) => {
    const button = event.target.closest(".add-component");
    if (!button) return;

    event.preventDefault();

    if (busy || !ready) return;
    addComponent();
    calculateCostPerUnit();
  });

  document.addEventListener("click", (event) => {
    const button = event.target.closest("[title='Remove component']");
    if (!button) return;

    console.log("Clicked remove component")
    event.preventDefault();

    if (busy || !ready) return;
    removeComponent(button);
  });

  document.addEventListener("input", (event) => {
    const input = event.target.closest("[name='component-price']");
    if (!input) return;

    calculateCostPerUnit();
  })

  document.addEventListener("click", (event) => {
    const button = event.target.closest(".suggestion-use");
    if (!button) return;

    setPriceToSuggestion();
  })

  document.addEventListener("click", (event) => {
    const button = event.target.closest(".add-subproduct");
    if (!button) return;
    event.preventDefault();
    void openAdd(button.dataset.productId);
  });

  subproductInput.addEventListener("change", () => {
    if (!subproductInput.checked) {
      void setTargetParent(0);
    } else if (parentId === 0) {
      subproductInput.setCustomValidity("Choose a parent product or uncheck this box.");
      parentSelected.closest("details").open = true;
    }
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    if (busy || !ready || parentCostLoading || parentCostError || !form.reportValidity()) return;

    const id = editingId;
    const isEditing = id !== null;
    const payload = {
      name: nameInput.value.trim(),
      sellPrice: priceInput.value,
      inventory: Number(quantityInput.value),
      parentId: parentId,
      tags: tagEditor.values(),
      tagColors: tagEditor.colors(),
      materials: getComponentsData()
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

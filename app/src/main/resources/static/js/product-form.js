"use strict";

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

  const nameInput = form.elements.namedItem("name");
  const quantityInput = form.elements.namedItem("quantity");
  const priceInput = form.elements.namedItem("price");
  const subproductInput = form.elements.namedItem("is-subproduct");

  const componentList = document.querySelector(".component-list");

  const SUGGESTION_MULTIPLIER = 1.5;
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

  let editingId = null;
  let busy = false;
  let ready = true;
  let availableParents = {};
  let parentId = 0;
  let components = [];
  let suggestedAmount = 0;
  let setParentForDirectAddSubproduct = false;

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

  suggestionUseButton.disabled = false;

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
    addComponentButton.disabled = busy;
  }

  
  function refreshParentProducts(productId = 0, targetParentId = 0) {
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

          if (targetParentId != 0) {
            setTargetParent(targetParentId);
          }
        })
      })
    } catch (error) {
      message.textContent = error.message || "Could not load the product.";
    } finally {
      busy = false;
      updateControls();
    }
  }

  function showPanel(productId) {
    if (!panel.matches(":popover-open")) {
      panel.showPopover();

      refreshParentProducts(productId);
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

  function clearComponents() {
    componentList.innerHTML = "";
    components = [];
  }

  function calculateCostPerUnit() {
    let sum = 0;
    console.log(components);
    components.forEach(componentEntry => {
      let price = componentEntry.querySelector('[name="component-price"]');
      sum += parseFloat(price.value);
    });
    console.log("Sum: " + sum);

    sum = sum.toFixed(2);

    componentTotal.firstElementChild.textContent =
      new Intl.NumberFormat("en-US", {
          style: "currency",
          currency: "USD"
      }).format(sum);

    suggestedAmount = (sum * SUGGESTION_MULTIPLIER).toFixed(2);

    suggestionAmount.textContent = 
      new Intl.NumberFormat("en-US", {
          style: "currency",
          currency: "USD"
      }).format(suggestedAmount);
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
    console.log("Setting price to suggestion");
    console.log(suggestedAmount);
    priceInput.value = suggestedAmount;
    console.log(priceInput.value);
  }

  function openAdd() {
    if (busy) return;

    editingId = null;
    ready = true;
    panel.classList.remove("is-viewing");
    form.reset();
    message.textContent = "";

    if (!setParentForDirectAddSubproduct) {
      console.log("cleared target parent");
      clearTargetParent();
    }
    setParentForDirectAddSubproduct = false;

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

      const responseComponents = await fetch(`/api/materials/list/${id}`);
      const productComponents = await readResponse(
        responseComponents, 
        ERROR_MESSAGES.GENERAL.INVALID_RESPONSE, 
        ERROR_MESSAGES.PRODUCT.MISSING_PRODUCT, 
        ERROR_MESSAGES.GENERAL.REQUEST_FAILURE
      );
      console.log(productComponents);
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

    setTargetParent(button.dataset.productId);
  });

  document.addEventListener("click", (event) => {
    const button = event.target.closest(".add-component");
    if (!button) return;

    event.preventDefault();

    // Add a new component entry
    addComponent();
  });

  document.addEventListener("click", (event) => {
    const button = event.target.closest("[title='Remove component']");
    if (!button) return;

    console.log("Clicked remove component")
    event.preventDefault();

    // Add a new component entry
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

    // event.preventDefault();
    refreshParentProducts(0, button.dataset.productId);
    setParentForDirectAddSubproduct = true;
    // console.log("adding a subproduct for parent: " + button.dataset.productId);
    // setTargetParent(button.dataset.productId);
    // console.log(parentId);
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    if (busy || !ready || !form.reportValidity()) return;

    const id = editingId;
    const isEditing = id !== null;
    const payload = {
      name: nameInput.value.trim(),
      sellPrice: priceInput.value,
      inventory: Number(quantityInput.value),
      parentId: parentId,
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
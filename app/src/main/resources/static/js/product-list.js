"use strict";

import { displayProducts, refreshProducts } from './products/products-helper.js';
import { readResponse, ERROR_MESSAGES } from './util/network-helper.js'

(() => {
    // const productList = document.querySelector('.product-list');
    // const template = document.getElementById('template-product');
    const refreshButton = document.querySelector('.header-actions [title="Refresh"]');
    const searchBar = document.querySelector('input[type="search"]');

    /* EVENT LISTENERS */
    // Refresh button input
    refreshButton.addEventListener("click", (event) => {
        event.preventDefault();
        refreshProducts();
    });

    // Search bar input
    searchBar.addEventListener('input', (event) =>{
        const query = event.target.value;
        search(query);
    });

    // Reload the list after a product is added or edited.
    document.addEventListener("products:changed", () => {
        refreshProducts().catch(console.error);
    });

    // // Displays all the products in the given array of ProductDetails
    // async function displayProducts(products){
    //     try{
    //         // Clear the previously displayed products
    //         clearDisplay();
    //         console.log(products);
    //         // Go through each ProductDetails in the list
    //         products.forEach(product => {
    //             // Create a new template instance for this product
    //             const productEntry = template.content.cloneNode(true);
    //             const viewButton = productEntry.querySelector(".btn-view");
    //             if (viewButton) {
    //                 viewButton.dataset.productId = product.productId;
    //             }

    //             const registerButton = productEntry.querySelector(".btn-sale");
    //             if(registerButton) {
    //                 registerButton.dataset.productId = product.productId;
    //             }

    //             // Fill in data to their appropriate positions
    //             productEntry.querySelector('.name').textContent = product.name;
    //             productEntry.querySelector('.stock').textContent = product.inventory;
    //             productEntry.querySelector('.price').textContent =
    //                 new Intl.NumberFormat("en-US", {
    //                     style: "currency",
    //                     currency: "USD"
    //                 }).format(product.sellPrice);

    //             if (product.archived) {
    //                 // console.log(productEntry.querySelectorAll());
    //                 // console.log(productEntry.children);
    //                 productEntry.querySelector('.product').classList.add("archived")
    //             }

    //             // Append the new template instance to the product-list
    //             productList.appendChild(productEntry);
    //         });
    //     } catch (error) {
    //         console.log(error.message);
    //     }
    // }

    // async function readResponse(response) {
    //     const text = await response.text();
    //     let result = {};

    //     try {
    //     result = text ? JSON.parse(text) : {};
    //     } catch {
    //     throw new Error("The server returned an unexpected response.");
    //     }

    //     console.log("Result has been constructed! Checking response status...");
    //     if (!response.ok) {
    //     throw new Error(
    //         result.message ||
    //         (response.status === 404
    //         ? "This product no longer exists."
    //         : "Could not complete the request.")
    //     );
    //     }
    //     return result;
    // }

        // Add an event listener to the refresh button.
    refreshButton.addEventListener("click", (event) => {
        event.preventDefault();
        refreshProducts();
    });

    // async function refresh()
    // {
    //     // Get the products from the ProductController
    //     const response = await fetch(`/api/products/list`);
    //     const products = await readResponse(response);
    //     displayProducts(products);
    // }

    // function clearDisplay()
    // {
    //     productList.innerHTML = "";
    // }

    // Reload the list after a product is added or edited.
    document.addEventListener("products:changed", () => {
        refreshProducts().catch(console.error);
    });
    /* HELPER FUNCTIONS */
    // function clearDisplay(){
    //     productList.innerHTML = "";
    // }

    /* FETCHING FUNCTIONS */
    // async function refresh(){
    //     // Get the products from the ProductController
    //     const response = await fetch(`/api/products/list`);
    //     const products = await readResponse(response);
    //     displayProducts(products);
    // }

    async function search(query){
        // If the search bar input is empty refresh to show all products
        // otherwise query the database:
        if(query != ""){
            const response = await fetch(`/api/products/search`, {
                                        method: "PUT",
                                        body: query});
            const products = await readResponse(
                response,
                ERROR_MESSAGES.GENERAL.INVALID_RESPONSE, 
                ERROR_MESSAGES.PRODUCT.MISSING_PRODUCT, 
                ERROR_MESSAGES.GENERAL.REQUEST_FAILURE
            );

            // Display the queries products to the main page
            displayProducts(products.data);
        } else {
            refreshProducts();
        }
    }

    // Load saved products when the page first opens.
    refreshProducts().catch(console.error);
    
})();
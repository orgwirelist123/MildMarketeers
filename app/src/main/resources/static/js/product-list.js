"use strict";

(() => {
    const productList = document.querySelector('.product-list');
    const template = document.getElementById('template-product');
    const parentTemplate = document.getElementById('template-product-group');
    const subTemplate = document.getElementById('template-subproduct');
    const refreshButton = document.querySelector('.header-actions [title="Refresh"]');
    const searchBar = document.querySelector('input[type="search"]');

    // Displays all the products in the given array of ProductDetails
    async function displayProducts(products){
        try{
            // Clear the previously displayed products
            clearDisplay();
            console.log(products);
            // Go through each ProductDetails in the list of products
            products.forEach(product => {
                // Create a new template instance for this product
                const productEntry = template.content.cloneNode(true);
                const viewButton = productEntry.querySelector(".btn-view");
                if (viewButton) {
                    viewButton.dataset.productId = product.productId;
                }

                const registerButton = productEntry.querySelector(".btn-sale");
                if(registerButton) {
                    registerButton.dataset.productId = product.productId;
                }

                // Fill in data to their appropriate positions
                productEntry.querySelector('.name').textContent = product.name;
                productEntry.querySelector('.stock').textContent = product.inventory;
                productEntry.querySelector('.price').textContent =
                    new Intl.NumberFormat("en-US", {
                        style: "currency",
                        currency: "USD"
                    }).format(product.sellPrice);

                if (product.archived) {
                    productEntry.querySelector('.product').classList.add("archived")
                }

                // Append the new template instance to the product-list
                productList.appendChild(productEntry);
            });
        } catch (error) {
            console.log(error.message);
        }
    }

    async function readResponse(response) {
        const text = await response.text();
        let result = {};

        try {
        result = text ? JSON.parse(text) : {};
        } catch {
        throw new Error("The server returned an unexpected response.");
        }
        console.log(result);
        console.log("Result has been constructed! Checking response status...");
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

        // Add an event listener to the refresh button.
    refreshButton.addEventListener("click", (event) => {
        event.preventDefault();
        refresh();
        });

    searchBar.addEventListener('input', (event) =>{
        const query = event.target.value;
        search(query);
    });
    
    async function refresh()
    {
        // Get the products from the ProductController
        const response = await fetch(`/api/products/list`);
        const products = await readResponse(response);
        displayProducts(products);
    }

    async function search(query)
    {
        const response = await fetch(`/api/products/search}`, {
                                    method: "POST",
                                    body: query});
        const products = await readResponse(response);
        displayProducts(products);
    }

    function clearDisplay()
    {
        productList.innerHTML = "";
    }

    // Reload the list after a product is added or edited.
    document.addEventListener("products:changed", () => {
        refresh().catch(console.error);
    });

    // Load saved products when the page first opens.
    refresh().catch(console.error);
    
})();
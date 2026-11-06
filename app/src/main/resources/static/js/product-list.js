"use strict";

(() => {
    const productList = document.querySelector('.product-list');
    const template = document.getElementById('template-product');
    const refreshButton = document.querySelector('.header-actions [title="Refresh"]');
    // const productGroupTemplate = document.getElementById('template-product-group');

    // Add an event listener to the refresh button.
    refreshButton.addEventListener("click", (event) => {
        event.preventDefault();
        refresh();
        });

    function refresh()
    {
        console.log("refreshing...");
        displayProducts();
    }

    async function displayProducts(){
        try{
            // // Get the products from the ProductController and convert it to a readable array
            // const response = await fetch(`/api/products/display`).then(response => response.json())
            // .then(jsArray => { 
            //     // Go through each ProductDetails item
            //     jsArray.at(item => {
            //         console.log("item");
            //         // // Create a new template instance
            //         // const product = document.importNode(template.contentEditable, true);
            //         // // Fill in required data
            //         // product.elements.namedItem("name").textContent = item.name;
            //         // product.elements.namedItem("stock").textContent = item.inventory;
            //         // product.elements.namedItem("price").textContent = item.sellPrice;
            //         // // Append the new template instance to the product-list
            //         // productList.appendChild(clone);
            //     });
            // })

            const response = await fetch(`/api/products/list`);
            console.log("Retrieved response. Reading response...");
            const products = await readResponse(response);
            console.log("Read response. Outputting...");
            products.forEach(product => {
                console.log(product.name);
                const productEntry = template.content.cloneNode(true);
                // const productEntry = document.importNode(template.contentEditable, true);
                console.log(productEntry);
                productEntry.querySelector('.name').textContent = product.name;
                productEntry.querySelector('.stock').textContent = product.inventory;
                productEntry.querySelector('.price').textContent = product.sellPrice;

                console.log(productList);
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
    
})();
"use strict";

(() => {
    const productList = document.getElementById('product-list');
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
            // Get the products from the ProductController and convert it to a readable array
            const response = await fetch(`/api/products/display`).then(response => response.json())
            .then(jsArray => {
                // Go through each ProductDetails item
                jsArray.forEach(item => {
                    // Create a new template instance
                    const product = document.importNode(template.contentEditable, true);
                    // Fill in required data
                    product.elements.namedItem("name").textContent = item.name;
                    product.elements.namedItem("stock").textContent = item.inventory;
                    product.elements.namedItem("price").textContent = item.sellPrice;
                    // Append the new template instance to the product-list
                    productList.appendChild(clone);
                });
            })           
        }
        catch(error){
            throw new Error("Unexpected error displaying products.");
        }
    }

    console.log("testing...");
    
})();
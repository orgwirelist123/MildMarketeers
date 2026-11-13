"use strict";

import { readResponse, ERROR_MESSAGES } from '../util/network-helper.js';

const productList = document.querySelector('.product-list');

const parentNoChildrenTemplate = document.getElementById('template-product');
const parentWithChildrenTemplate = document.getElementById('template-product-group');
const childTemplate = document.getElementById('template-subproduct');

const refreshButton = document.querySelector('.header-actions [title="Refresh"]');

// Displays all the products in the given array of ProductDetails
export async function displayProducts(products){
    try{
        // Clear the previously displayed products
        clearDisplay();
        //console.log(products);

        let productFamilies = {}
        let productChildren = []

        /*
        Before we start constructing products
        - First, do an initial pass through all the products
            - For parent products (parent id == 0), add them into the productFamilies dictionary, so we can attach children later
            - For child parents (parent id != 0), add them into the productChildren, so we can go through them and attach them to parents later
        
        As an example, we could have:
        productFamilies = {
            ProductA's ID = {
                Data = {...},
                Children = []
            }
        }

        productChildren = [
            ProductB's ID = {
                Data = {...}
            }
        ]
        */
        // Go through each ProductDetails in the list
        products.forEach(product => {
            if (product.parentId == 0) // If the parent id == 0, we have a parent (or a possible parent)
            {
                productFamilies[product.productId] = {
                    Data: product,
                    Children: []
                };
            }
            else // If the parent id != 0, we have a child
            {
                productChildren.push(product);
            }
        });

        
        /*
        - Second, go through all the product children and attach them to parents in productFamilies

        Following the previous example, we would then have:
        productFamilies = {
            ProductA = {
                Data = {...},
                Children = [
                    ProductB = {
                        Data = {...}
                    }
                ]
            }
        }
        */

        productChildren.forEach(product => {
            let parentId = product.parentId;
            productFamilies[parentId].Children.push(product);
        });

        /*
        - Third, go through all the product families and generate their entries
            - For a parent without children, the entry uses template-product
            - For a parent with children, the entry uses template-product-group
            - For a child, the entry uses template-subproduct
        */
        Object.keys(productFamilies).forEach(id => {
            let productFamily = productFamilies[id];

            // Make the parent
            // Create a new template instance for this product
            let template = parentNoChildrenTemplate;
            if (productFamily.Children.length > 0) {
                template = parentWithChildrenTemplate;
            }

            const productEntry = template.content.cloneNode(true);
            const viewButton = productEntry.querySelector(".btn-view");
            if (viewButton) {
                viewButton.dataset.productId = productFamily.Data.productId;
            }

            const saleButton = productEntry.querySelector(".btn-sale");
            if (saleButton) {
                saleButton.dataset.productId = productFamily.Data.productId;
            }

            const addSubproductButton = productEntry.querySelector(".add-subproduct");
            if (addSubproductButton) {
                addSubproductButton.dataset.productId = productFamily.Data.productId;
            }

            // Fill in data to their appropriate positions
            productEntry.querySelector('.name').textContent = productFamily.Data.name;
            productEntry.querySelector('.stock').textContent = productFamily.Data.inventory;
            productEntry.querySelector('.price').textContent =
                new Intl.NumberFormat("en-US", {
                    style: "currency",
                    currency: "USD"
                }).format(productFamily.Data.sellPrice);

            // Display tags for parent product
            displayTags(productFamily.Data.productId, productEntry);

            if (productFamily.Data.archived) {
                productEntry.querySelector('.product').classList.add("archived")
            }
            const subproducts = productEntry.querySelector('.subproducts');

            // Make the children
            productFamily.Children.forEach(child => {
                let template = childTemplate;

                const productEntry = template.content.cloneNode(true);
                const viewButton = productEntry.querySelector(".btn-view");
                if (viewButton) {
                    viewButton.dataset.productId = child.productId;
                }

                const saleButton = productEntry.querySelector(".btn-sale");
                if (saleButton) {
                    saleButton.dataset.productId = child.productId;
                }

                // Fill in data to their appropriate positions
                productEntry.querySelector('.name').textContent = child.name;
                productEntry.querySelector('.stock').textContent = child.inventory;
                productEntry.querySelector('.price').textContent =
                    new Intl.NumberFormat("en-US", {
                        style: "currency",
                        currency: "USD"
                    }).format(child.sellPrice);
                
                // Display tags for child products
                displayTags(child.productId, productEntry);

                if (child.archived) {
                    productEntry.querySelector('.product').classList.add("archived")
                }

                // Append the new template instance to the subproducts list
                subproducts.appendChild(productEntry);
            })

            // Append the new template instance to the product-list
            productList.appendChild(productEntry);
        });

        /*
        // Create a new template instance for this product
            const productEntry = template.content.cloneNode(true);
            const viewButton = productEntry.querySelector(".btn-view");
            if (viewButton) {
                viewButton.dataset.productId = product.productId;
            }

            const saleButton = productEntry.querySelector(".btn-sale");
            if (saleButton) {
                saleButton.dataset.productId = product.productId;
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
                // console.log(productEntry.querySelectorAll());
                // console.log(productEntry.children);
                productEntry.querySelector('.product').classList.add("archived")
            }

            // Append the new template instance to the product-list
            productList.appendChild(productEntry);
        */
    } catch (error) {
        console.log(error.message);
    }
}

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

async function displayTags(productId, productEntry) {
        // if(!productEntry) {
        //     console.log("productEntry is null");
        //     return;
        // }

        const tagTemplate = document.getElementById("template-tag");
        const moreTagTemplate = document.getElementById("template-tag-more").content.cloneNode(true);
        const tagList = productEntry.querySelector('.tags');
        const moreTagList = moreTagTemplate.querySelector('.tag-menu');
        const tagNum = moreTagTemplate.querySelector('.tag-more');
        //console.log({ moreTagTemplate, tagmenu: moreTagTemplate?.querySelector('.tag-menu') });

        const response = await fetch(`/api/tags/${productId}`);
        const productTags = await readResponse(response);

        if(productTags.length == 0) return;

        // console.log(productTags);

        //console.log({ productEntry, tags: productEntry?.querySelector('.tags') });

        try {
            var num = 0;

            productTags.forEach(tag => {
                const tagEntry = tagTemplate.content.cloneNode(true);
                tagEntry.querySelector('.tag').textContent = tag;

                if(num == 0) {
                    tagList.insertBefore(tagEntry, tagList.firstChild);
                }
                else if(num < 2) {
                    tagList.append(tagEntry);
                }
                else if(num == 2){
                    tagList.appendChild(moreTagTemplate);
                    //console.log("num("+num+"): moreTagList added");
                    moreTagList.appendChild(tagEntry);
                    //console.log("num("+num+"): tagEntry added to moreTagList");
                }
                else {
                    moreTagList.appendChild(tagEntry);
                }

                //console.log("Finished adding tag: " + tag + ", num(" + num + ")");
                
                num = num + 1;
            });

            if(num > 2) {                
                tagNum.textContent = "+" + (num-2);
            }
        } catch (error) {
            console.log(error.message);
        }        
    }

    // Add an event listener to the refresh button.
refreshButton.addEventListener("click", (event) => {
    event.preventDefault();
    refresh();
    });

async function refresh()
{
    // Get the products from the ProductController
    const response = await fetch(`/api/products/list`);
    const products = await readResponse(
        response, 
        ERROR_MESSAGES.GENERAL.INVALID_RESPONSE, 
        ERROR_MESSAGES.PRODUCT.MISSING_PRODUCT, 
        ERROR_MESSAGES.GENERAL.REQUEST_FAILURE
    );
    displayProducts(products);
}

function clearDisplay()
{
    productList.innerHTML = "";
}

export async function refreshProducts() {
    refresh();
}
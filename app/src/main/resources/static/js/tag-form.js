"use strict";

import { readResponse } from './util/network-helper.js';

(() => {
    const panel = document.querySelector("#panel-product");
    const form = panel.querySelector(".product-form");

    const tagNameInput = form.elements.namedItem("tagname");
    // const addTagMenu = form.querySelector(".add-tag-menu");
    const tagField = form.querySelector(".tag-field");
    const tagOptionField = form.querySelector(".tag-option-list");

    const allChosenTagNames = [];

    async function addTags() {
        const allChosenTags = form.querySelectorAll(".tag-chosen");
        allChosenTags.forEach(tag => {
            allChosenTagNames.push(tag.querySelector(".tag-name").textContent);
        });
    }

    // Actions when 'Create' tag button is clicked
    form.addEventListener("click", (event) => {
        const button = event.target.closest(".btn-dark");
        if (!button) return;

        //console.log("Create tag button clicked");

        event.preventDefault();

        try {
            addChosenTag(tagNameInput.value);
            addTagOption();
        } catch (error){
            console.log(error.message);
        } finally {            
            tagNameInput.value = "";    
        }
    });

    // Adds tag in 'Tags' field
    function addChosenTag(tagName) {        
        const tagChosenEntry = document.getElementById("template-tag-chosen").content.cloneNode(true);
        tagChosenEntry.querySelector(".tag-name").textContent = tagName;

        if(!form) {
            console.log("form is null");
            return;
        }

        const removeButton = tagChosenEntry.querySelector(".tag-remove");
        let addTagDropdown = form.querySelector(".add-tag");
        tagField.insertBefore(tagChosenEntry, addTagDropdown);

        // Allows tag to be removed
        removeButton.addEventListener("click", function() {
            removeButton.parentNode.remove();
        });        
    }

    // Adds tag to option menu
    function addTagOption() {        
        const tagOptionEntry = document.getElementById("template-tag-option").content.cloneNode(true);
        tagOptionEntry.querySelector(".tag-name").textContent = tagNameInput.value;

        const optionName = tagOptionEntry.querySelector(".tag-name").textContent;

        // Allows tag to be added to 'Tags' field when clicked
        tagOptionEntry.querySelector('button[type="button"]').addEventListener("click", function() {
            //console.log("tag option clicked");
            addChosenTag(optionName);
        });

        tagOptionField.appendChild(tagOptionEntry);
    }
})();
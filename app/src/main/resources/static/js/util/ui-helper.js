"use strict";

// Stops the mouse wheel from changing a number input while scrolling the page
document.addEventListener("wheel", (event) => {
    if (event.target === document.activeElement && event.target.type === "number") {
        event.target.blur();
    }
}, { passive: false });

// Panels and popups always reopen scrolled to the top
document.querySelectorAll(".panel").forEach((panel) => {
    panel.addEventListener("beforetoggle", (event) => {
        if (event.newState === "closed") {
            panel.scrollTop = 0;
        }
    });
});
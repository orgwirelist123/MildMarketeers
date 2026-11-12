"use strict";

// How long a notification stays in milliseconds
const NOTIFICATION_TIME = 4000;

// Shows a notification in the bottom-right corner
// Already shows the newest 3 notifications
function sendNotification(message) {
    const container = document.querySelector(".notifications");
    const template = document.getElementById("template-notification");
    const notification = template.content.firstElementChild.cloneNode(true);

    notification.querySelector(".notification-message").textContent = message;

    notification.querySelector(".close-btn").addEventListener("click", () => hideNotification(notification));
    setTimeout(() => hideNotification(notification), NOTIFICATION_TIME);

    container.append(notification);
}

// Fades a notification out, then removes it
function hideNotification(notification) {
    // Ignore if it's already closing
    if (notification.classList.contains("hiding")) return;

    // If it's hidden, remove it right away
    if (notification.getClientRects().length === 0) {
        notification.remove();
        return;
    }

    notification.classList.add("hiding");
    notification.addEventListener("animationend", () => notification.remove());
}
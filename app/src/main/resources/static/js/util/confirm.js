"use strict";

// Shows the confirmation popup and waits for the user to choose
// Resolves to true if they confirm, or false if they cancel or close it
export function confirmAction({ title, message, confirmLabel, danger = false}) {
	const popup = document.getElementById("popup-confirm");
	const confirmation = popup.querySelector(".confirm-yes");

	popup.querySelector(".confirm-title").textContent = title;
	popup.querySelector(".confirm-message").textContent = message;
	confirmation.textContent = confirmLabel;

	// Red button for deleting, dark button for everything else
	confirmation.classList.toggle("btn-danger", danger);
	confirmation.classList.toggle("btn-dark", !danger);

	return new Promise((resolve) => {
		let confirmed = false;

		confirmation.onclick = () => {
			confirmed = true;
			popup.hidePopover();
		};

		const onToggle = (event) => {
			if (event.newState !== "closed") return;
			popup.removeEventListener("toggle", onToggle);
			resolve(confirmed);
		};
		popup.addEventListener("toggle", onToggle);

		popup.showPopover();
	});
}
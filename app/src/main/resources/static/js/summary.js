"use strict";

(() => {
	// Default to the last 7 days
	const today = new Date();
	const weekAgo = new Date();
	weekAgo.setDate(today.getDate() - 6);

	// Date range calendar (Flatpickr)
	flatpickr("#date-range", {
		mode: "range",
		dateFormat: "M j, Y",
		defaultDate: [weekAgo, today],
		maxDate: "today",
		position: "below right",
		positionElement: document.querySelector(".date-range"),
		locale: { rangeSeparator: " - "},
		onChange: (selectedDates) => {
			if (selectedDates.length === 2) {
				// TODO: Load stats, graphs, and top products for selectedDates[0] to selectedDates[1]
			}
		}
	});
})();
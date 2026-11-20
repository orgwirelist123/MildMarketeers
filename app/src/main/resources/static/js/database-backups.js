"use strict";

import { confirmAction } from "./util/confirm.js";

const list = document.querySelector(".backup-list");
const createButton = document.getElementById("create-backup");
const status = document.getElementById("backup-status");
let busy = false;

async function request(path = "", method = "GET") {
	const response = await fetch(`/api/database/backups${path}`, {
		method, cache: "no-store", headers: { "X-MM-Backup": "1" }
	});
	const result = await response.json();
	if (!response.ok) throw new Error(result.message || "Backup operation failed.");
	return result;
}

function message(text) {
	status.textContent = text;
}

function setBusy(value) {
	busy = value;
	createButton.disabled = value;
	list.querySelectorAll("button").forEach(button => { button.disabled = value; });
}

async function refresh() {
	const result = await request();
	document.querySelector(".database-name").textContent = result.database;
	document.getElementById("last-backup").textContent = result.backups.length
		? new Date(result.backups[0].createdAt).toLocaleString() : "No backups yet";
	list.replaceChildren();
	for (const backup of result.backups) {
		const row = document.createElement("li");
		row.className = "backup";
		const info = document.createElement("div");
		info.className = "backup-info";
		const name = document.createElement("span");
		name.className = "backup-name";
		name.textContent = `Backup ${backup.id.slice(0, 8)}`;
		const date = document.createElement("span");
		date.className = "backup-date";
		date.textContent = `${new Date(backup.createdAt).toLocaleString()} · ${(backup.bytes / 1024).toFixed(1)} KB`;
		info.append(name, date);
		const button = document.createElement("button");
		button.type = "button";
		button.className = "btn btn-small";
		button.textContent = "Load";
		button.disabled = busy;
		button.addEventListener("click", () => load(backup, name.textContent));
		row.append(info, button);
		list.append(row);
	}
	if (!result.backups.length) {
		const empty = document.createElement("li");
		empty.textContent = "No backups yet. Select Back up database to create one.";
		list.append(empty);
	}
}

async function load(backup, name) {
	if (busy) return;
	setBusy(true);
	try {
		const confirmed = await confirmAction({
			title: "Load Backup",
			message: `Load ${name} from ${new Date(backup.createdAt).toLocaleString()}? This replaces all current products, sales, tags, components, and their histories. Changes made since this backup will be lost.`,
			confirmLabel: "Load backup", danger: true
		});
		if (!confirmed) return;
		message("Loading backup. Please wait…");
		const result = await request(`/${backup.id}/load`, "POST");
		message(result.message);
		sendNotification(result.message);
	} catch (error) {
		message(error.message);
		sendNotification(error.message);
	} finally { setBusy(false); }
}

createButton.addEventListener("click", async () => {
	if (busy) return;
	setBusy(true);
	message("Creating backup. Please wait…");
	try {
		await request("", "POST");
		message("Backup created successfully.");
		try { await refresh(); }
		catch (error) { message(`Backup created, but the list could not refresh. Reload this page. ${error.message}`); }
	} catch (error) { message(error.message); }
	finally { setBusy(false); }
});

setBusy(true);
refresh().then(() => message("")).catch(error => message(error.message)).finally(() => setBusy(false));

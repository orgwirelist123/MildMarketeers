"use strict";

const DEFAULT_HEADER_TYPES = {
	applicationJson: { "Content-Type": "application/json" }
}

const ERROR_MESSAGES = {
	GENERAL: {
		REQUEST_FAILURE: "Could not complete the request.",
		INVALID_RESPONSE: "The server returned an unexpected response."
	},
	PRODUCT: {
		MISSING_PRODUCT: "This product no longer exists."
	}
}

// Return a copy of the header type, in case the user wants to modify it in any way
function GetDefaultHeaderType(name) {
	return [...DEFAULT_HEADER_TYPES[name]];
}

// Given a response and error-catching messages, attempt to parse the response
// and print relevant messages when errors are occurred
async function readResponse(response, invalidResponseMessage, notOkMissingMessage, notOkOtherMessage) {
	const text = await response.text();
	let result = {};

	try {
	  result = text ? JSON.parse(text) : {};
	} catch {
	  throw new Error(invalidResponseMessage);
	}

	if (!response.ok) {
	  throw new Error(
		result.message ||
		(response.status === 404
		  ? notOkMissingMessage
		  : notOkOtherMessage)
	  );
	}

	return result;
}

// Make a request and return the response
async function makeRequest(url, method, headers, body) {
	const response = await fetch(
		url,
		{
			method: method,
			headers: headers,
			body: JSON.stringify(body)
		}
	);
	return response;
}

export { readResponse, makeRequest, GetDefaultHeaderType, ERROR_MESSAGES }
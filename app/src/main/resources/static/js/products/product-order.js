// Sort whole families while keeping each child under its parent.
export const sortModes = [
	"Alphabetically", "Price", "In stock", "Out of stock",
	"Highest inventory", "Lowest inventory"
];

export function arrangeProducts(products, mode = "Alphabetically", query = "") {
	const names = new Intl.Collator("en", { sensitivity: "base", numeric: true });
	const compare = (a, b) => {
		let difference = 0;
		if (mode === "Price") difference = Number(a.sellPrice) - Number(b.sellPrice);
		if (mode === "Highest inventory") difference = b.inventory - a.inventory;
		if (mode === "Lowest inventory") difference = a.inventory - b.inventory;
		if (mode === "In stock") difference = Number(b.inventory > 0) - Number(a.inventory > 0);
		if (mode === "Out of stock") difference = Number(a.inventory > 0) - Number(b.inventory > 0);
		return difference || names.compare(a.name, b.name) || a.productId - b.productId;
	};
	const families = new Map();
	for (const product of products) {
		if (!product.parentId) families.set(product.productId, { parent: product, children: [] });
	}
	for (const product of products) {
		if (product.parentId) families.get(product.parentId)?.children.push(product);
	}
	const needle = query.trim().toLocaleLowerCase();
	const tag = mode.startsWith("Tag: ") ? mode.slice(5).toLowerCase() : null;
	const matches = product => product.name.toLocaleLowerCase().includes(needle)
		&& (tag === null || (product.tags || []).some(label => label.toLowerCase() === tag));
	return [...families.values()]
		.sort((a, b) => compare(a.parent, b.parent))
		.flatMap(({ parent, children }) => {
			const visible = matches(parent) ? children : children.filter(matches);
			if (!matches(parent) && !visible.length) return [];
			return [parent, ...visible.slice().sort(compare)];
		});
}

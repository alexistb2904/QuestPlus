export function visibleRows(count, width, top, height) {
	const columns = Math.max(1, Math.floor((width + 5) / 90)),
		rowHeight = 88,
		rows = Math.ceil(count / columns);
	return { columns, rowHeight, rows, first: Math.min(rows, Math.max(0, Math.floor(top / rowHeight) - 2)), last: Math.min(rows, Math.ceil((top + height) / rowHeight) + 2) };
}

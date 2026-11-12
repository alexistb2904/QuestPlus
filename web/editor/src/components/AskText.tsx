import React, { useState } from "react";
import { createRoot } from "react-dom/client";
import { tUI } from "../ui/ui-i18n.js";
import { Dialog } from "./Dialog";

type Ask = {
	title: string;
	label: string;
	initial?: string;
	submit?: string;
	maxLength?: number;
	/** Returns a blocking error message, or "" when the trimmed value is acceptable. */
	validate?: (value: string) => string;
	/** Returns a non-blocking note shown under the field. */
	warn?: (value: string) => string;
};

/** In-page replacement for window.prompt: resolves with the trimmed value, or null when cancelled. */
export function askText(ask: Ask): Promise<string | null> {
	const host = document.body.appendChild(document.createElement("div")),
		root = createRoot(host);
	return new Promise((resolve) => {
		const done = (value: string | null) => {
			root.unmount();
			host.remove();
			resolve(value);
		};
		root.render(<AskTextDialog {...ask} done={done} />);
	});
}

function AskTextDialog({ title, label, initial = "", submit = tUI("Create"), maxLength, validate, warn, done }: Ask & { done: (value: string | null) => void }) {
	const [value, setValue] = useState(initial),
		[touched, setTouched] = useState(false);
	const clean = value.trim(),
		error = !clean ? tUI("Enter a name.") : maxLength && clean.length > maxLength ? maxLength + " " + tUI("characters maximum") : validate?.(clean) || "",
		note = !error && warn?.(clean);
	return (
		<Dialog title={title} onClose={() => done(null)} className="ask-text-dialog">
			<form
				noValidate
				onSubmit={(e) => {
					e.preventDefault();
					setTouched(true);
					if (!error) done(clean);
				}}>
				<label className="field">
					<span>{label}</span>
					<input
						autoFocus
						value={value}
						maxLength={maxLength}
						aria-invalid={touched && !!error}
						aria-describedby="ask-text-message"
						onFocus={(e) => e.currentTarget.select()}
						onChange={(e) => {
							setValue(e.target.value);
							setTouched(true);
						}}
					/>
					<small id="ask-text-message" className={touched && error ? "reward-error" : "ask-text-note"} role={touched && error ? "alert" : undefined}>
						{touched && error ? error : note || (maxLength ? clean.length + " / " + maxLength : "")}
					</small>
				</label>
				<div className="dialog-actions">
					<button type="button" className="button" onClick={() => done(null)}>
						{tUI("Cancel")}
					</button>
					<button type="submit" className="button primary" disabled={touched && !!error}>
						{submit}
					</button>
				</div>
			</form>
		</Dialog>
	);
}

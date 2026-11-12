import { ThumbsDown, ThumbsUp } from "lucide-react";
import React, { useState } from "react";
import { tUI } from "../ui/ui-i18n.js";
import { Dialog } from "./Dialog";

const hub = String(import.meta.env.VITE_QUESTPLUS_HUB_URL || (import.meta.env.DEV ? "http://localhost:8787" : window.location.origin)).replace(/\/$/, "");

export function FeedbackDialog({ onClose }: { onClose: () => void }) {
	const [rating, setRating] = useState<"good" | "bad" | "">(""),
		[message, setMessage] = useState(""),
		[status, setStatus] = useState<"idle" | "sending" | "sent" | "error">("idle"),
		[error, setError] = useState("");
	const send = async () => {
		setStatus("sending");
		try {
			const res = await fetch(hub + "/api/feedback", {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ rating, message, source: "studio" }),
			});
			if (!res.ok) throw Error((await res.json().catch(() => null))?.error || "HTTP " + res.status);
			setStatus("sent");
		} catch (e) {
			setError(e instanceof TypeError ? tUI("Feedback needs an internet connection.") : String((e as Error).message));
			setStatus("error");
		}
	};
	return (
		<Dialog title={tUI("Send feedback")} onClose={onClose} className="feedback-dialog">
			{status === "sent" ? (
				<>
					<p role="status">{tUI("Thanks, your feedback was sent.")}</p>
					<div className="dialog-actions">
						<button className="button primary" onClick={onClose}>
							{tUI("Close")}
						</button>
					</div>
				</>
			) : (
				<>
					<p>{tUI("Tell us what works well or what gets in your way.")}</p>
					<div className="feedback-rating" role="radiogroup" aria-label={tUI("Rating")}>
						<button type="button" role="radio" aria-checked={rating === "good"} className={"button secondary" + (rating === "good" ? " selected" : "")} onClick={() => setRating("good")}>
							<ThumbsUp size={16} aria-hidden="true" /> {tUI("I like it")}
						</button>
						<button type="button" role="radio" aria-checked={rating === "bad"} className={"button secondary" + (rating === "bad" ? " selected" : "")} onClick={() => setRating("bad")}>
							<ThumbsDown size={16} aria-hidden="true" /> {tUI("Something is wrong")}
						</button>
					</div>
					<textarea className="feedback-message" aria-label={tUI("Your feedback")} placeholder={tUI("Your feedback")} maxLength={2000} rows={6} value={message} onChange={(e) => setMessage(e.target.value)} />
					{status === "error" && (
						<p className="error-line" role="alert">
							{error}
						</p>
					)}
					<div className="dialog-actions">
						<button className="button secondary" onClick={onClose}>
							{tUI("Cancel")}
						</button>
						<button className="button primary" disabled={!rating || !message.trim() || status === "sending"} onClick={send}>
							{tUI(status === "sending" ? "Sending…" : "Send")}
						</button>
					</div>
				</>
			)}
		</Dialog>
	);
}

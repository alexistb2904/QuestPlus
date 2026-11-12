<script setup lang="ts">
import { ref } from "vue";

const dialog = ref<HTMLDialogElement>();
const rating = ref<"good" | "bad" | "">("");
const message = ref("");
const status = ref<"idle" | "sending" | "sent" | "error">("idle");
const error = ref("");

function open(value: "good" | "bad") {
	rating.value = value;
	if (status.value === "sent") message.value = "";
	status.value = "idle";
	dialog.value?.showModal();
}
async function send() {
	status.value = "sending";
	try {
		// The docs are served from the same origin as the Hub API, which holds the Discord webhook.
		const res = await fetch("/api/feedback", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ rating: rating.value, message: message.value, source: "docs", page: location.pathname }),
		});
		if (!res.ok) throw Error((await res.json().catch(() => null))?.error || "HTTP " + res.status);
		status.value = "sent";
	} catch (e) {
		error.value = e instanceof TypeError ? "Feedback needs an internet connection." : String((e as Error).message);
		status.value = "error";
	}
}
</script>

<template>
	<div class="QPFeedback">
		<span>Was this page helpful?</span>
		<button type="button" @click="open('good')">👍 Yes</button>
		<button type="button" @click="open('bad')">👎 No</button>
		<dialog ref="dialog" class="QPFeedback__dialog" aria-labelledby="qp-feedback-title" @click.self="dialog?.close()">
			<form method="dialog" @submit.prevent="send">
				<h2 id="qp-feedback-title">Send feedback</h2>
				<template v-if="status === 'sent'">
					<p role="status">Thanks, your feedback was sent.</p>
					<div class="QPFeedback__actions"><button type="button" class="primary" @click="dialog?.close()">Close</button></div>
				</template>
				<template v-else>
					<div class="QPFeedback__rating" role="radiogroup" aria-label="Rating">
						<button type="button" role="radio" :aria-checked="rating === 'good'" @click="rating = 'good'">👍 I like it</button>
						<button type="button" role="radio" :aria-checked="rating === 'bad'" @click="rating = 'bad'">👎 Something is wrong</button>
					</div>
					<textarea v-model="message" aria-label="Your feedback" placeholder="What works well, what is missing or unclear?" maxlength="2000" rows="6" required></textarea>
					<p v-if="status === 'error'" class="QPFeedback__error" role="alert">{{ error }}</p>
					<div class="QPFeedback__actions">
						<button type="button" @click="dialog?.close()">Cancel</button>
						<button type="submit" class="primary" :disabled="!rating || !message.trim() || status === 'sending'">{{ status === "sending" ? "Sending…" : "Send" }}</button>
					</div>
				</template>
			</form>
		</dialog>
	</div>
</template>

<style scoped>
.QPFeedback {
	display: flex;
	flex-wrap: wrap;
	align-items: center;
	gap: 8px;
	margin-top: 32px;
	font-size: 14px;
	color: var(--vp-c-text-2);
}
.QPFeedback button {
	padding: 4px 12px;
	border: 1px solid var(--vp-c-divider);
	border-radius: 6px;
	color: var(--vp-c-text-1);
	background: var(--vp-c-bg-soft);
	cursor: pointer;
}
.QPFeedback button:hover,
.QPFeedback button:focus-visible,
.QPFeedback button[aria-checked="true"] {
	border-color: var(--vp-c-brand-1);
}
.QPFeedback button:disabled {
	opacity: 0.5;
	cursor: not-allowed;
}
.QPFeedback__dialog {
	width: min(480px, calc(100vw - 32px));
	padding: 20px;
	border: 1px solid var(--vp-c-divider);
	border-radius: 8px;
	color: var(--vp-c-text-1);
	background: var(--vp-c-bg);
}
.QPFeedback__dialog::backdrop {
	background: rgba(0, 0, 0, 0.6);
}
.QPFeedback__dialog h2 {
	margin: 0 0 12px;
	font-size: 18px;
}
.QPFeedback__rating {
	display: flex;
	gap: 8px;
	margin-bottom: 10px;
}
.QPFeedback__rating button {
	flex: 1;
}
.QPFeedback__dialog textarea {
	width: 100%;
	box-sizing: border-box;
	padding: 8px;
	border: 1px solid var(--vp-c-divider);
	border-radius: 6px;
	background: var(--vp-c-bg-soft);
	font: inherit;
	resize: vertical;
}
.QPFeedback__error {
	color: var(--vp-c-danger-1);
}
.QPFeedback__actions {
	display: flex;
	justify-content: flex-end;
	gap: 8px;
	margin-top: 12px;
}
.QPFeedback__actions .primary {
	border-color: var(--vp-c-brand-1);
	background: var(--vp-c-brand-soft);
}
</style>

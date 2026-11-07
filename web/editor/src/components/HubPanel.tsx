import { AlertCircle, Check, Copy, Download, Globe, Link2, Save, Server, UploadCloud } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { hubHttpError, isHubOverloaded } from "../core/hub-errors.js";
import { setHubRegistry } from "../core/hub-registry.js";
import { fileMap, importDocuments, validate } from "../core/project.js";
import type { Project } from "../types";
import { tUI } from "../ui/ui-i18n.js";
import { Dialog } from "./Dialog";

type HubData = { files: { name: string; content: string }[]; registry?: Record<string, unknown>; revision: string; serverName?: string };
type Feedback = { message: string; detail?: string; tone: "info" | "success" | "error" };
const params = new URLSearchParams(window.location.search);
const defaultHub = String(params.get("hub") || import.meta.env.VITE_QUESTPLUS_HUB_URL || (import.meta.env.DEV ? "http://localhost:8787" : window.location.origin)).replace(
	/\/$/,
	""
);
const linkedSession = params.get("session") || "";
const linkedPack = params.get("pack") || "";
const documents = (p: Project) => [...fileMap(p)].filter(([name]) => /\.json5?$/i.test(name)).map(([name, content]) => ({ name, content }));
const json = async (url: string, method = "GET", body?: unknown, access?: string) => {
	const target = new URL(url);
	if (target.protocol !== "https:" && !(target.protocol === "http:" && ["localhost", "127.0.0.1"].includes(target.hostname)))
		throw Error(tUI("Use HTTPS, or localhost for development."));
	const response = await fetch(url, {
		method,
		headers: { ...(body ? { "Content-Type": "application/json" } : {}), ...(access ? { Authorization: "Bearer " + access } : {}) },
		body: body ? JSON.stringify(body) : undefined,
		cache: "no-store",
	});
	const data = await response.json().catch(() => ({ error: tUI("The website returned an invalid response.") }));
	if (!response.ok) throw hubHttpError(response, data);
	return data;
};
export function HubPanel({ project, onLoad, onSaveLocal }: { project: Project; onLoad: (p: Project) => void; onSaveLocal: () => boolean }) {
	const [open, setOpen] = useState(Boolean(linkedPack || linkedSession));
	const [hub, setHub] = useState(defaultHub),
		[session, setSession] = useState(linkedSession),
		[pack, setPack] = useState(linkedPack);
	const [pending, setPending] = useState<{ requestId: string; requestSecret: string; code: string } | null>(null);
	const [token, setToken] = useState(""),
		[revision, setRevision] = useState(""),
		[loaded, setLoaded] = useState(false);
	const [serverName, setServerName] = useState(""),
		[savedProject, setSavedProject] = useState(""),
		[share, setShare] = useState("");
	const [status, setStatus] = useState<Feedback | null>(null),
		[busy, setBusy] = useState(false),
		[copied, setCopied] = useState("");
	const running = useRef(false);
	const host = hub.replace(/\/$/, "");
	const dirty = loaded && JSON.stringify(project) !== savedProject;
	const feedback = (message: string, tone: Feedback["tone"] = "info", detail?: string) => setStatus({ message, tone, detail });
	const run = async (job: () => Promise<void>) => {
		if (running.current) return;
		running.current = true;
		setBusy(true);
		setStatus(null);
		try {
			await job();
		} catch (e) {
			feedback("Action failed. Your editor changes are kept.", "error", (e as Error).message);
		} finally {
			running.current = false;
			setBusy(false);
		}
	};
	const disconnect = () => {
		setToken("");
		setPending(null);
		setRevision("");
		setLoaded(false);
		setSavedProject("");
		setServerName("");
		setHubRegistry({});
		setStatus(null);
	};
	const copy = async (value: string) => {
		try {
			await navigator.clipboard.writeText(value);
			setCopied(value);
		} catch {
			feedback("Copy unavailable. Select and copy the text manually.", "error");
		}
	};
	const loadProject = (data: HubData) => {
		const next = importDocuments(data.files);
		const issues = validate(next);
		if (issues.length) throw Error(issues[0].where + ": " + tUI(issues[0].message));
		if (!window.confirm(tUI("Replace the editor content with this pack? Export a ZIP first to keep a separate copy."))) return false;
		onLoad(next as Project);
		if (data.registry) setHubRegistry(data.registry);
		else if (!token) setHubRegistry({});
		return next;
	};
	const start = () =>
		run(async () => {
			if (!session.trim()) throw Error(tUI("Open the link from /questplus editor in Minecraft, or enter its session ID."));
			const meta = await json(host + "/api/editor/sessions/" + encodeURIComponent(session));
			setServerName(meta.serverName || "Minecraft");
			const req = await json(host + "/api/editor/sessions/" + encodeURIComponent(session) + "/request", "POST", {});
			setPending(req);
		});
	useEffect(() => {
		if (!pending || !session || token) return;
		let cancelled = false,
			checking = false;
		const check = async () => {
			if (checking) return;
			checking = true;
			try {
				const result = await json(host + "/api/editor/sessions/" + encodeURIComponent(session) + "/status", "POST", pending);
				if (cancelled || !result.approved) return;
				setToken(result.editorToken);
				setPending(null);
				feedback("Browser approved. Load the server quests to start editing.", "success");
			} catch (error) {
				if (!cancelled) {
					setPending(null);
					feedback("Approval failed. Request a new code.", "error", (error as Error).message);
				}
			} finally {
				checking = false;
			}
		};
		void check();
		const timer = window.setInterval(() => {
			void check();
		}, 2500);
		return () => {
			cancelled = true;
			window.clearInterval(timer);
		};
	}, [host, session, pending, token]);
	useEffect(() => {
		if (!token) return;
		let mounted = true;
		const timer = window.setInterval(() => {
			void json(host + "/api/editor/sessions/" + encodeURIComponent(session) + "/ping", "GET", undefined, token).catch((e) => {
				if (mounted && !isHubOverloaded(e)) {
					disconnect();
					feedback("Connection lost. Reopen the editor link from Minecraft. Your changes are kept.", "error", (e as Error).message);
				}
			});
		}, 60_000);
		return () => {
			mounted = false;
			window.clearInterval(timer);
		};
	}, [host, session, token]);
	const load = () =>
		run(async () => {
			const response: HubData = await json(host + "/api/editor/sessions/" + encodeURIComponent(session) + "/data", "GET", undefined, token);
			const next = loadProject(response);
			if (next) {
				setRevision(response.revision);
				setLoaded(true);
				setSavedProject(JSON.stringify(next));
				feedback("Server quests loaded. Save will apply your changes to this server.", "success");
			}
		});
	const publish = () =>
		run(async () => {
			const issues = validate(project);
			if (issues.length) throw Error(issues[0].where + ": " + tUI(issues[0].message));
			const submitted = JSON.stringify(project);
			const response = await json(
				host + "/api/editor/sessions/" + encodeURIComponent(session) + "/publish",
				"POST",
				{ files: documents(project), baseRevision: revision },
				token
			);
			feedback("Saving… waiting for Minecraft to validate your changes.");
			const deadline = Date.now() + 165000;
			for (let i = 0; i < 65 && Date.now() < deadline; i++) {
				await new Promise((ok) => setTimeout(ok, 2500));
				let update;
				try {
					update = await json(host + "/api/editor/sessions/" + encodeURIComponent(session) + "/jobs/" + response.id, "GET", undefined, token);
				} catch (error) {
					if (!isHubOverloaded(error)) throw error;
					const delay = (error as Error & { retryAfterMs?: number }).retryAfterMs || 5000;
					await new Promise((ok) => setTimeout(ok, Math.max(0, Math.min(delay, deadline - Date.now()))));
					continue;
				}
				if (update.status === "applied") {
					setRevision(update.revision);
					setSavedProject(submitted);
					feedback("Saved on the server. Players now see the updated quests.", "success");
					return;
				}
				if (update.status === "failed") throw Error(tUI("Minecraft rejected the changes. Previous quests are kept.") + " " + update.error);
			}
			throw Error(tUI("Minecraft has not confirmed the save. Check the game before trying again."));
		});
	const save = () => {
		const localSaved = onSaveLocal();
		if (token && loaded) {
			void publish();
			return;
		}
		if (session || token) {
			setOpen(true);
			return;
		}
		if (localSaved) feedback("Draft saved in this browser. Connect a server to publish it.", "success");
		else feedback("Storage is full. Export your project as ZIP.", "error");
	};
	useEffect(() => {
		const key = (e: KeyboardEvent) => {
			if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "s") {
				e.preventDefault();
				if (!running.current) save();
			}
		};
		window.addEventListener("keydown", key);
		return () => window.removeEventListener("keydown", key);
	});
	const publishPack = () =>
		run(async () => {
			const issues = validate(project);
			if (issues.length) throw Error(issues[0].where + ": " + tUI(issues[0].message));
			const result = await json(host + "/api/packs", "POST", { files: documents(project) });
			const url = new URL(result.url, host);
			if (!["http:", "https:"].includes(url.protocol)) throw Error(tUI("The website returned an invalid response."));
			url.searchParams.set("hub", host);
			setShare(url.href);
			setPack(result.id);
			feedback("Share link created for 7 days. This does not update a server.", "success");
		});
	const loadPack = () =>
		run(async () => {
			if (!pack.trim()) throw Error(tUI("Enter a pack ID."));
			if (loadProject(await json(host + "/api/packs/" + encodeURIComponent(pack))))
				feedback(
					token && loaded
						? "Shared pack opened in the editor. Save to publish it to your connected server."
						: "Shared pack opened. Edit it, export a ZIP or connect a server.",
					"success"
				);
		});
	const command = pending ? "/questplus editor approve " + pending.code : "/questplus editor";
	const saveLabel = token && loaded ? "Save to server" : session || token ? "Connect to save" : "Save draft";
	const stateLabel = token ? (loaded ? (dirty ? "Unpublished changes" : "Up to date on server") : "Ready to load") : pending ? "Approve in Minecraft" : "No server connected";
	return (
		<>
			<button
				type="button"
				className={"button secondary hub-toolbar-button" + (token ? " connected" : "")}
				onClick={() => setOpen(true)}
				title={token ? (serverName || tUI("Connected server")) + " · " + tUI(stateLabel) : tUI(stateLabel)}>
				<Server size={17} />
				<span>{token ? serverName || tUI("Connected server") : tUI("Server & sharing")}</span>
			</button>
			<button type="button" className="button primary" disabled={busy} onClick={save} title={tUI(saveLabel) + " (Ctrl+S)"}>
				<Save size={18} />
				{tUI(busy ? "Working…" : saveLabel)}
			</button>
			{status && !open && (
				<span className="hub-status-wrap" role="status">
					<button
						type="button"
						className={"hub-toolbar-status " + status.tone}
						onClick={() => setOpen(true)}
						title={tUI(status.message) + (status.detail ? " " + status.detail : "")}>
						{tUI(dirty && status.tone === "success" ? "Unpublished changes" : status.message)}
					</button>
				</span>
			)}
			{open && (
				<Dialog className="hub-dialog" title={tUI("Server & sharing")} onClose={() => setOpen(false)}>
					<div className="hub-panel" aria-busy={busy}>
						<section className="hub-connection">
							<div className="hub-server-heading">
								<span className="hub-server-icon">
									<Server size={24} />
								</span>
								<div>
									<h3>{serverName || tUI("Your Minecraft server")}</h3>
									<p className={"hub-state" + (token ? " online" : "")}>
										{token && <Check size={14} />} {tUI(stateLabel)}
									</p>
								</div>
							</div>
							<p className="hub-intro">
								{tUI(
									token && loaded
										? "Edit your quests, then use Save to server or Ctrl+S. Minecraft validates each save before applying it."
										: "Open Quest Studio from Minecraft with /questplus editor, then approve this browser in the game."
								)}
							</p>
							{!token && (
								<>
									<div className="hub-command">
										<code>{command}</code>
										<button type="button" className="button secondary" onClick={() => void copy(command)}>
											<Copy size={14} />
											{tUI(copied === command ? "Copied" : "Copy command")}
										</button>
									</div>
									{pending ? (
										<div className="hub-approval">
											<strong>{tUI("Compare this code with Minecraft")}</strong>
											<span className="hub-confirm-code">{pending.code}</span>
											<p>{tUI("Approve only if both codes match. You can click the approval message in Minecraft.")}</p>
										</div>
									) : (
										<label className="field">
											<span>{tUI("Session ID")}</span>
											<input
												value={session}
												disabled={busy}
												onChange={(e) => {
													disconnect();
													setSession(e.target.value);
												}}
												spellCheck={false}
												placeholder={tUI("From the editor link in Minecraft")}
											/>
										</label>
									)}
									{!pending && (
										<button type="button" className="button primary" disabled={busy || !session.trim()} onClick={start}>
											<Link2 size={16} />
											{tUI("Connect this browser")}
										</button>
									)}
								</>
							)}
							{token && (
								<>
									<div className="hub-actions">
										<button type="button" className={"button " + (loaded ? "secondary" : "primary")} disabled={busy} onClick={load}>
											<Download size={16} />
											{tUI(loaded ? "Reload server quests" : "Load server quests")}
										</button>
										{loaded && (
											<button type="button" className="button primary" disabled={busy} onClick={save}>
												<UploadCloud size={16} />
												{tUI(busy ? "Saving…" : "Save to server")}
											</button>
										)}
									</div>
									<p className="picker-help">{tUI("Player progress is preserved. Automatic drafts stay in this browser.")}</p>
								</>
							)}
							{(token || pending) && (
								<button type="button" className="subtle hub-disconnect" disabled={busy} onClick={disconnect}>
									{tUI("Disconnect this browser")}
								</button>
							)}
						</section>
						{status && (
							<div className={"hub-status " + status.tone} role={status.tone === "error" ? "alert" : "status"}>
								{status.tone === "error" ? <AlertCircle size={17} /> : status.tone === "success" ? <Check size={17} /> : <UploadCloud size={17} />}
								<div>
									<strong>{tUI(status.message)}</strong>
									{status.detail && <p>{status.detail}</p>}
								</div>
							</div>
						)}
						<details className="hub-disclosure" open={linkedPack ? true : undefined}>
							<summary>
								<Link2 size={16} />
								{tUI("Share a pack or open a link")}
							</summary>
							<div className="hub-disclosure-body">
								<p className="picker-help">{tUI("Create a separate copy to share. To update your connected server, use Save to server.")}</p>
								<button type="button" className="button secondary" disabled={busy} onClick={publishPack}>
									<Link2 size={16} />
									{tUI("Create share link")}
								</button>
								{share && (
									<div className="hub-share">
										<a href={share} target="_blank" rel="noreferrer">
											{share}
										</a>
										<button type="button" className="button secondary" onClick={() => void copy(share)}>
											<Copy size={14} />
											{tUI(copied === share ? "Copied" : "Copy link")}
										</button>
										<p>
											{tUI("Import in Minecraft:")} <code>/questplus import {pack}</code>
										</p>
									</div>
								)}
								<label className="field">
									<span>{tUI("Open shared pack")}</span>
									<div className="hub-actions">
										<input value={pack} disabled={busy} onChange={(e) => setPack(e.target.value)} placeholder={tUI("Pack ID")} />
										<button type="button" className="button secondary" disabled={busy || !pack.trim()} onClick={loadPack}>
											{tUI("Open pack")}
										</button>
									</div>
								</label>
							</div>
						</details>
						<details className="hub-disclosure">
							<summary>
								<Globe size={16} />
								{tUI("Connection settings")}
							</summary>
							<div className="hub-disclosure-body">
								<label className="field">
									<span>{tUI("QuestPlus website address")}</span>
									<input
										value={hub}
										disabled={busy || !!token || !!pending}
										onChange={(e) => {
											disconnect();
											setHub(e.target.value);
										}}
										spellCheck={false}
										placeholder="https://quests.example.com"
									/>
								</label>
								<p className="picker-help">{tUI("Use the website configured on your Minecraft server. HTTPS is required, except on localhost.")}</p>
							</div>
						</details>
					</div>
					<div className="hub-footer">
						<span>{tUI("ZIP export remains available in the editor footer.")}</span>
						<button type="button" className="button secondary" onClick={() => setOpen(false)}>
							{tUI("Back to editor")}
						</button>
					</div>
				</Dialog>
			)}
		</>
	);
}

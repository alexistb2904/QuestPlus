import { AlertCircle, Check, Copy, Download, Globe, Link2, Save, Server, Settings, UploadCloud } from "lucide-react";
import { type ReactNode, useEffect, useMemo, useRef, useState } from "react";
import { hubHttpError, isHubOverloaded } from "../core/hub-errors.js";
import { hubRegistry, serverVersion, setHubRegistry, unsupportedConfigKeys } from "../core/hub-registry.js";
import { closestModVersion } from "../core/mod-versions.js";
import { fileMap, importDocuments, supportedMinecraftVersion, validate } from "../core/project.js";
import type { Project } from "../types";
import { tUI } from "../ui/ui-i18n.js";
import { Dialog } from "./Dialog";
import { ServerSettings } from "./ServerSettings";

type HubData = { files: { name: string; content: string }[]; registry?: Record<string, unknown>; revision: string; serverName?: string };
type Feedback = { message: string; detail?: string; tone: "info" | "success" | "error"; share?: boolean };
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
export function HubPanel({
	project,
	onLoad,
	onSaveLocal,
	onConfig,
}: {
	project: Project;
	onLoad: (p: Project) => void;
	onSaveLocal: () => boolean;
	onConfig: (change: (config: any) => void, field?: string) => void;
}) {
	const [open, setOpen] = useState(Boolean(linkedPack || linkedSession)),
		[tab, setTab] = useState<"server" | "settings" | "share">(linkedPack ? "share" : "server");
	const [hub, setHub] = useState(defaultHub),
		[session, setSession] = useState(linkedSession),
		[pack, setPack] = useState(linkedPack);
	const [pending, setPending] = useState<{ requestId: string; requestSecret: string; code: string } | null>(null);
	const [token, setToken] = useState(""),
		[revision, setRevision] = useState(""),
		[loaded, setLoaded] = useState(false),
		[version, setVersion] = useState("");
	const [serverName, setServerName] = useState(""),
		[savedProject, setSavedProject] = useState(""),
		[share, setShare] = useState("");
	const [status, setStatus] = useState<Feedback | null>(null),
		[busy, setBusy] = useState(false),
		[copied, setCopied] = useState("");
	const running = useRef(false);
	const host = hub.replace(/\/$/, "");
	const dirty = useMemo(() => loaded && JSON.stringify(project) !== savedProject, [loaded, project, savedProject]);
	// share marks messages from the Sharing tab so each tab only shows its own outcome.
	const feedback = (message: string, tone: Feedback["tone"] = "info", detail?: string, share = false) => setStatus({ message, tone, detail, share });
	const run = async (job: () => Promise<void>, share = false) => {
		if (running.current) return;
		running.current = true;
		setBusy(true);
		setStatus(null);
		try {
			await job();
		} catch (e) {
			feedback("Action failed. Your editor changes are kept.", "error", (e as Error).message, share);
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
		setVersion("");
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
	const converted = useRef<string | undefined>(undefined);
	const loadProject = (data: HubData) => {
		const report: { converted?: number } = {},
			next = importDocuments(data.files, report);
		// The connected server's own Minecraft and QuestPlus versions win over the ones saved in the pack.
		const minecraft = supportedMinecraftVersion(data.registry?.minecraftVersion);
		if (minecraft) next.targetMinecraftVersion = minecraft;
		// Servers before 1.1.7 do not report their version. A shared pack has no registry and keeps its own.
		if (data.registry) next.targetModVersion = closestModVersion(typeof data.registry.questplusVersion === "string" ? data.registry.questplusVersion : "1.1.6");
		converted.current = report.converted ? tUI("Objectives converted from an older QuestPlus version:") + " " + report.converted : undefined;
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
				// Approval exists to edit this server, so its quests load right away (load still asks before replacing local work).
				void load(result.editorToken);
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
	const load = (access = token) =>
		run(async () => {
			const response: HubData = await json(host + "/api/editor/sessions/" + encodeURIComponent(session) + "/data", "GET", undefined, access);
			const next = loadProject(response);
			if (next) {
				setRevision(response.revision);
				setLoaded(true);
				setVersion(serverVersion(next.config));
				setSavedProject(JSON.stringify(next));
				feedback("Server quests loaded. Save will apply your changes to this server.", "success", converted.current);
			} else feedback("Browser approved. Load the server quests to start editing.", "success");
		});
	const publish = () =>
		run(async () => {
			const issues = validate(project);
			if (issues.length) throw Error(issues[0].where + ": " + tUI(issues[0].message));
			const unsupported = unsupportedConfigKeys(project.config, version);
			if (unsupported.length) throw Error(tUI("This server's QuestPlus version rejects these settings:") + " " + unsupported.join(", "));
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
			feedback("Share link created for 7 days. This does not update a server.", "success", undefined, true);
		}, true);
	const loadPack = () =>
		run(async () => {
			if (!pack.trim()) throw Error(tUI("Enter a pack ID."));
			if (loadProject(await json(host + "/api/packs/" + encodeURIComponent(pack))))
				feedback(
					token && loaded
						? "Shared pack opened in the editor. Save to publish it to your connected server."
						: "Shared pack opened. Edit it, export a ZIP or connect a server.",
					"success",
					converted.current,
					true
				);
		}, true);
	const approveCommand = pending ? "/questplus editor approve " + pending.code : "";
	const saveLabel = token && loaded ? "Save to server" : session || token ? "Connect to save" : "Save draft";
	const stateLabel = token ? (loaded ? (dirty ? "Unpublished changes" : "Up to date on server") : "Ready to load") : pending ? "Approve in Minecraft" : "No server connected";
	const stateTone = token && loaded ? (dirty ? "warn" : "success") : token || pending ? "info" : "";
	const reported = hubRegistry().questplusVersion;
	const meta = token && loaded ? (reported ? "QuestPlus " + reported : tUI("QuestPlus 1.1.6 or earlier")) : host.replace(/^https?:\/\//, "");
	const commandBox = (value: string) => (
		<div className="hub-command">
			<code>{value}</code>
			<button type="button" className="button secondary" onClick={() => void copy(value)}>
				<Copy size={14} />
				{tUI(copied === value ? "Copied" : "Copy command")}
			</button>
		</div>
	);
	const statusBox = (share: boolean) =>
		status &&
		!status.share === !share && (
		<div className={"hub-status " + status.tone} role={status.tone === "error" ? "alert" : "status"}>
			{status.tone === "error" ? <AlertCircle size={17} /> : status.tone === "success" ? <Check size={17} /> : <UploadCloud size={17} />}
			<div>
				<strong>{tUI(status.message)}</strong>
				{status.detail && <p>{status.detail}</p>}
			</div>
		</div>
	);
	// Three visible steps: the link from Minecraft, the in-game approval, then loading the server quests.
	const step = (index: number, title: string, done: boolean, current: boolean, body: ReactNode) => (
		<li className={"hub-step" + (done ? " done" : current ? " current" : "")} aria-current={current ? "step" : undefined}>
			<span className="hub-step-mark" aria-hidden="true">
				{done ? <Check size={14} strokeWidth={3} /> : index}
			</span>
			<div className="hub-step-body">
				<strong>{tUI(title)}</strong>
				{current && body}
			</div>
		</li>
	);
	const tabs = [
		{ id: "server", icon: <Server size={17} />, label: "Server", hint: stateLabel },
		{ id: "settings", icon: <Settings size={17} />, label: "Settings", hint: "config.json" },
		{ id: "share", icon: <Link2 size={17} />, label: "Sharing", hint: "Link valid for 7 days" },
	] as const;
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
				<Dialog className="reward-library-dialog hub-dialog" title={tUI("Server & sharing")} onClose={() => setOpen(false)}>
					<div className="reward-library hub-layout" aria-busy={busy}>
						<nav className="reward-library-list hub-rail" aria-label={tUI("Server & sharing")}>
							<ul>
								{tabs.map((item) => (
									<li key={item.id}>
										<button type="button" className="reward-library-item" aria-current={tab === item.id} onClick={() => setTab(item.id)}>
											{item.icon}
											<span>
												<strong>{tUI(item.label)}</strong>
												<small>{tUI(item.hint)}</small>
											</span>
										</button>
									</li>
								))}
							</ul>
						</nav>
						<section className="reward-library-main hub-main">
							{tab === "server" && (
								<>
									<header className="hub-head">
										<span className="hub-server-icon">
											<Server size={22} />
										</span>
										<div className="hub-head-text">
											<h3>{serverName || tUI("Your Minecraft server")}</h3>
											<p>{meta}</p>
										</div>
										<span className={"hub-chip " + stateTone}>
											{stateTone === "success" && <Check size={13} strokeWidth={3} />}
											{tUI(stateLabel)}
										</span>
									</header>
									{token && loaded ? (
										<div className="hub-connected">
											<p className="hub-intro">{tUI("Edit your quests, then use Save to server or Ctrl+S. Minecraft validates each save before applying it.")}</p>
											<div className="hub-actions">
												<button type="button" className="button primary" disabled={busy} onClick={save}>
													<UploadCloud size={16} />
													{tUI(busy ? "Saving…" : "Save to server")}
												</button>
												<button type="button" className="button secondary" disabled={busy} onClick={() => load()}>
													<Download size={16} />
													{tUI("Reload server quests")}
												</button>
											</div>
											<p className="picker-help">{tUI("Player progress is preserved. Automatic drafts stay in this browser.")}</p>
										</div>
									) : (
										<ol className="hub-steps">
											{step(
												1,
												"Open the link from Minecraft",
												Boolean(pending || token),
												!pending && !token,
												<>
													<p className="hub-intro">{tUI("In Minecraft, run this command, then open the link it sends.")}</p>
													{commandBox("/questplus editor")}
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
													<button type="button" className="button primary hub-step-action" disabled={busy || !session.trim()} onClick={start}>
														<Link2 size={16} />
														{tUI("Connect this browser")}
													</button>
												</>
											)}
											{step(
												2,
												"Approve the code in Minecraft",
												Boolean(token),
												Boolean(pending),
												<>
													<span className="hub-confirm-code">{pending?.code}</span>
													<p className="hub-intro">{tUI("Approve only if both codes match. You can click the approval message in Minecraft.")}</p>
													{commandBox(approveCommand)}
												</>
											)}
											{step(
												3,
												"Load server quests",
												loaded,
												Boolean(token),
												busy ? (
													<p className="hub-intro hub-loading">{tUI("Loading the server quests…")}</p>
												) : (
													<button type="button" className="button primary hub-step-action" onClick={() => load()}>
														<Download size={16} />
														{tUI("Load server quests")}
													</button>
												)
											)}
										</ol>
									)}
									{statusBox(false)}
									{(token || pending) && (
										<button type="button" className="subtle hub-disconnect" disabled={busy} onClick={disconnect}>
											{tUI("Disconnect this browser")}
										</button>
									)}
									<details className="hub-site">
										<summary>
											<Globe size={15} />
											{tUI("QuestPlus website address")}
										</summary>
										<input
											className="form-control"
											aria-label={tUI("QuestPlus website address")}
											value={hub}
											disabled={busy || !!token || !!pending}
											onChange={(e) => {
												disconnect();
												setHub(e.target.value);
											}}
											spellCheck={false}
											placeholder="https://quests.example.com"
										/>
										<p className="picker-help">{tUI("Use the website configured on your Minecraft server. HTTPS is required, except on localhost.")}</p>
									</details>
								</>
							)}
							{tab === "settings" && <ServerSettings project={project} onConfig={onConfig} version={token && loaded ? version : ""} offline={!token} />}
							{tab === "share" && (
								<>
									<header className="hub-section-head">
										<h3>{tUI("Share a pack or open a link")}</h3>
										<p>{tUI("Create a separate copy to share. To update your connected server, use Save to server.")}</p>
									</header>
									<section className="hub-group">
										<h4>{tUI("Create share link")}</h4>
										<button type="button" className="button secondary hub-step-action" disabled={busy} onClick={publishPack}>
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
									</section>
									<section className="hub-group">
										<h4>{tUI("Open shared pack")}</h4>
										<div className="hub-actions">
											<input className="form-control" aria-label={tUI("Pack ID")} value={pack} disabled={busy} onChange={(e) => setPack(e.target.value)} placeholder={tUI("Pack ID")} />
											<button type="button" className="button secondary" disabled={busy || !pack.trim()} onClick={loadPack}>
												{tUI("Open pack")}
											</button>
										</div>
									</section>
									{statusBox(true)}
								</>
							)}
						</section>
					</div>
					<footer className="reward-library-footer hub-footer">
						<span>{tUI("ZIP export remains available in the editor footer.")}</span>
						<button type="button" className="button secondary" onClick={() => setOpen(false)}>
							{tUI("Back to editor")}
						</button>
					</footer>
				</Dialog>
			)}
		</>
	);
}

import { Box, Castle, ChartColumn, Copy, Droplet, FlaskConical, Gem, Orbit, PawPrint, Search, Sparkles, Target, Trees, Trophy, Upload } from "lucide-react";
import React, { useMemo, useState } from "react";
import { hubLabel, hubRegistry, setHubRegistry } from "../core/hub-registry.js";
import { BIOMES, DIMENSIONS, ENTITIES, STRUCTURES } from "../core/objective-targets.js";
import { resourceMatcher } from "../core/resource-search.js";
import { ENCHANTMENTS_26_3 } from "../data/vanilla-enchantments-26.3.js";
import { inVanillaRegistry, MC_VERSION, vanillaCatalog } from "../ui/minecraft-icons.js";
import { getUiLocale, tUI } from "../ui/ui-i18n.js";
import { Dialog } from "./Dialog";
import { ItemIcon } from "./IconPicker";
import { ResourceGrid } from "./ResourceGrid";

const KINDS: [string, string, React.ReactNode][] = [
	["items", "Items", <Gem size={16} />],
	["blocks", "Blocks", <Box size={16} />],
	["fluids", "Fluids", <Droplet size={16} />],
	["entities", "Entities", <PawPrint size={16} />],
	["effects", "Effects", <FlaskConical size={16} />],
	["enchantments", "Enchantments", <Sparkles size={16} />],
	["biomes", "Biomes", <Trees size={16} />],
	["structures", "Structures", <Castle size={16} />],
	["dimensions", "Dimensions", <Orbit size={16} />],
	["statistics", "Statistics", <ChartColumn size={16} />],
	["advancements", "Advancements", <Trophy size={16} />],
];
const mc = (names: readonly string[]) => names.map((n) => (n.includes(":") ? n : "minecraft:" + n));
const catalog = vanillaCatalog();
/** What the editor already knows offline, so the browser is useful before any server connection. */
const VANILLA: Record<string, string[]> = {
	items: catalog.filter((e) => e.kind !== "block").map((e) => e.id),
	blocks: catalog.filter((e) => e.kind !== "item").map((e) => e.id),
	entities: mc(ENTITIES),
	biomes: mc(BIOMES),
	structures: mc(STRUCTURES),
	dimensions: DIMENSIONS.map(([id]) => id),
	enchantments: ENCHANTMENTS_26_3.map((e) => e.id),
};
const pretty = (id: string) => {
	const name = id.replace(/^#/, "").split(":").pop()!.replace(/[_/]/g, " ");
	return (id.startsWith("#") ? "#" : "") + name.charAt(0).toUpperCase() + name.slice(1);
};

/** Server registry when connected or imported, the bundled vanilla lists otherwise. */
export function RegistryBrowser({ onClose, targetFor, onUse }: { onClose: () => void; targetFor?: (kind: string) => string | null; onUse?: (kind: string, id: string) => void }) {
	const [version, setVersion] = useState(0),
		[imported, setImported] = useState(false),
		[kind, setKind] = useState("items"),
		[query, setQuery] = useState(""),
		[picked, setPicked] = useState(""),
		[copied, setCopied] = useState(""),
		[message, setMessage] = useState<{ tone: "ok" | "error"; text: string } | null>(null);
	const data = useMemo(() => hubRegistry() as Record<string, any>, [version]);
	const list = (k: string) => {
		const server: string[] = Array.isArray(data[k]) ? data[k] : [];
		return server.length ? { ids: [...server, ...(data.tags?.[k] || [])], server: true } : { ids: VANILLA[k] || [], server: false };
	};
	const current = list(kind);
	const entries = useMemo(() => {
		const match = resourceMatcher(query);
		return current.ids.filter((id) => match(id, hubLabel(id, getUiLocale())));
	}, [current.ids, query]);
	const label = (id: string) => (id.replace(/^#/, "").startsWith("minecraft:") ? pretty(id) : hubLabel(id.replace(/^#/, ""), getUiLocale()));
	// Entities borrow their spawn egg, enchantments the enchanted book, so every tile has a picture when one exists.
	const icon =
		kind === "items" || kind === "blocks"
			? (id: string) => <ItemIcon id={id.replace(/^#/, "")} />
			: kind === "entities"
				? (id: string) => (inVanillaRegistry(id + "_spawn_egg") ? <ItemIcon id={id + "_spawn_egg"} /> : null)
				: kind === "enchantments"
					? () => <ItemIcon id="minecraft:enchanted_book" />
					: undefined;
	const target = targetFor?.(kind) ?? null;
	const load = async (file: File) => {
		try {
			if (file.size > 5_000_000) throw Error("Registry snapshot exceeds 5 MB");
			const raw = JSON.parse(await file.text());
			if (!raw || typeof raw !== "object" || Array.isArray(raw)) throw Error("Invalid registry snapshot");
			setHubRegistry(raw.registry && typeof raw.registry === "object" ? raw.registry : raw);
			setVersion((n) => n + 1);
			setImported(true);
			setMessage({ tone: "ok", text: tUI("Registry snapshot loaded locally for this session.") });
		} catch (error) {
			setMessage({ tone: "error", text: tUI((error as Error).message) });
		}
	};
	const copy = async (id: string) => {
		try {
			await navigator.clipboard.writeText(id);
			setCopied(id);
		} catch {
			setPicked("");
			setMessage({ tone: "error", text: tUI("Clipboard unavailable") + ": " + id });
		}
	};
	return (
		<Dialog title={tUI("Mod registry")} onClose={onClose} className="reward-library-dialog registry-workbench">
			<div className="reward-library">
				<nav className="reward-library-list" aria-label={tUI("Resource types")}>
					<ul>
						{KINDS.map(([id, name, glyph]) => {
							const count = list(id).ids.length;
							return (
								<li key={id}>
									<button
										type="button"
										className="reward-library-item registry-kind"
										aria-current={kind === id}
										onClick={() => {
											setKind(id);
											setPicked("");
										}}>
										<span className="registry-kind-icon" aria-hidden="true">
											{glyph}
										</span>
										<strong>{tUI(name)}</strong>
										<small>{count || "–"}</small>
									</button>
								</li>
							);
						})}
					</ul>
					<label className="button secondary registry-import">
						<Upload size={15} aria-hidden="true" />
						{tUI("Import registry JSON")}
						<input
							type="file"
							accept=".json"
							hidden
							onChange={(e) => {
								const file = e.target.files?.[0];
								e.target.value = "";
								if (file) void load(file);
							}}
						/>
					</label>
				</nav>
				<section className="reward-library-main registry-main" aria-label={tUI(KINDS.find(([id]) => id === kind)![1])}>
					<header className="registry-head">
						<label className="bulk-search registry-search">
							<Search size={16} aria-hidden="true" />
							<input autoFocus aria-label={tUI("Search registry resources")} placeholder={tUI("Search by name, mod or ID…")} value={query} onChange={(e) => setQuery(e.target.value)} />
						</label>
						<span className={"registry-source" + (current.server ? " server" : "")}>
							{current.server ? tUI(imported ? "Imported snapshot" : "Server registry") : tUI("Vanilla") + " " + MC_VERSION}
						</span>
						<span className="registry-count">
							{entries.length} {tUI(entries.length === 1 ? "entry" : "entries")}
						</span>
					</header>
					{data.assetsLimited && <p className="picker-help">{tUI("Some server previews exceed the session size limit. All registry IDs remain available.")}</p>}
					{entries.length ? (
						<ResourceGrid
							key={kind + "\0" + query + "\0" + version}
							items={entries.map((id) => ({ id, label: label(id) }))}
							renderIcon={icon}
							selected={picked}
							onPick={setPicked}
						/>
					) : (
						<div className="library-empty registry-empty">
							<strong>{tUI(query ? "No matching resources" : "Nothing known offline for this type")}</strong>
							<span>{tUI(query ? "Try another search." : "Connect to your server from Server & sharing, or import a registry JSON snapshot.")}</span>
						</div>
					)}
				</section>
			</div>
			<footer className="reward-library-footer registry-footer">
				{picked ? (
					<span className="registry-picked">
						{icon?.(picked)}
						<span>
							<strong>{label(picked)}</strong>
							<code>{picked}</code>
						</span>
					</span>
				) : (
					<span role="status" className={message?.tone === "error" ? "template-error" : undefined}>
						{message?.text ?? tUI("Select a resource to copy its ID or use it as an objective target.")}
					</span>
				)}
				<div className="bulk-actions">
					<button type="button" className="button secondary" disabled={!picked} onClick={() => void copy(picked)}>
						<Copy size={15} aria-hidden="true" />
						{tUI(copied && copied === picked ? "Copied" : "Copy ID")}
					</button>
					{onUse && (
						<button type="button" className="button primary" disabled={!picked || !target} title={target ? undefined : tUI("The selected quest has no objective of this type")} onClick={() => onUse(kind, picked)}>
							<Target size={15} aria-hidden="true" />
							{target ? tUI("Set as target") + " · " + target : tUI("Set as objective target")}
						</button>
					)}
				</div>
			</footer>
		</Dialog>
	);
}

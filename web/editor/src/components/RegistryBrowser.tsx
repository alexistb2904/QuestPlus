import { useMemo, useState } from "react";
import { hubLabel, hubRegistry, setHubRegistry } from "../core/hub-registry.js";
import { resourceMatcher } from "../core/resource-search.js";
import { getUiLocale, tUI } from "../ui/ui-i18n.js";
import { Dialog } from "./Dialog";
import { ItemIcon } from "./IconPicker";
import { ResourceGrid } from "./ResourceGrid";

/** Browser backed by the current approved server's registry snapshot, or by a local JSON export. */
export function RegistryBrowser({ onClose, onPick }: { onClose: () => void; onPick?: (kind: string, id: string) => void }) {
	const [version, setVersion] = useState(0),
		[kind, setKind] = useState("items"),
		[query, setQuery] = useState(""),
		[message, setMessage] = useState("");
	const data = hubRegistry() as Record<string, any>;
	const kinds = ["items", "blocks", "fluids", "entities", "effects", "enchantments", "biomes", "structures", "dimensions", "statistics", "advancements"];
	const entries = useMemo(() => {
		const values = Array.isArray(data[kind]) ? (data[kind] as string[]) : [];
		const tags = Array.isArray(data.tags?.[kind]) ? (data.tags[kind] as string[]) : [];
		const matches = resourceMatcher(query);
		return [...values, ...tags].filter((id) => matches(id));
	}, [data, kind, query, version]);
	const load = async (file: File) => {
		try {
			if (file.size > 5_000_000) throw Error("Registry snapshot exceeds 5 MB");
			const raw = JSON.parse(await file.text());
			if (!raw || typeof raw !== "object" || Array.isArray(raw)) throw Error("Invalid registry snapshot");
			setHubRegistry(raw.registry && typeof raw.registry === "object" ? raw.registry : raw);
			setVersion((n) => n + 1);
			setMessage("Registry snapshot loaded locally for this session.");
		} catch (error) {
			setMessage((error as Error).message);
		}
	};
	const copy = async (id: string) => {
		try {
			await navigator.clipboard.writeText(id);
			setMessage("Copied " + id);
		} catch {
			setMessage("Clipboard unavailable: " + id);
		}
	};
	return (
		<Dialog title="Mod Registry Browser" onClose={onClose} className="icon-dialog">
			<p>Search items, blocks and other resources installed on the connected Minecraft server. A local export can also be opened without connecting.</p>
			<label className="button secondary" style={{ display: "inline-flex", cursor: "pointer" }}>
				{tUI("Import registry JSON")}
				<input
					type="file"
					accept=".json"
					hidden
					onChange={(e) => {
						const file = e.target.files?.[0];
						if (file) void load(file);
						e.target.value = "";
					}}
				/>
			</label>
			<div className="picker-filters" style={{ marginTop: 12 }}>
				<select aria-label="Registry type" value={kind} onChange={(e) => setKind(e.target.value)}>
					{kinds.map((k) => (
						<option key={k} value={k}>
							{k} ({Array.isArray(data[k]) ? data[k].length : 0})
						</option>
					))}
				</select>
				<input aria-label="Search registry resources" placeholder="Search modid, ID or tag…" value={query} onChange={(e) => setQuery(e.target.value)} />
			</div>
			<div className="picker-count">
				{entries.length} {tUI(entries.length === 1 ? "entry" : "entries")}
			</div>
			{data.assetsLimited && <p className="picker-help">{tUI("Some server previews exceed the session size limit; all registry IDs remain available.")}</p>}
			<ResourceGrid
				key={kind + "\0" + query + "\0" + version}
				items={entries.map((id) => ({ id, label: !id.startsWith("minecraft:") && !id.startsWith("#") ? hubLabel(id, getUiLocale()) : id }))}
				renderIcon={kind === "items" || kind === "blocks" ? (id) => <ItemIcon id={id.replace(/^#/, "")} /> : undefined}
				onPick={(id) => {
					if (onPick) onPick(kind, id);
					else void copy(id);
				}}
			/>
			{!entries.length && (
				<div className="library-empty">
					<strong>{tUI(query ? "No matching resources" : "No registry data loaded")}</strong>
					<span>
						{tUI(query ? "Try another search." : "Connect through Server Editor to load the server registry, or import a registry JSON snapshot for this session.")}
					</span>
				</div>
			)}
			<p role="status" className="picker-help">
				{message || tUI("Click a resource to copy its ID, or insert it into the selected quest objective.")}
			</p>
		</Dialog>
	);
}

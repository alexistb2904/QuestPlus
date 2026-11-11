import { Package } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { hubLabel, hubTargets, hubTexture } from "../core/hub-registry.js";
import { BIOMES, DIMENSIONS, ENTITIES, STRUCTURES } from "../core/objective-targets.js";
import { resourceMatcher } from "../core/resource-search.js";
import { ENCHANTMENTS_26_3 } from "../data/vanilla-enchantments-26.3.js";
import { iconUrl, inVanillaRegistry, loadVanillaIcons, vanillaCatalog } from "../ui/minecraft-icons.js";
import { getUiLocale, tUI } from "../ui/ui-i18n.js";
import { Dialog } from "./Dialog";
import { ResourceGrid } from "./ResourceGrid";

export function ItemIcon({ id, version = "26.3", size = 30 }: { id?: string; version?: string; size?: number }) {
	const [fail, setFail] = useState(0);
	useEffect(() => setFail(0), [id, version]);
	const url = fail === 0 ? ((id || "").startsWith("data:image/png;base64,") ? id : hubTexture(id || "") || (inVanillaRegistry(id) ? iconUrl(id || "", version) : "")) : "";
	return (
		<span className="mc-icon" title={id?.startsWith("data:") ? tUI("Custom image") : id || ""} style={{ width: size, height: size, flexShrink: 0 }}>
			{url ? <img
					alt=""
					loading="lazy"
					src={url}
					onError={() => setFail((f) => f + 1)}
					// Animated vanilla textures are vertical frame strips. Show only the first frame.
					onLoad={(e) => e.currentTarget.classList.toggle("strip", e.currentTarget.naturalHeight > e.currentTarget.naturalWidth)}
				/> : <Package size={Math.min(size, 24)} aria-hidden="true" />}
		</span>
	);
}
export function IconPicker({ onPick, onClose }: { onPick: (value: string) => void; onClose: () => void }) {
	const [search, setSearch] = useState(""),
		[filter, setFilter] = useState("all"),
		[error, setError] = useState("");
	const upload = async (file: File) => {
		if (file.type !== "image/png" || file.size > 16384) {
			setError(tUI("Choose a PNG up to 16 KB and 128 × 128 pixels."));
			return;
		}
		const url = URL.createObjectURL(file),
			image = new Image();
		try {
			await new Promise<void>((resolve, reject) => {
				image.onload = () => resolve();
				image.onerror = () => reject();
				image.src = url;
			});
			if (image.naturalWidth > 128 || image.naturalHeight > 128 || !image.naturalWidth || !image.naturalHeight) throw Error();
			const data = await new Promise<string>((resolve, reject) => {
				const reader = new FileReader();
				reader.onload = () => resolve(String(reader.result));
				reader.onerror = () => reject(reader.error);
				reader.readAsDataURL(file);
			});
			onPick(data);
		} catch {
			setError(tUI("Choose a PNG up to 16 KB and 128 × 128 pixels."));
		} finally {
			URL.revokeObjectURL(url);
		}
	};
	useEffect(() => {
		loadVanillaIcons().catch(() => {});
	}, []);
	const matches = useMemo(() => {
		const vanilla = vanillaCatalog();
		const known = new Set(vanilla.map((v: any) => v.id));
		const modded = [
			...hubTargets("item").map((id) => ({ id, label: hubLabel(id, getUiLocale()), kind: "item" })),
			...hubTargets("block").map((id) => ({ id, label: hubLabel(id, getUiLocale()), kind: "block" })),
		].filter((item) => {
			if (known.has(item.id)) return false;
			known.add(item.id);
			return true;
		});
		const matches = resourceMatcher(search);
		return [...vanilla, ...modded].filter((item: any) => (filter === "all" || item.kind === filter || item.kind === "both") && matches(item.id, item.label));
	}, [search, filter]);
	return (
		<Dialog title={tUI("Choose an icon")} onClose={onClose} className="icon-dialog">
			<div className="picker-filters">
				<input
					autoFocus
					aria-label={tUI("Search every item and block…")}
					value={search}
					onChange={(e) => setSearch(e.target.value)}
					placeholder={tUI("Search every item and block…")}
				/>
				<select value={filter} onChange={(e) => setFilter(e.target.value)} aria-label={tUI("Icon type")}>
					<option value="all">{tUI("All")}</option>
					<option value="item">{tUI("Items")}</option>
					<option value="block">{tUI("Blocks")}</option>
				</select>
			</div>
			<div className="picker-count">
				{matches.length} {tUI("results")}
			</div>
			<label className="button secondary icon-upload">
				{tUI("Upload image")}
				<input
					type="file"
					accept="image/png"
					hidden
					onChange={(e) => {
						const file = e.target.files?.[0];
						if (file) void upload(file);
						e.target.value = "";
					}}
				/>
			</label>
			{error && (
				<p role="alert" className="error-line">
					{error}
				</p>
			)}
			<ResourceGrid key={search + "\0" + filter} items={matches} onPick={onPick} renderIcon={(id) => <ItemIcon id={id} />} />
			<p className="picker-help">{tUI("Modded items: enter a namespaced ID (e.g. create:brass_ingot) directly.")}</p>
		</Dialog>
	);
}

export function TargetPicker({
	kind,
	onPick,
	onClose,
}: {
	kind: "block" | "item" | "entity" | "biome" | "structure" | "statistic" | "advancement" | "fluid" | "dimension" | "enchantment" | "effect";
	onPick: (value: string) => void;
	onClose: () => void;
}) {
	const [search, setSearch] = useState(""),
		[custom, setCustom] = useState("");
	const catalogue = useMemo(() => {
		let defaults: string[];
		if (kind === "biome") defaults = BIOMES;
		else if (kind === "structure") defaults = STRUCTURES;
		else if (kind === "entity") defaults = ENTITIES;
		else if (kind === "dimension") defaults = DIMENSIONS.map(([id]) => id);
		else if (kind === "enchantment") defaults = ENCHANTMENTS_26_3.map((e) => e.id);
		else if (kind === "effect") defaults = [];
		else if (kind === "statistic" || kind === "advancement" || kind === "fluid") defaults = kind === "fluid" ? ["minecraft:water", "minecraft:lava"] : [];
		else
			defaults = vanillaCatalog()
				.filter((item: any) => (kind === "block" ? item.kind === "block" || item.kind === "both" : item.kind === "item" || item.kind === "both"))
				.map((item: any) => item.id);
		return [...new Set([...defaults, ...hubTargets(kind)])].sort();
	}, [kind]);
	const matches = useMemo(() => {
		const match = resourceMatcher(search);
		return catalogue.filter((id) => match(id));
	}, [catalogue, search]);
	const valid = /^#?[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(custom.trim());
	return (
		<Dialog title={tUI("Choose target")} onClose={onClose} className="icon-dialog">
			<div className="picker-filters">
				<input autoFocus value={search} onChange={(e) => setSearch(e.target.value)} placeholder={tUI("Search targets…")} aria-label={tUI("Search targets…")} />
			</div>
			<div className="picker-count">
				{matches.length} {tUI("results")}
			</div>
			<ResourceGrid
				key={kind + "\0" + search}
				items={matches.map((id) => ({ id, label: id.startsWith("minecraft:") ? id.replace("minecraft:", "").replace(/_/g, " ") : hubLabel(id, getUiLocale()) }))}
				onPick={onPick}
				renderIcon={kind === "block" || kind === "item" ? (id) => <ItemIcon id={id} /> : undefined}
			/>
			<div className="target-custom">
				<input aria-label={tUI("Modded target ID")} placeholder="modid:custom_target" value={custom} spellCheck={false} onChange={(e) => setCustom(e.target.value)} />
				<button className="button primary" type="button" disabled={!valid} onClick={() => onPick(custom.trim())}>
					{tUI("Use ID")}
				</button>
			</div>
		</Dialog>
	);
}

import { Plus, X } from "lucide-react";
import { useEffect, useId, useState } from "react";
import { tUI } from "../ui/ui-i18n.js";
import { EnchantmentPicker } from "./EnchantmentPicker";
import { ItemIcon, TargetPicker } from "./IconPicker";

export type GearItem = { slot: string; item: string; enchantments?: Record<string, number> };
export const ARMOR_SLOTS = ["head", "chest", "legs", "feet"] as const;
export const GEAR_SLOTS = ["inventory", "mainhand", "offhand", ...ARMOR_SLOTS];
export const MATERIALS = ["leather", "copper", "chainmail", "iron", "golden", "diamond", "netherite"];
const enchantId = /^[a-z0-9_.-]+:[a-z0-9_./-]+$/;
export const getGear = (conditions: any): GearItem[] => {
	const node = conditions?.["questplus:gear"] ? conditions : conditions?.and?.find((c: any) => c?.["questplus:gear"]);
	return node?.["questplus:gear"]?.items || [];
};
export const updateGear = (conditions: any, items: GearItem[]) => {
	const gear = { "questplus:gear": { items } };
	if (!conditions || conditions["questplus:gear"]) return gear;
	if (Array.isArray(conditions.and)) {
		const remaining = conditions.and.filter((c: any) => !c?.["questplus:gear"]);
		return { and: [...remaining, gear] };
	}
	return { and: [conditions, gear] };
};
export const armorPreset = (material: string): GearItem[] => {
	const suffixes = ["helmet", "chestplate", "leggings", "boots"];
	const base = material === "gold" ? "golden" : material;
	return ARMOR_SLOTS.map((slot, i) => ({ slot, item: "minecraft:" + base + "_" + suffixes[i], enchantments: {} }));
};

function EnchantmentId({ id, taken, onRename, onBrowse }: { id: string; taken: Record<string, number>; onRename: (next: string) => void; onBrowse: () => void }) {
	const [draft, setDraft] = useState(id);
	const inputId = useId();
	useEffect(() => setDraft(id), [id]);
	const invalid = !enchantId.test(draft.trim()) || (draft.trim() !== id && Object.hasOwn(taken, draft.trim()));
	const commit = () => {
		if (!invalid && draft.trim() !== id) onRename(draft.trim());
	};
	return (
		<div className="field gear-enchantment-id">
			<label htmlFor={inputId}>{tUI("Enchantment ID")}</label>
			<div className="gear-enchantment-id-entry">
				<input
					id={inputId}
					spellCheck={false}
					value={draft}
					aria-label={tUI("Enchantment ID")}
					aria-invalid={invalid}
					title={invalid ? tUI("Invalid or duplicate enchantment ID") : undefined}
					onChange={(e) => setDraft(e.target.value)}
					onBlur={commit}
					onKeyDown={(e) => {
						if (e.key === "Enter") {
							e.preventDefault();
							commit();
							e.currentTarget.blur();
						}
					}}
				/>
				<button type="button" className="button secondary" onClick={onBrowse}>
					{tUI("Browse")}
				</button>
			</div>
		</div>
	);
}

// Slot labels read as the second half of a sentence: "Iron helmet, worn on head".
const SLOT_PHRASES: Record<string, string> = {
	inventory: "In inventory",
	mainhand: "In main hand",
	offhand: "In off hand",
	head: "Worn on head",
	chest: "Worn on chest",
	legs: "Worn on legs",
	feet: "Worn on feet",
};
const itemName = (id: string) =>
	String(id || "")
		.split(":")
		.pop()
		?.replace(/_/g, " ") || "";

export function GearEditor({ conditions, onChange }: { conditions: any; onChange: (next: any) => void }) {
	const [pick, setPick] = useState<number | null>(null);
	const [pickEnchant, setPickEnchant] = useState<{ item: number; previous?: string } | null>(null);
	const items = getGear(conditions);
	const change = (next: GearItem[]) => onChange(updateGear(conditions, next));
	const edit = (index: number, partial: Partial<GearItem>) => change(items.map((item, i) => (i === index ? { ...item, ...partial } : item)));
	const enchant = (index: number, id: string, value: number | null) => {
		const enchantments = { ...(items[index].enchantments || {}) };
		if (value === null) delete enchantments[id];
		else enchantments[id] = Math.max(1, Math.min(10, Math.round(value) || 1));
		edit(index, { enchantments });
	};
	return (
		<div className="gear-editor">
			<ul className="gear-list" aria-label={tUI("Required equipment")}>
				{items.map((gear, i) => (
					<li className="gear-row" key={i}>
						<ItemIcon id={gear.item} size={32} />
						<div className="gear-row-main">
							<div className="gear-row-title">
								<strong title={gear.item}>{itemName(gear.item) || tUI("Equipment item")}</strong>
								<span className="field gear-slot">
									<select value={gear.slot} aria-label={tUI("Equipment slot") + " " + (i + 1)} onChange={(e) => edit(i, { slot: e.target.value })}>
										{GEAR_SLOTS.map((slot) => (
											<option key={slot} value={slot}>
												{tUI(SLOT_PHRASES[slot] || slot)}
											</option>
										))}
									</select>
								</span>
							</div>
							<span className="gear-item-entry">
								<input
									value={gear.item}
									spellCheck={false}
									aria-label={tUI("Equipment item") + " " + (i + 1)}
									onChange={(e) => edit(i, { item: e.target.value })}
									placeholder="modid:custom_armor"
								/>
								<button type="button" className="button secondary" onClick={() => setPick(i)}>
									{tUI("Browse")}
								</button>
							</span>
							{Object.entries(gear.enchantments || {}).map(([id, level]) => (
								<div className="gear-enchantment" key={id}>
									<EnchantmentId
										id={id}
										taken={gear.enchantments || {}}
										onBrowse={() => setPickEnchant({ item: i, previous: id })}
										onRename={(newId) => {
											const next = { ...(gear.enchantments || {}) };
											delete next[id];
											next[newId] = level;
											edit(i, { enchantments: next });
										}}
									/>
									<label className="field gear-enchantment-level">
										<span>{tUI("Minimum level")}</span>
										<input type="number" min={1} max={10} value={level} onChange={(e) => enchant(i, id, Number(e.target.value))} />
									</label>
									<button type="button" className="tiny danger" aria-label={tUI("Remove enchantment")} onClick={() => enchant(i, id, null)}>
										<X size={14} aria-hidden="true" />
									</button>
								</div>
							))}
							{/* Modded IDs stay possible: pick any enchantment, then edit its ID field. */}
							<button type="button" className="gear-add-enchantment" title={tUI("Browse enchantments")} onClick={() => setPickEnchant({ item: i })}>
								<Plus size={13} aria-hidden="true" />
								{tUI("Enchantment")}
							</button>
						</div>
						<button
							type="button"
							className="tiny danger"
							aria-label={tUI("Remove equipment") + " " + (i + 1)}
							title={tUI("Remove equipment")}
							onClick={() => change(items.filter((_, n) => n !== i))}>
							<X size={14} aria-hidden="true" />
						</button>
					</li>
				))}
			</ul>
			<div className="gear-toolbar">
				<button type="button" className="button secondary" onClick={() => change([...items, { slot: "mainhand", item: "minecraft:diamond_pickaxe", enchantments: {} }])}>
					<Plus size={14} aria-hidden="true" />
					{tUI("Add equipment")}
				</button>
				<label className="gear-preset">
					<span>{tUI("Armor preset")}</span>
					<span className="field">
						<select
							defaultValue=""
							aria-label={tUI("Armor preset")}
							onChange={(e) => {
								if (e.target.value) change(armorPreset(e.target.value));
								e.target.value = "";
							}}>
							<option value="">{tUI("Select set…")}</option>
							{MATERIALS.map((m) => (
								<option key={m} value={m}>
									{tUI(m)}
								</option>
							))}
						</select>
					</span>
				</label>
			</div>
			{pickEnchant !== null && (
				<EnchantmentPicker
					taken={Object.keys(items[pickEnchant.item]?.enchantments || {})}
					replaceId={pickEnchant.previous}
					onPick={(id) => {
						const current = items[pickEnchant.item];
						if (!current) {
							setPickEnchant(null);
							return;
						}
						const next = { ...(current.enchantments || {}) };
						if (pickEnchant.previous && pickEnchant.previous !== id) {
							const preserved = next[pickEnchant.previous];
							delete next[pickEnchant.previous];
							next[id] = preserved || 1;
						} else if (!pickEnchant.previous) next[id] = 1;
						edit(pickEnchant.item, { enchantments: next });
						setPickEnchant(null);
					}}
					onClose={() => setPickEnchant(null)}
				/>
			)}
			{pick !== null && (
				<TargetPicker
					kind="item"
					onPick={(id) => {
						edit(pick, { item: id });
						setPick(null);
					}}
					onClose={() => setPick(null)}
				/>
			)}
		</div>
	);
}

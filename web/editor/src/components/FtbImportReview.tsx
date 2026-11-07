import { AlertTriangle, ArrowRight, Download, Search } from "lucide-react";
import { useMemo, useState } from "react";
import { inspectFtbPack, selectFtbQuests } from "../core/ftb-import.js";
import { tUI as t } from "../ui/ui-i18n.js";
import { Dialog } from "./Dialog";
type Pending = { report: ReturnType<typeof inspectFtbPack>; documents: { name: string; content: string }[]; files: File[] };
export function FtbImportReview({
	pendingImport,
	onConfirm,
	onCancel,
}: {
	pendingImport: Pending;
	onConfirm: (selection: string[], options?: { fluidUnits: string; energyRatio: number }) => void;
	onCancel: () => void;
}) {
	const [search, setSearch] = useState(""),
		[fluidUnits, setFluidUnits] = useState("millibuckets"),
		[energyRatio, setEnergyRatio] = useState(1);
	const { files } = pendingImport;
	const report = useMemo(
		() => (fluidUnits === "millibuckets" && energyRatio === 1 ? pendingImport.report : inspectFtbPack(pendingImport.documents, { fluidUnits, energyRatio })),
		[pendingImport, fluidUnits, energyRatio]
	);
	const [selected, setSelected] = useState<string[]>(() => report.project.quests.map((q: { id: string }) => q.id));
	const effective = selectFtbQuests(
		report,
		selected.filter((id) => report.project.quests.some((q: { id: string }) => q.id === id))
	);
	const warnings = report.warnings.filter((w: string) => w.toLowerCase().includes(search.toLowerCase()));
	const entries = [...report.sourceIdMap.entries()].filter(([a, b]) => (a + " " + b).toLowerCase().includes(search.toLowerCase()));
	const download = (blob: Blob, name: string) => {
		const url = URL.createObjectURL(blob),
			a = document.createElement("a");
		a.href = url;
		a.download = name;
		a.click();
		setTimeout(() => URL.revokeObjectURL(url), 1500);
	};
	return (
		<Dialog title={t("Review FTB import")} onClose={onCancel} className="ftb-dialog">
			<div className="ftb-body">
				<div className="ftb-intro">
					<div className="ftb-stats">
						<span>
							<strong>{report.chapterCount}</strong> {t("Chapters")}
						</span>
						<span>
							<strong>{report.questCount}</strong> {t("Quests")}
						</span>
						<span className={report.warnings.length ? "has-warnings" : ""}>
							<strong>{report.warnings.length}</strong> {t("Warnings")}
						</span>
					</div>
					<p>{t("Unsupported quests and their dependents are excluded. Review the mapping before replacing your project. Undo restores it.")}</p>
				</div>
				<div className="ftb-controls">
					<label className="field ftb-search">
						<span>{t("Search report")}</span>
						<div>
							<Search size={16} aria-hidden="true" />
							<input value={search} onChange={(e) => setSearch(e.target.value)} />
						</div>
					</label>
					<div className="field-row">
						<label className="field">
							<span>{t("Source fluid units")}</span>
							<select value={fluidUnits} onChange={(e) => setFluidUnits(e.target.value)}>
								<option value="millibuckets">mB (Forge / NeoForge)</option>
								<option value="droplets">{t("Droplets (Fabric)")}</option>
							</select>
						</label>
						<label className="field">
							<span>{t("FE per Fabric energy unit")}</span>
							<input
								type="number"
								min={1}
								max={1000000}
								step={1}
								value={energyRatio}
								onChange={(e) => setEnergyRatio(Math.max(1, Math.min(1000000, Math.trunc(Number(e.target.value) || 1))))}
							/>
						</label>
					</div>
				</div>
				<section className="ftb-section">
					<div className="ftb-section-heading">
						<h3>
							<AlertTriangle size={16} aria-hidden="true" /> {t("Warnings")}
						</h3>
						<span>
							{warnings.length} / {report.warnings.length}
						</span>
					</div>
					<div className="ftb-warnings">
						{warnings.map((warning: string, i: number) => (
							<p key={i}>
								<strong>{/skipped|not imported|invalid|ambiguous/i.test(warning) ? t("Blocked") : t("Warning")}</strong>
								<span>{warning}</span>
							</p>
						))}
						{!warnings.length && <p>{t("No matching warnings.")}</p>}
					</div>
				</section>
				<section className="ftb-section">
					<div className="ftb-section-heading">
						<div>
							<h3>{t("Source ID mapping")}</h3>
							<p>
								{effective.quests.length} {t("Quests")} · {t("Prerequisites are included automatically.")}
							</p>
						</div>
						<div className="toolbar-row">
							<button className="button secondary" onClick={() => setSelected(report.project.quests.map((q: { id: string }) => q.id))}>
								{t("Select all")}
							</button>
							<button className="button secondary" onClick={() => setSelected([])}>
								{t("Clear selection")}
							</button>
						</div>
					</div>
					<div className="ftb-mapping">
						{entries.map(([source, target]) => {
							const quest = report.project.quests.find((q: { id: string }) => q.id === target);
							return (
								<label className="checkbox-row ftb-map-row" key={source}>
									<input
										type="checkbox"
										disabled={!quest}
										checked={selected.includes(target)}
										onChange={(e) => setSelected(e.target.checked ? [...selected, target] : selected.filter((id) => id !== target))}
									/>
									<span className="ftb-map-content">
										<strong>
											{quest?.title?.en_us || source}
											{!quest && <em>{t("Excluded")}</em>}
										</strong>
										<span className="ftb-ids">
											<span>{source}</span>
											<ArrowRight size={13} aria-hidden="true" />
											<span>{target}</span>
										</span>
									</span>
								</label>
							);
						})}
					</div>
				</section>
			</div>
			<div className="ftb-footer">
				<div className="toolbar-row">
					<button className="button secondary" onClick={() => files.forEach((file) => download(file, file.name))}>
						<Download size={14} />
						{t("Download source backup")}
					</button>
					<button
						className="button secondary"
						onClick={() =>
							download(
								new Blob(
									[
										JSON.stringify(
											{
												warnings: report.warnings,
												mapping: Object.fromEntries(report.sourceIdMap),
												selected: effective.quests.map((q: { id: string }) => q.id),
											},
											null,
											2
										),
									],
									{ type: "application/json" }
								),
								"ftb-import-report.json"
							)
						}>
						{t("Download report")}
					</button>
				</div>
				<div className="toolbar-row">
					<button className="button secondary" onClick={onCancel}>
						{t("Cancel")}
					</button>
					<button
						className="button primary"
						disabled={!effective.quests.length}
						onClick={() =>
							onConfirm(
								effective.quests.map((q: { id: string }) => q.id),
								{ fluidUnits, energyRatio }
							)
						}>
						{t("Replace project")}
					</button>
				</div>
			</div>
		</Dialog>
	);
}

import React, { useEffect, useMemo, useRef, useState } from "react";
import {
	Save,
	Download,
	FolderOpen,
	Undo2,
	Redo2,
	Plus,
	Search,
	Grid2X2,
	Magnet,
	Minus,
	Maximize,
	MousePointer2,
	Hand,
	Workflow,
	StickyNote,
	Check,
	TriangleAlert,
	ChevronRight,
	Globe,
	Eye,
	Package,
	PanelLeftOpen,
	SlidersHorizontal,
	GitBranch,
} from "lucide-react";
import { descriptionParts, descriptionHtml } from "./core/description-format.js";
import { text, validate, objectiveLabel } from "./core/project.js";
import { storedUiLocale, setUiLocale, tUI } from "./ui/ui-i18n.js";
import { localesOf, supportedLocales } from "./types";
import { useStudio, owner } from "./hooks/useStudio";
import { Sidebar } from "./components/Sidebar";
import { TreeCanvas } from "./components/TreeCanvas";
import { Inspector } from "./components/Inspector";
import { QuestDetails } from "./components/QuestDetails";
import { IconPicker, ItemIcon } from "./components/IconPicker";
import { Dialog } from "./components/Dialog";
const logo = import.meta.env.BASE_URL + "questplus-logo.png";

export default function App() {
	const studio = useStudio(),
		{ state, setState } = studio;
	const { project, categoryId, selection, camera, language } = state;
	const [uiLocale, setInterfaceLocale] = useState(() => {
		const l = storedUiLocale(globalThis.localStorage, ["en"]);
		setUiLocale(l);
		return l;
	});
	const [picker, setPicker] = useState<string | null>(null),
		[jsonOpen, setJsonOpen] = useState(false),
		[jsonContent, setJsonContent] = useState(""),
		[jsonError, setJsonError] = useState(""),
		[issuesOpen, setIssuesOpen] = useState(false),
		[previewOpen, setPreviewOpen] = useState(false);
	const [search, setSearch] = useState(""),
		[grid, setGrid] = useState(true),
		[snap, setSnap] = useState(false),
		[gridSize, setGridSize] = useState(32),
		[tool, setTool] = useState<"select" | "pan">("select"),
		[libraryOpen, setLibraryOpen] = useState(() => window.matchMedia("(min-width:1200px)").matches),
		[mobilePanel, setMobilePanel] = useState<"canvas" | "properties">("canvas");
	const files = useRef<HTMLInputElement>(null),
		folder = useRef<HTMLInputElement>(null),
		searchRef = useRef<HTMLInputElement>(null);
	const locales = useMemo(() => localesOf(project), [project]),
		issues = useMemo(() => validate(project), [project]);
	const cat = studio.currentCategory,
		quests = studio.quests;
	const selected = selection?.type === "quest" ? project.quests.find((q) => q.id === selection.id) : undefined;
	useEffect(() => {
		const media = window.matchMedia("(min-width:1200px)");
		const adapt = () => setLibraryOpen(media.matches);
		media.addEventListener("change", adapt);
		return () => media.removeEventListener("change", adapt);
	}, []);
	useEffect(() => {
		document.documentElement.lang = uiLocale;
	}, [uiLocale]);
	useEffect(() => {
		const callback = (event: KeyboardEvent) => {
			if (!(event.ctrlKey || event.metaKey)) return;
			const key = event.key.toLowerCase();
			if (key === "s") {
				event.preventDefault();
				studio.save();
				return;
			}
			if (key === "k") {
				event.preventDefault();
				searchRef.current?.focus();
				return;
			}
			if (key !== "z" || (event.target as HTMLElement)?.closest("input,textarea,[contenteditable]")) return;
			event.preventDefault();
			if (event.shiftKey) studio.redo();
			else studio.undo();
		};
		window.addEventListener("keydown", callback);
		return () => window.removeEventListener("keydown", callback);
	});
	const changeCamera = (next: any) => setState((s) => ({ ...s, camera: next }));
	const editJson = () => {
		setJsonContent(JSON.stringify(owner(project, selection), null, 2));
		setJsonError("");
		setJsonOpen(true);
	};
	const doAction = (type: string, index?: number) => {
		if (type === "advanced-json") return editJson();
		studio.action(type, index);
	};
	const goQuest = (id: string) => {
		if (!window.matchMedia("(min-width:1200px)").matches) setLibraryOpen(false);
		const q = project.quests.find((q) => q.id === id);
		if (!q) return;
		setState((s) => ({
			...s,
			categoryId: q.category,
			selection: { type: "quest", id },
			camera: q.layout
				? { ...s.camera, x: q.layout.x, y: q.layout.y }
				: studio.fit(
						project.categories.find((c) => c.id === q.category),
						project.quests.filter((row) => row.category === q.category)
					),
		}));
	};
	const inspectorProps = {
		project,
		selection,
		language,
		onUpdate: studio.setField,
		onAction: doAction,
		onChooseIcon: setPicker,
		onCondition: studio.changeCondition,
		onAddLocale: studio.addLocale,
	};
	return (
		<div className="app">
			<header className="topbar stone">
				<div className="brand">
					<img src={logo} alt="" width="44" height="44" />
					<div>
						<div className="brand-name">
							QuestPlus <span>Studio</span>
						</div>
						<div className="brand-caption">{tUI("Quest Editor for Minecraft Mods")}</div>
					</div>
				</div>
				<div className="editor-plaque">
					<Workflow size={26} />
					<span>{tUI("Quest Editor")}</span>
				</div>
				<div className="landscape-banner" role="img" aria-label={tUI("Minecraft landscape")} />
				<div className="top-actions">
					<label className="language-control">
						<Globe size={15} />
						<span className="sr-only">Interface</span>
						<select
							id="ui-language"
							value={uiLocale}
							onChange={(e) => {
								setUiLocale(e.target.value, { save: true });
								setInterfaceLocale(e.target.value);
							}}>
							<option value="en">EN</option>
							<option value="fr">FR</option>
							<option value="es">ES</option>
							<option value="zh">中文</option>
						</select>
					</label>
					<span className="edition">JAVA 26.3</span>
				</div>
			</header>
			<div className="command-bar stone" role="toolbar" aria-label={tUI("Editor actions")}>
				<button className="button primary" onClick={studio.save} title="Ctrl+S">
					<Save size={18} />
					{tUI("Save")}
				</button>
				<button className="button secondary" onClick={() => setPreviewOpen(true)} disabled={!selected}>
					<Eye size={18} />
					{tUI("Preview")}
				</button>
				<span className="divider" />
				<button className="button secondary history-button" aria-label={tUI("Undo")} onClick={studio.undo} disabled={!studio.history.current.length} title="Ctrl+Z">
					<Undo2 size={17} />
					<span>{tUI("Undo")}</span>
				</button>
				<button className="button secondary history-button" aria-label={tUI("Redo")} onClick={studio.redo} disabled={!studio.future.current.length} title="Ctrl+Shift+Z">
					<Redo2 size={17} />
					<span>{tUI("Redo")}</span>
				</button>
				<span className="divider" />
				<button className="icon-button" aria-label={tUI("Zoom out")} onClick={() => changeCamera({ ...camera, zoom: Math.max(0.25, camera.zoom / 1.2) })}>
					<Minus size={17} />
				</button>
				<button className="zoom-reset" onClick={() => changeCamera({ ...camera, zoom: 1 })} title={tUI("Reset zoom")}>
					{Math.round(camera.zoom * 100)}%
				</button>
				<button className="icon-button" aria-label={tUI("Zoom in")} onClick={() => changeCamera({ ...camera, zoom: Math.min(2.5, camera.zoom * 1.2) })}>
					<Plus size={17} />
				</button>
				<button className={"icon-button toggle" + (grid ? " active" : "")} aria-label={tUI("Show grid")} aria-pressed={grid} onClick={() => setGrid((v) => !v)}>
					<Grid2X2 size={18} />
				</button>
				<select className="grid-select" aria-label={tUI("Grid size")} value={gridSize} onChange={(e) => setGridSize(Number(e.target.value))}>
					{[8, 16, 32].map((n) => (
						<option key={n} value={n}>
							{n} px
						</option>
					))}
				</select>
				<button className={"button secondary snap-button" + (snap ? " active" : "")} aria-label={tUI("Snap")} aria-pressed={snap} onClick={() => setSnap((v) => !v)}>
					<Magnet size={16} />
					<span>{tUI("Snap")}</span>
				</button>
				<div className="global-search">
					<Search size={17} />
					<input ref={searchRef} aria-label={tUI("Search quests…")} placeholder={tUI("Search quests…")} value={search} onChange={(e) => setSearch(e.target.value)} />
					<kbd>Ctrl K</kbd>
				</div>
				<button className="button violet" onClick={studio.createCategory}>
					<FolderOpen size={17} />
					<span>{tUI("Add Category")}</span>
				</button>
				<button className="button primary" disabled={!cat} onClick={studio.createQuest}>
					<Plus size={19} />
					<span>{tUI("Add Quest")}</span>
				</button>
			</div>
			<input
				ref={files}
				hidden
				type="file"
				multiple
				accept=".json,.zip"
				onChange={(e) => {
					void studio.importFiles(e.target.files);
					e.target.value = "";
				}}
			/>
			<input
				ref={(el) => {
					folder.current = el;
					if (el) el.setAttribute("webkitdirectory", "");
				}}
				hidden
				type="file"
				multiple
				onChange={(e) => {
					void studio.importFiles(e.target.files);
					e.target.value = "";
				}}
			/>
			<nav className="workspace-switcher" aria-label={tUI("Workspace panels")}>
				<button className="library-toggle" onClick={() => setLibraryOpen((v) => !v)} aria-expanded={libraryOpen}>
					<FolderOpen size={17} />
					{tUI("Library")}
				</button>
				<button aria-pressed={mobilePanel === "canvas"} onClick={() => setMobilePanel("canvas")}>
					<GitBranch size={17} />
					{tUI("Quest Diagram")}
				</button>
				<button aria-pressed={mobilePanel === "properties"} onClick={() => setMobilePanel("properties")}>
					<SlidersHorizontal size={17} />
					{tUI("Properties")}
				</button>
			</nav>
			{libraryOpen && <button className="library-backdrop" aria-label={tUI("Close library")} onClick={() => setLibraryOpen(false)} />}
			<div data-panel={mobilePanel} className={"workspace" + (!libraryOpen ? " library-collapsed" : "")}>
				{libraryOpen && (
					<Sidebar
						project={project}
						categoryId={categoryId}
						quests={quests}
						selection={selection}
						language={language}
						onCategory={studio.selectCategory}
						onQuest={goQuest}
						onNewCategory={studio.createCategory}
						onReset={studio.reset}
						search={search}
						onSearch={setSearch}
						onCollapse={() => setLibraryOpen(false)}
						onImport={() => files.current?.click()}
						onImportFolder={() => folder.current?.click()}
					/>
				)}
				<main className="main" id="graph-panel">
					<div className="canvas-shell">
						<div className="canvas-toolbar stone">
							<div className="breadcrumbs">
								{!libraryOpen && (
									<button className="icon-button" onClick={() => setLibraryOpen(true)} aria-label={tUI("Open library")}>
										<PanelLeftOpen size={17} />
									</button>
								)}
								<Workflow size={19} />
								<span>{tUI("Quest Diagram")}</span>
								<ChevronRight size={14} />
								<button
									className="breadcrumb-category"
									disabled={!cat}
									onClick={() => categoryId && setState((s) => ({ ...s, selection: { type: "category", id: categoryId } }))}>
									{text(cat?.title, language) || tUI("Quest tree")}
								</button>
								<span className="count">{quests.length}</span>
							</div>
							<div className="canvas-tools">
								<button
									className={"icon-button" + (tool === "select" ? " active" : "")}
									onClick={() => setTool("select")}
									aria-pressed={tool === "select"}
									title={tUI("Select")}
									aria-label={tUI("Select")}>
									<MousePointer2 size={18} />
								</button>
								<button
									className={"icon-button" + (tool === "pan" ? " active" : "")}
									onClick={() => setTool("pan")}
									aria-pressed={tool === "pan"}
									title={tUI("Pan")}
									aria-label={tUI("Pan")}>
									<Hand size={18} />
								</button>
								<button className="icon-button" disabled={!cat} onClick={studio.autoLayout} title={tUI("Auto-layout")} aria-label={tUI("Auto-layout")}>
									<Workflow size={18} />
								</button>
								<button className="icon-button" onClick={() => changeCamera(studio.fit())} title={tUI("Fit")} aria-label={tUI("Fit")}>
									<Maximize size={18} />
								</button>
							</div>
						</div>
						<TreeCanvas
							allQuests={project.quests}
							onLink={studio.connectQuests}
							quests={quests}
							category={cat}
							language={language}
							selection={selection}
							camera={camera}
							onCamera={changeCamera}
							onSelect={(s) => setState((old) => ({ ...old, selection: s }))}
							onPosition={studio.setPosition}
							showGrid={grid}
							gridSize={gridSize}
							snap={snap}
							tool={tool}
						/>
						<div className="canvas-bottom">
							<span>
								<MousePointer2 size={12} />
								{tUI("Drag to move · Scroll to zoom")}
							</span>
							<button className="subtle" disabled={!cat} onClick={studio.createAnnotation}>
								<StickyNote size={13} />
								{tUI("Add annotation")}
							</button>
							<span className="coordinates">
								x: {Math.round(camera.x)} &nbsp; y: {Math.round(camera.y)}
							</span>
						</div>
					</div>
					<QuestDetails {...inspectorProps} />
					<div className={"issues-bar stone" + (issues.length ? " error" : "")} role="status">
						<span className="issues-icon">{issues.length ? <TriangleAlert size={16} /> : <Check size={16} />}</span>
						<button className="subtle validation-button" onClick={() => setIssuesOpen((v) => !v)} aria-expanded={issuesOpen}>
							{issues.length ? issues.length + " " + tUI("errors") : tUI("Validation: No issues found")}
						</button>
						<span className="pack-stats">
							{project.quests.length} {tUI("Quests")}
							<span className="status-separator">|</span>
							{project.categories.length} {tUI("Categories")}
						</span>
						<span className={"save-status " + studio.saveStatus}>
							{tUI(studio.saveStatus === "saved" ? "Saved locally" : studio.saveStatus === "error" ? "Local save failed" : "Saving…")}
						</span>
					</div>
					{issuesOpen && (
						<div className="issues-list" id="issues-list">
							{issues.length ? (
								issues.map((x: any, i: number) => (
									<button
										key={i}
										className="issue-item"
										onClick={() => {
											const q = project.quests.find((q) => q.id === x.where);
											if (q) goQuest(q.id);
										}}>
										<strong>{x.where}</strong> — {x.message}
									</button>
								))
							) : (
								<div>{tUI("No errors")}</div>
							)}
						</div>
					)}
				</main>
				<div className="properties-pane" id="properties-panel">
					<Inspector {...inspectorProps} />
				</div>
			</div>
			<footer className="project-bar">
				<label className="language-control">
					{tUI("Preview language")}
					<select id="preview-language" value={language} onChange={(e) => setState((s) => ({ ...s, language: e.target.value }))}>
						{locales.map((id) => (
							<option key={id} value={id}>
								{supportedLocales[id] || id} · {id}
							</option>
						))}
					</select>
				</label>
				<label className="version-selector">
					Minecraft
					<input
						aria-label={tUI("Target Minecraft version")}
						value={project.targetMinecraftVersion || "26.3"}
						onChange={(e) =>
							studio.mutate((s) => {
								s.project.targetMinecraftVersion = e.target.value;
							}, "version")
						}
					/>
				</label>
				<button
					className="button primary export-button"
					onClick={() => {
						if (!studio.exportZip()) setIssuesOpen(true);
					}}>
					<Download size={16} />
					{tUI("Export ZIP")}
				</button>
			</footer>
			{picker && (
				<IconPicker
					onPick={(id) => {
						studio.setField(picker, id);
						setPicker(null);
					}}
					onClose={() => setPicker(null)}
				/>
			)}
			{jsonOpen && (
				<Dialog title={tUI("Advanced JSON")} onClose={() => setJsonOpen(false)}>
					<textarea className="json-textarea" aria-label={tUI("Advanced JSON")} spellCheck={false} value={jsonContent} onChange={(e) => setJsonContent(e.target.value)} />
					{jsonError && (
						<p className="error-line" role="alert">
							{jsonError}
						</p>
					)}
					<div className="dialog-actions">
						<button className="button secondary" onClick={() => setJsonOpen(false)}>
							{tUI("Cancel")}
						</button>
						<button
							className="button primary"
							onClick={() => {
								const error = studio.applyJson(jsonContent);
								if (error) setJsonError(error);
								else setJsonOpen(false);
							}}>
							{tUI("Apply")}
						</button>
					</div>
				</Dialog>
			)}
			{previewOpen && selected && (
				<Dialog title={tUI("Quest preview")} onClose={() => setPreviewOpen(false)}>
					<div className="quest-preview">
						<ItemIcon id={selected.icon} size={52} />
						<h2>{text(selected.title, language)}</h2>
						<div className="description-preview" dangerouslySetInnerHTML={{ __html: descriptionHtml(descriptionParts(text(selected.description, language))) }} />
						{selected.kind === "information" ? (
							<p>{text(selected.narrative, language)}</p>
						) : (
							<ul>
								{selected.objectives.map((o) => (
									<li key={o.id}>
										<span className="preview-checkbox" />
										{objectiveLabel(o, language)}
									</li>
								))}
							</ul>
						)}
						<p className="preview-note">{tUI("Content preview · Player progress is not simulated")}</p>
					</div>
				</Dialog>
			)}
			{studio.notice && (
				<div className="toast show" role="status">
					{studio.notice}
				</div>
			)}
		</div>
	);
}

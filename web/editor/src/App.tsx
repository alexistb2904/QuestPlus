import JSON5 from "json5";
import {
	BookOpen,
	Check,
	ChevronRight,
	Compass,
	Download,
	Eye,
	EyeOff,
	FilePlus2,
	FileText,
	Focus,
	FolderInput,
	FolderOpen,
	FolderPlus,
	Gift,
	GitBranch,
	Globe,
	Grid2X2,
	Hand,
	Languages,
	LayoutTemplate,
	ListChecks,
	Magnet,
	Minus,
	MousePointer2,
	Package,
	PanelLeftOpen,
	Pickaxe,
	Plus,
	Redo2,
	Search,
	SlidersHorizontal,
	Square,
	StickyNote,
	TriangleAlert,
	Undo2,
	Upload,
	Workflow,
	Wrench,
	X,
	ZoomIn,
	MessageSquareHeart,
	Hammer,
} from "lucide-react";
import React, { useEffect, useMemo, useRef, useState } from "react";
import { AiPromptDialog } from "./components/AiPromptDialog";
import { BulkEditor } from "./components/BulkEditor";
import { Dialog } from "./components/Dialog";
import { FeedbackDialog } from "./components/FeedbackDialog";
import { FtbImportReview } from "./components/FtbImportReview";
import { HubPanel } from "./components/HubPanel";
import { IconPicker } from "./components/IconPicker";
import { Inspector } from "./components/Inspector";
import { PaneSeparator } from "./components/PaneSeparator";
import { QuestDetails } from "./components/QuestDetails";
import { QuestPreview } from "./components/QuestPreview";
import { RegistryBrowser } from "./components/RegistryBrowser";
import { QuestGiverLibrary } from "./components/QuestGiverLibrary";
import { RewardTableLibrary } from "./components/RewardTableLibrary";
import { Sidebar } from "./components/Sidebar";
import { TemplateLibrary } from "./components/TemplateLibrary";
import { MenuItem, ToolbarMenu } from "./components/ToolbarMenu";
import { TreeCanvas } from "./components/TreeCanvas";
import { WelcomeDialog } from "./components/WelcomeDialog";
import { clampZoom, ZOOM_STEP } from "./core/editor-geometry.js";
import { clampPaneSize } from "./core/pane-resize.js";
import { MINECRAFT_VERSIONS, objectiveLabel, orderedCategories, text, validate } from "./core/project.js";
import { renameRewardTable } from "./core/reward-tables.js";
import { owner, useStudio } from "./hooks/useStudio";
import { localesOf, supportedLocales } from "./types";
import { setUiLocale, storedUiLocale, tUI } from "./ui/ui-i18n.js";
const logo = import.meta.env.BASE_URL + "questplus-logo.png";
const docsUrl = import.meta.env.VITE_QUESTPLUS_DOCS_URL || "https://quest.alexistb.com/docs/";

const REGISTRY_EVENTS: Record<string, string[]> = {
	items: ["item_obtained", "item_craft", "item_smelt", "item_use", "item_consume", "fish_item", "trade", "inventory_count", "item_submit", "item_turn_in"],
	blocks: ["block_break", "block_place", "block_interact"],
	fluids: ["fluid_transfer"],
	entities: ["entity_kill", "fish_entity", "tame_animal", "breed_animal", "npc_interact"],
	biomes: ["biome_visit"],
	structures: ["structure_visit"],
	dimensions: ["dimension_enter"],
	statistics: ["custom_stat"],
	advancements: ["advancement_complete"],
};

export default function App() {
	const studio = useStudio(),
		{ state, setState } = studio;
	const { project, categoryId, selection, camera, language } = state;
	const [uiLocale, setInterfaceLocale] = useState(() => {
		const l = storedUiLocale(globalThis.localStorage, ["en"]);
		setUiLocale(l);
		return l;
	});
	// A Hub link (?session= / ?pack=) opens the Hub panel instead, so skip the startup choice.
	const [startupOpen, setStartupOpen] = useState(() => {
		const params = new URLSearchParams(window.location.search);
		return studio.hasPreviousProject && !params.get("session") && !params.get("pack");
	});
	const [translationRequest, setTranslationRequest] = useState(0);
	const [picker, setPicker] = useState<string | null>(null),
		[jsonOpen, setJsonOpen] = useState(false),
		[jsonContent, setJsonContent] = useState(""),
		[jsonError, setJsonError] = useState(""),
		[issuesOpen, setIssuesOpen] = useState(false),
		[previewOpen, setPreviewOpen] = useState(false);
	const [registryOpen, setRegistryOpen] = useState(false);
	const [importOpen, setImportOpen] = useState(false);
	const [feedbackOpen, setFeedbackOpen] = useState(false);
	const [aiOpen, setAiOpen] = useState(false);
	const [bulkOpen, setBulkOpen] = useState(false);
	const [templatesOpen, setTemplatesOpen] = useState(false);
	const [tablesOpen, setTablesOpen] = useState(false),
		[tableFocus, setTableFocus] = useState(""),
		/** The quest-giver library is open when set. The value is the NPC to show first. */
		[giverFocus, setGiverFocus] = useState<string | null>(null),
		[issueAlert, setIssueAlert] = useState<{ where: string; message: string } | null>(null),
		alertedIssues = useRef<Set<string> | null>(null);
	const [showQuestTitles, setShowQuestTitles] = useState(() => localStorage.getItem("questplus.quest-titles") === "true");
	useEffect(() => {
		localStorage.setItem("questplus.quest-titles", String(showQuestTitles));
	}, [showQuestTitles]);
	const [search, setSearch] = useState(""),
		[grid, setGrid] = useState(true),
		[snap, setSnap] = useState(true),
		[gridSize, setGridSize] = useState(16),
		[tool, setTool] = useState<"select" | "pan" | "background">("select"),
		[libraryOpen, setLibraryOpen] = useState(() => window.matchMedia("(min-width:1200px)").matches),
		[mobilePanel, setMobilePanel] = useState<"canvas" | "properties">("canvas");
	const workspaceRef = useRef<HTMLDivElement>(null);
	const [paneSizes, setPaneSizes] = useState<{ library?: number; properties?: number; details?: number }>(() => {
		try {
			const saved = JSON.parse(localStorage.getItem("questplus.pane-sizes") || "{}");
			return Object.fromEntries(
				Object.entries(saved).filter(([key, value]) => ["library", "properties", "details"].includes(key) && typeof value === "number" && Number.isFinite(value))
			);
		} catch {
			return {};
		}
	});
	useEffect(() => {
		try {
			localStorage.setItem("questplus.pane-sizes", JSON.stringify(paneSizes));
		} catch {}
	}, [paneSizes]);
	const paneValue = (kind: "library" | "properties" | "details") => {
		const selector = kind === "library" ? ".sidebar" : kind === "properties" ? ".properties-pane" : ".quest-details";
		const rect = workspaceRef.current?.querySelector(selector)?.getBoundingClientRect();
		return (kind === "details" ? rect?.height : rect?.width) || paneSizes[kind] || (kind === "library" ? 270 : kind === "properties" ? 355 : 250);
	};
	const resizePane = (kind: "library" | "properties" | "details", value: number) => {
		const workspace = workspaceRef.current;
		if (!workspace) return;
		const other = kind === "library" ? paneValue("properties") : libraryOpen ? paneValue("library") : 0;
		const height = workspace.querySelector(".main")?.clientHeight || workspace.clientHeight;
		setPaneSizes((old) => ({ ...old, [kind]: clampPaneSize(kind, value, workspace.clientWidth, other, height) }));
	};
	const separator = (kind: "library" | "properties" | "details") => <PaneSeparator kind={kind} value={() => paneValue(kind)} onResize={(value) => resizePane(kind, value)} />;
	const paneStyle = {
		"--library-width": paneSizes.library === undefined ? undefined : paneSizes.library + "px",
		"--properties-width": paneSizes.properties === undefined ? undefined : paneSizes.properties + "px",
		"--details-height": paneSizes.details === undefined ? undefined : paneSizes.details + "px",
	} as React.CSSProperties;
	const files = useRef<HTMLInputElement>(null),
		folder = useRef<HTMLInputElement>(null),
		searchRef = useRef<HTMLInputElement>(null);
	const locales = useMemo(() => localesOf(project), [project]),
		issues = useMemo(() => validate(project), [project]);
	// Announce a new validation problem once the author pauses, so a half-typed ID does not flash an alert on every key.
	useEffect(() => {
		const key = (x: { where: string; message: string }) => x.where + "\u0000" + x.message,
			keys = new Set(issues.map(key));
		if (!alertedIssues.current) {
			alertedIssues.current = keys;
			return;
		}
		const alerted = alertedIssues.current;
		for (const k of alerted) if (!keys.has(k)) alerted.delete(k);
		setIssueAlert((current) => (current && keys.has(key(current)) ? current : null));
		const fresh = issues.find((x: { where: string; message: string }) => !alerted.has(key(x)));
		if (!fresh) return;
		const timer = window.setTimeout(() => {
			alerted.add(key(fresh));
			setIssueAlert(fresh);
		}, 700);
		return () => clearTimeout(timer);
	}, [issues]);
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
	const changeCamera = (next: any) => setState((s) => ({ ...s, camera: { ...next, zoom: clampZoom(next.zoom) } }));
	const editJson = () => {
		setJsonContent(JSON5.stringify(owner(project, selection, categoryId), null, 2));
		setJsonError("");
		setJsonOpen(true);
	};
	const doAction = (type: string, index?: number) => {
		if (type === "advanced-json") return editJson();
		if (type.startsWith("enable-crate:")) {
			const name = type.slice("enable-crate:".length);
			return studio.mutate((s) => {
				const table = s.project.rewardTables?.find((row) => row.name === name);
				if (table && table.reward.type !== "questplus:choice") table.crate = true;
			});
		}
		if (type.startsWith("open-reward-tables")) {
			setTableFocus(type.slice("open-reward-tables:".length));
			return setTablesOpen(true);
		}
		if (type.startsWith("open-quest-givers")) return setGiverFocus(type.slice("open-quest-givers:".length));
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
	const objectiveFor = (kind: string) => {
		const events = REGISTRY_EVENTS[kind] || [];
		return selected?.objectives?.findIndex((o) => events.includes(o.event)) ?? -1;
	};
	const inspectorProps = {
		project,
		categoryId,
		selection,
		language,
		onUpdate: studio.setField,
		onAction: doAction,
		onChooseIcon: setPicker,
		onCondition: studio.changeCondition,
		onAddLocale: studio.addLocale,
		onConfig: (change: (config: any) => void, field = "config:npcs") =>
			studio.mutate((state) => {
				state.project.config ||= {};
				change(state.project.config);
			}, field),
		onOpenTranslations: () => {
			setTranslationRequest((value) => value + 1);
			setMobilePanel("canvas");
		},
		translationRequest,
		issues,
	};
	return (
		<div className="app">
			<header className="topbar stone">
				<div className="editor-plaque">
					<img className="studio-logo" src={logo} alt="" />
					<span>
						<div className="brand-name">
							QuestPlus <span>Studio</span>
						</div>
					</span>
				</div>
				<div className="landscape-banner" role="img" aria-label={tUI("Minecraft landscape")} />
				<div className="top-actions">
					<a className="studio-docs-link" href={docsUrl} target="_blank" rel="noopener noreferrer" title={tUI("Documentation")} aria-label={tUI("Documentation")}>
						<BookOpen size={16} aria-hidden="true" />
						<span>{tUI("Documentation")}</span>
					</a>
					<button type="button" className="studio-docs-link" onClick={() => setFeedbackOpen(true)} title={tUI("Send feedback")} aria-label={tUI("Send feedback")}>
						<MessageSquareHeart size={16} aria-hidden="true" />
						<span>{tUI("Feedback")}</span>
					</button>
					<button type="button" className="studio-docs-link" onClick={() => setAiOpen(true)} title={tUI("Create your own quest pack")} aria-label={tUI("Create your own quest pack")}>
						<Hammer size={16} aria-hidden="true" />
						<span>{tUI("Create your own")}</span>
					</button>
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
				</div>
			</header>
			<div className="command-bar stone" role="toolbar" aria-label={tUI("Editor actions")}>
				<ToolbarMenu label={tUI("File")} icon={<FileText size={16} aria-hidden="true" />}>
					<MenuItem icon={<FilePlus2 size={17} />} label={tUI("New project")} hint={tUI("Start an empty quest pack")} onSelect={() => studio.reset(false)} />
					<MenuItem icon={<Compass size={17} />} label={tUI("Open sample project")} hint={tUI("Explore a finished pack to learn the editor")} onSelect={() => studio.reset(true)} />
					<div className="toolbar-menu-separator" role="separator" />
					<MenuItem icon={<Upload size={17} />} label={tUI("Import pack…")} hint={tUI("QuestPlus or FTB Quests, ZIP or JSON")} onSelect={() => setImportOpen(true)} />
					<MenuItem icon={<FolderInput size={17} />} label={tUI("Import folder…")} hint={tUI("An FTB Quests or QuestPlus pack folder")} onSelect={() => folder.current?.click()} />
					<div className="toolbar-menu-separator" role="separator" />
					<MenuItem
						icon={<Download size={17} />}
						label={tUI("Export ZIP")}
						hint={tUI("Download the pack for your server")}
						onSelect={() => {
							if (!studio.exportZip()) setIssuesOpen(true);
						}}
					/>
				</ToolbarMenu>
				<HubPanel
					project={project}
					onConfig={inspectorProps.onConfig}
					onSaveLocal={() => {
						const saved = studio.save();
						if (saved) studio.toast(tUI("Saved locally"));
						return saved;
					}}
					onLoad={(next) => {
						setState((s) => {
							const first = orderedCategories(next.categories)[0]?.id || null;
							return {
								...s,
								project: next,
								categoryId: first,
								selection: first ? { type: "category", id: first } : null,
								camera: { x: 0, y: 0, zoom: 1 },
								language: "en_us",
							};
						});
					}}
				/>
				<span className="divider" />
				<div className="history-group" role="group" aria-label={tUI("History")}>
					<button className="icon-button" aria-label={tUI("Undo")} title={tUI("Undo") + " (Ctrl+Z)"} onClick={studio.undo} disabled={!studio.history.current.length}>
						<Undo2 size={17} />
					</button>
					<button className="icon-button" aria-label={tUI("Redo")} title={tUI("Redo") + " (Ctrl+Shift+Z)"} onClick={studio.redo} disabled={!studio.future.current.length}>
						<Redo2 size={17} />
					</button>
				</div>
				<div className="global-search">
					<Search size={17} />
					<input ref={searchRef} aria-label={tUI("Search quests…")} placeholder={tUI("Search quests…")} value={search} onChange={(e) => setSearch(e.target.value)} />
					<kbd>Ctrl K</kbd>
				</div>
				<ToolbarMenu label={tUI("View")} icon={<ZoomIn size={16} aria-hidden="true" />} summary={Math.round(camera.zoom * 100) + "%"}>
					<div className="toolbar-menu-zoom" role="group" aria-label={tUI("Zoom")}>
						<button
							type="button"
							role="menuitem"
							className="icon-button"
							aria-label={tUI("Zoom out")}
							title={tUI("Zoom out")}
							onClick={() => changeCamera({ ...camera, zoom: clampZoom(camera.zoom / ZOOM_STEP) })}>
							<Minus size={16} />
						</button>
						<button
							type="button"
							role="menuitem"
							className="zoom-reset"
							aria-label={tUI("Reset zoom")}
							title={tUI("Reset zoom")}
							onClick={() => changeCamera({ ...camera, zoom: 1 })}>
							{Math.round(camera.zoom * 100)}%
						</button>
						<button
							type="button"
							role="menuitem"
							className="icon-button"
							aria-label={tUI("Zoom in")}
							title={tUI("Zoom in")}
							onClick={() => changeCamera({ ...camera, zoom: clampZoom(camera.zoom * ZOOM_STEP) })}>
							<Plus size={16} />
						</button>
					</div>
					<div className="toolbar-menu-separator" role="separator" />
					<MenuItem role="menuitemcheckbox" checked={grid} icon={<Grid2X2 size={17} />} label={tUI("Show grid")} onSelect={() => setGrid((v) => !v)} />
					<div className="toolbar-menu-sizes" role="group" aria-label={tUI("Grid size")}>
						<span>{tUI("Grid size")}</span>
						{[8, 16, 32].map((n) => (
							<button key={n} type="button" role="menuitemradio" aria-checked={gridSize === n} onClick={() => setGridSize(n)}>
								{n} px
							</button>
						))}
					</div>
					<MenuItem
						role="menuitemcheckbox"
						checked={snap}
						icon={<Magnet size={17} />}
						label={tUI("Snap to grid")}
						hint={tUI("Dragged quests land on grid points")}
						onSelect={() => setSnap((v) => !v)}
					/>
				</ToolbarMenu>
				<ToolbarMenu label={tUI("Tools")} icon={<Wrench size={16} aria-hidden="true" />}>
					<MenuItem
						icon={<Eye size={17} />}
						label={tUI("Quest preview")}
						hint={tUI(selected ? "See the selected quest as players read it" : "Select a quest first")}
						disabled={!selected}
						onSelect={() => setPreviewOpen(true)}
					/>
					<div className="toolbar-menu-separator" role="separator" />
					<MenuItem icon={<LayoutTemplate size={17} />} label={tUI("Templates")} hint={tUI("Reuse objectives and rewards")} onSelect={() => setTemplatesOpen(true)} />
					<MenuItem
						icon={<Gift size={17} />}
						label={tUI("Reward tables")}
						hint={tUI("Shared loot and crates")}
						onSelect={() => {
							setTableFocus("");
							setTablesOpen(true);
						}}
					/>
					<MenuItem icon={<ListChecks size={17} />} label={tUI("Bulk edit")} hint={tUI("Change many quests at once")} onSelect={() => setBulkOpen(true)} />
					<div className="toolbar-menu-separator" role="separator" />
					<MenuItem icon={<Package size={17} />} label={tUI("Mod registry")} hint={tUI("Look up item, block and entity IDs")} onSelect={() => setRegistryOpen(true)} />
				</ToolbarMenu>
				<span className="divider" />
				<button className="button secondary add-category-button" onClick={studio.createCategory}>
					<FolderPlus size={17} />
					<span>{tUI("Add Category")}</span>
				</button>
				<button className="button primary" disabled={!cat} onClick={() => studio.createQuest()}>
					<Plus size={19} />
					<span>{tUI("Add Quest")}</span>
				</button>
			</div>
			<input
				ref={files}
				hidden
				type="file"
				multiple
				accept=".json,.json5,.snbt,.txt,.zip,.png,.jpg,.jpeg,.gif"
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
			<div
				ref={workspaceRef}
				style={paneStyle}
				data-panel={mobilePanel}
				className={"workspace" + (!libraryOpen ? " library-collapsed" : "") + (paneSizes.details === undefined ? "" : " panes-custom-height")}>
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
						search={search}
						onSearch={setSearch}
						onCollapse={() => setLibraryOpen(false)}
					/>
				)}
				{libraryOpen && separator("library")}
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
								<button
									className={"icon-button" + (tool === "background" ? " active" : "")}
									disabled={!cat}
									aria-pressed={tool === "background"}
									aria-label={tUI("Draw background")}
									title={tUI("Draw background")}
									onClick={() => setTool("background")}>
									<Square size={18} />
								</button>
								<button
									type="button"
									className={"icon-button" + (showQuestTitles ? " active" : "")}
									aria-pressed={showQuestTitles}
									aria-label={tUI("Show quest titles")}
									title={tUI("Show quest titles")}
									onClick={() => setShowQuestTitles((v) => !v)}>
									{showQuestTitles ? <Eye size={18} /> : <EyeOff size={18} />}
								</button>
								<button
									type="button"
									className={"icon-button" + (snap ? " active" : "")}
									aria-pressed={snap}
									aria-label={tUI("Snap to grid")}
									title={tUI("Snap to grid")}
									onClick={() => setSnap((v) => !v)}>
									<Magnet size={18} />
								</button>
								<button className="icon-button" disabled={!cat} onClick={studio.autoLayout} title={tUI("Auto-layout")} aria-label={tUI("Auto-layout")}>
									<Workflow size={18} />
								</button>
								<button className="icon-button" onClick={() => changeCamera(studio.fit())} title={tUI("Center tree")} aria-label={tUI("Center tree")}>
									<Focus size={18} />
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
							onResize={studio.resizeNode}
							onRoute={studio.setLinkRoute}
							onBackgroundCreate={(rect) => {
								studio.createBackground(rect);
								setTool("select");
							}}
							onCreateAt={(kind, at) => (kind === "quest" ? studio.createQuest(at) : studio.createAnnotation(at))}
							onCreateTemplate={(templateId, at) => studio.createQuest(at, templateId)}
							showQuestTitles={showQuestTitles}
							showGrid={grid}
							gridSize={gridSize}
							snap={snap}
							tool={tool}
						/>
						<div className="canvas-bottom">
							<span>
								<MousePointer2 size={12} />
								{tUI("Drag to move · Wheel to zoom · Shift+wheel to pan")}
							</span>
							<button className="subtle" disabled={!cat} onClick={() => studio.createAnnotation()}>
								<StickyNote size={13} />
								{tUI("Add annotation")}
							</button>
							<span className="coordinates">
								x: {Math.round(camera.x)} &nbsp; y: {Math.round(camera.y)}
							</span>
						</div>
					</div>
					{separator("details")}
					<QuestDetails {...inspectorProps} />
					<div className={"issues-bar stone" + (issues.length ? " error" : "")} role="status">
						<span className="issues-icon">{issues.length ? <TriangleAlert size={16} /> : <Check size={16} />}</span>
						<button className="subtle validation-button" onClick={() => setIssuesOpen((v) => !v)} aria-expanded={issuesOpen}>
							{issues.length
								? issues.length + " " + tUI(issues.length === 1 ? "error · click to review" : "errors · click to review")
								: tUI("Validation: No issues found")}
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
										<strong>{x.where}</strong>: {tUI(x.message)}
									</button>
								))
							) : (
								<div>{tUI("No errors")}</div>
							)}
						</div>
					)}
				</main>
				{separator("properties")}
				<div className="properties-pane" id="properties-panel">
					<Inspector {...inspectorProps} />
				</div>
			</div>
			{/* Pack-wide config.json settings live in Server & sharing > Settings, not here. */}
			<footer className="project-bar stone">
				<label className="pack-field">
					<span className="pack-field-label">
						<Languages size={14} aria-hidden="true" />
						<span className="pack-field-text">{tUI("Preview language")}</span>
					</span>
					<select id="preview-language" value={language} onChange={(e) => studio.setLanguage(e.target.value)}>
						{locales.map((id) => (
							<option key={id} value={id}>
								{supportedLocales[id] || id} · {id}
							</option>
						))}
					</select>
				</label>
				<label className="pack-field pack-field-version">
					<span className="pack-field-label">
						<Pickaxe size={14} aria-hidden="true" />
						<span className="pack-field-text">Minecraft</span>
					</span>
					<select
						aria-label={tUI("Target Minecraft version")}
						value={project.targetMinecraftVersion || MINECRAFT_VERSIONS[0]}
						onChange={(e) =>
							studio.mutate((s) => {
								s.project.targetMinecraftVersion = e.target.value;
							}, "version")
						}>
						{/* A draft saved before this list existed may hold a free-typed version: show it, but not as a choice. */}
						{!MINECRAFT_VERSIONS.includes(project.targetMinecraftVersion || MINECRAFT_VERSIONS[0]) && (
							<option value={project.targetMinecraftVersion} disabled>
								{project.targetMinecraftVersion}
							</option>
						)}
						{MINECRAFT_VERSIONS.map((id) => (
							<option key={id} value={id}>
								{id}
							</option>
						))}
					</select>
				</label>
				<button
					className="button primary export-button"
					onClick={() => {
						if (!studio.exportZip()) setIssuesOpen(true);
					}}>
					<Download size={16} aria-hidden="true" />
					{tUI("Export ZIP")}
				</button>
			</footer>
			{startupOpen && (
				<WelcomeDialog
					project={project}
					language={language}
					docsUrl={docsUrl}
					onResume={() => setStartupOpen(false)}
					onStartFresh={(withExamples) => {
						studio.startFresh(withExamples);
						setStartupOpen(false);
					}}
					onImport={() => {
						setStartupOpen(false);
						files.current?.click();
					}}
					onBackup={() => void studio.exportZip()}
				/>
			)}
			{tablesOpen && (
				<RewardTableLibrary
					project={studio.state.project}
					language={language}
					initial={tableFocus}
					onRename={(from, to) =>
						studio.mutate((s) => {
							renameRewardTable(s.project, from, to);
						})
					}
					onChange={(tables) =>
						studio.mutate((s) => {
							s.project.rewardTables = tables;
						})
					}
					onClose={() => setTablesOpen(false)}
				/>
			)}
			{giverFocus !== null && (
				<QuestGiverLibrary
					project={studio.state.project}
					language={language}
					initial={giverFocus}
					quest={selected?.id}
					onConfig={inspectorProps.onConfig}
					onClose={() => setGiverFocus(null)}
				/>
			)}
			{templatesOpen && (
				<TemplateLibrary
					projectTemplates={studio.state.project.questTemplates}
					onSaveProject={studio.setProjectTemplates}
					selectedQuest={selected}
					language={language}
					canCreate={!!cat}
					onCreate={(templateId) => {
						setTemplatesOpen(false);
						studio.createQuest(undefined, templateId);
					}}
					onClose={() => setTemplatesOpen(false)}
				/>
			)}
			{bulkOpen && (
				<BulkEditor
					project={project}
					language={language}
					onClose={() => setBulkOpen(false)}
					onApply={(result, count) => {
						studio.mutate((draft) => {
							draft.project = result;
						}, "bulk-edit");
						studio.toast(tUI("Quests updated") + ": " + count);
					}}
				/>
			)}
			{importOpen && (
				<Dialog title={tUI("Import")} onClose={() => setImportOpen(false)}>
					<p>{tUI("Choose the format of your pack.")}</p>
					<div className="import-choices">
						<button
							className="button secondary"
							onClick={() => {
								setImportOpen(false);
								files.current?.click();
							}}>
							QuestPlus · ZIP / JSON
						</button>
						<button
							className="button secondary"
							onClick={() => {
								setImportOpen(false);
								folder.current?.click();
							}}>
							FTB Quests · dossier
						</button>
						<button
							className="button secondary"
							onClick={() => {
								setImportOpen(false);
								files.current?.click();
							}}>
							FTB Quests · ZIP
						</button>
					</div>
				</Dialog>
			)}
			{registryOpen && (
				<RegistryBrowser
					onClose={() => setRegistryOpen(false)}
					targetFor={(kind) => {
						const index = objectiveFor(kind);
						return index < 0 ? null : objectiveLabel(selected!.objectives[index], language);
					}}
					onUse={(kind, id) => {
						const index = objectiveFor(kind);
						if (index < 0) return;
						studio.setField("objectives." + index + ".target", id);
						studio.toast(tUI("Objective target set") + ": " + id);
						setRegistryOpen(false);
					}}
				/>
			)}
			{picker && (
				<IconPicker
					onPick={(id) => {
						studio.setField(picker, id);
						setPicker(null);
					}}
					onClose={() => setPicker(null)}
				/>
			)}
			{feedbackOpen && <FeedbackDialog onClose={() => setFeedbackOpen(false)} />}
			{aiOpen && <AiPromptDialog version={project.targetMinecraftVersion} onClose={() => setAiOpen(false)} />}
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
				<QuestPreview project={project} quest={selected} language={language} locales={locales} onLanguage={studio.setLanguage} onClose={() => setPreviewOpen(false)} />
			)}
			{studio.pendingImport && <FtbImportReview pendingImport={studio.pendingImport} onConfirm={studio.confirmFtbImport} onCancel={studio.cancelFtbImport} />}
			{issueAlert && (
				<div className="issue-toast" role="alert">
					<TriangleAlert size={18} aria-hidden="true" />
					<div>
						<strong>{tUI("Validation error")}</strong>
						<p>
							<b>{tUI(issueAlert.where)}</b>: {tUI(issueAlert.message)}
						</p>
					</div>
					<button
						className="button secondary"
						onClick={() => {
							setIssuesOpen(true);
							if (issueAlert.where === "Reward tables") {
								setTableFocus("");
								setTablesOpen(true);
							} else if (project.quests.some((q) => q.id === issueAlert.where)) goQuest(issueAlert.where);
							setIssueAlert(null);
						}}>
						{tUI("Show errors")}
					</button>
					<button className="icon-button" aria-label={tUI("Dismiss")} onClick={() => setIssueAlert(null)}>
						<X size={16} />
					</button>
				</div>
			)}
			{studio.notice && (
				<div className="toast show" role="status">
					{studio.notice}
				</div>
			)}
		</div>
	);
}

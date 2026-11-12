import { ChevronRight, FolderOpen, FolderPlus, PanelLeftClose, Search, X } from "lucide-react";
import { useState } from "react";
import { orderedCategories, text } from "../core/project.js";
import type { Project, Quest, Selection } from "../types";
import { tUI } from "../ui/ui-i18n.js";
import { ItemIcon } from "./IconPicker";
type Props = {
	project: Project;
	categoryId: string | null;
	quests: Quest[];
	selection: Selection | null;
	language: string;
	onCategory: (id: string) => void;
	onQuest: (id: string) => void;
	onNewCategory: () => void;
	search: string;
	onSearch: (s: string) => void;
	onCollapse: () => void;
};
export function Sidebar({ project, categoryId, selection, language, onCategory, onQuest, onNewCategory, search, onSearch, onCollapse }: Props) {
	const [collapsed, setCollapsed] = useState<Set<string>>(new Set());
	const grouped = [...new Set(orderedCategories(project.categories).map((c) => c.group || ""))].flatMap((group) =>
		orderedCategories(project.categories).filter((c) => (c.group || "") === group)
	);
	const query = search.trim().toLocaleLowerCase();
	return (
		<aside className="sidebar wood" aria-label={tUI("Library")}>
			<div className="sidebar-header grass-trim">
				<FolderOpen size={20} aria-hidden="true" />
				<h2>{tUI("Quest Library")}</h2>
				<div className="sidebar-header-actions">
					<button className="icon-button" aria-label={tUI("New Category")} title={tUI("New Category")} onClick={onNewCategory}>
						<FolderPlus size={17} />
					</button>
					<button className="icon-button" aria-label={tUI("Collapse library")} title={tUI("Collapse library")} onClick={onCollapse}>
						<PanelLeftClose size={17} />
					</button>
				</div>
			</div>
			<div className="search-box">
				<Search size={15} aria-hidden="true" />
				<input
					type="search"
					value={search}
					aria-label={tUI("Search categories / quests…")}
					onChange={(e) => onSearch(e.target.value)}
					placeholder={tUI("Search categories / quests…")}
				/>
				{search && (
					<button type="button" className="search-clear" aria-label={tUI("Clear search")} title={tUI("Clear search")} onClick={() => onSearch("")}>
						<X size={14} />
					</button>
				)}
			</div>
			<nav className="library-tree">
				{!project.categories.length && !query && (
					<div className="library-start">
						<p>{tUI("No categories yet")}</p>
						<button className="button primary" onClick={onNewCategory}>
							<FolderPlus size={16} />
							{tUI("New Category")}
						</button>
					</div>
				)}
				{grouped.map((c, index) => {
					const all = project.quests.filter((q) => q.category === c.id),
						categoryMatches = (text(c.title, language) + " " + c.id + " " + (c.group || "")).toLocaleLowerCase().includes(query);
					const rows = query && !categoryMatches ? all.filter((q) => (text(q.title, language) + " " + q.id).toLocaleLowerCase().includes(query)) : all;
					if (query && !categoryMatches && !rows.length) return null;
					const open = !!query || !collapsed.has(c.id);
					return (
						<div className={"library-group" + (categoryId === c.id ? " current" : "")} key={c.id}>
							{c.group && (index === 0 || grouped[index - 1].group !== c.group) && <h3 className="chapter-group-title">{c.group}</h3>}
							<div className="category-row">
								<button
									className="category-expander"
									aria-label={tUI(open ? "Collapse category" : "Expand category") + " " + text(c.title, language)}
									aria-expanded={open}
									onClick={() =>
										setCollapsed((old) => {
											const next = new Set(old);
											if (next.has(c.id)) next.delete(c.id);
											else next.add(c.id);
											return next;
										})
									}>
									<ChevronRight size={15} aria-hidden="true" />
								</button>
								<button className={"category-item" + (selection?.type === "category" && selection.id === c.id ? " active" : "")} onClick={() => onCategory(c.id)}>
									<ItemIcon id={c.icon} size={24} />
									<strong className="cat-name">{text(c.title, language)}</strong>
									<span className="category-count">{all.length}</span>
								</button>
							</div>
							{open && (
								<div className="quest-list">
									{rows.map((q) => (
										<button
											className={"quest-item" + (selection?.type === "quest" && selection.id === q.id ? " active" : "")}
											aria-label={text(q.title, language)}
											aria-current={selection?.type === "quest" && selection.id === q.id ? "true" : undefined}
											onClick={() => onQuest(q.id)}
											key={q.id}>
											<ItemIcon id={q.icon} size={22} />
											<span className="quest-label">
												<strong>{text(q.title, language)}</strong>
											</span>
										</button>
									))}
									{!all.length && <p className="library-empty">{tUI("No quests yet")}</p>}
								</div>
							)}
						</div>
					);
				})}
				{query &&
					!project.categories.some((c) => (text(c.title, language) + " " + c.id + " " + (c.group || "")).toLocaleLowerCase().includes(query)) &&
					!project.quests.some((q) => (text(q.title, language) + " " + q.id).toLocaleLowerCase().includes(query)) && <p className="library-empty">{tUI("No results")}</p>}
			</nav>
		</aside>
	);
}

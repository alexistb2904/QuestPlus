import { defineConfig, type HeadConfig } from "vitepress";

// Canonical public origin. GitHub Pages mirrors this site, so every page points search engines here.
const SITE = "https://quest.alexistb.com/";
const DOCS = SITE + "docs/";
const NAME = "Quest Plus";
const DESCRIPTION =
	"Quest Plus is a free quest mod for Minecraft Java 26.1, 26.2 and 26.3 on Fabric and NeoForge: an animated quest tablet, quest blocks, quest givers, loot crates, teams and a visual quest editor with FTB Quests import.";
const KEYWORDS =
	"Quest Plus, QuestPlus, Minecraft quest mod, quest book mod, FTB Quests alternative, FTB Quests 26.1, quest mod 26.1, quest mod 26.2, quest mod 26.3, Fabric quest mod, NeoForge quest mod, modpack quests, quest editor, Quest Studio";
const IMAGE = DOCS + "store/01-banner.jpg";
const LINKS = {
	modrinth: "https://modrinth.com/mod/quest-plus",
	curseforge: "https://www.curseforge.com/minecraft/mc-mods/quest-plus",
	github: "https://github.com/alexistb2904/QuestPlus",
};
const ids = { mod: DOCS + "#mod", studio: SITE + "#studio", site: DOCS + "#website", author: DOCS + "#author" };

// Site wide entities, published once on the home page and referenced by @id everywhere else.
const graph = [
	{
		"@type": "SoftwareApplication",
		"@id": ids.mod,
		name: NAME,
		alternateName: ["QuestPlus", "Quest Plus mod"],
		description: DESCRIPTION,
		applicationCategory: "GameApplication",
		applicationSubCategory: "Minecraft mod",
		operatingSystem: "Windows, macOS, Linux (Minecraft Java Edition 26.1 to 26.3, Fabric or NeoForge, Java 25)",
		softwareRequirements: "Minecraft Java Edition 26.1+, Fabric Loader with Fabric API or NeoForge",
		url: DOCS,
		image: IMAGE,
		screenshot: [DOCS + "store/03-tablet.jpg", DOCS + "store/04-quest-blocks.jpg", DOCS + "store/06-quest-givers.jpg", DOCS + "store/10-quest-studio.jpg"],
		downloadUrl: [LINKS.modrinth, LINKS.curseforge],
		codeRepository: LINKS.github,
		isAccessibleForFree: true,
		offers: { "@type": "Offer", price: "0", priceCurrency: "USD" },
		author: { "@id": ids.author },
		sameAs: Object.values(LINKS),
		keywords: KEYWORDS,
		featureList: [
			"Animated quest tablet with quest trees and HUD tracking",
			"Quest Board, Submission Station and Quest Relay blocks",
			"Quest givers (NPCs) and animated loot crates",
			"Team progression and leaderboards",
			"Visual browser editor (Quest Studio) with offline ZIP export",
			"FTB Quests definition import and progress migration",
			"Java API for Fabric and NeoForge integrations",
		],
	},
	{
		"@type": "WebApplication",
		"@id": ids.studio,
		name: "Quest Studio",
		description: "Free browser editor for Minecraft quest packs: quest trees, objectives, rewards, quest givers, loot crates, translations and FTB Quests import.",
		url: SITE,
		applicationCategory: "DeveloperApplication",
		operatingSystem: "Any modern web browser",
		isAccessibleForFree: true,
		offers: { "@type": "Offer", price: "0", priceCurrency: "USD" },
		isPartOf: { "@id": ids.mod },
	},
	{ "@type": "Person", "@id": ids.author, name: "alexistb2904", url: "https://github.com/alexistb2904" },
	{
		"@type": "WebSite",
		"@id": ids.site,
		name: NAME + " documentation",
		url: DOCS,
		inLanguage: "en",
		about: { "@id": ids.mod },
		publisher: { "@id": ids.author },
	},
];

function pageUrl(relativePath: string) {
	return DOCS + relativePath.replace(/(^|\/)index\.md$/, "$1").replace(/\.md$/, ".html");
}

export default defineConfig({
	title: NAME,
	titleTemplate: ":title | Quest Plus",
	description: DESCRIPTION,
	lang: "en-US",
	base: "/QuestPlus/docs/",
	srcDir: "content",
	outDir: "dist",
	cleanUrls: false,
	appearance: "force-dark",
	markdown: { theme: "github-dark" },
	lastUpdated: true,
	sitemap: {
		hostname: DOCS,
		transformItems: (items) => [{ url: SITE, changefreq: "monthly", priority: 0.9 }, ...items.map((i) => ({ ...i, priority: i.url === "" ? 1 : 0.7 }))],
	},
	head: [
		["meta", { name: "theme-color", content: "#18181b" }],
		["meta", { name: "author", content: "alexistb2904" }],
		["meta", { name: "robots", content: "index, follow, max-image-preview:large, max-snippet:-1" }],
		["meta", { property: "og:site_name", content: NAME }],
		["meta", { property: "og:locale", content: "en_US" }],
		["meta", { property: "og:image", content: IMAGE }],
		["meta", { property: "og:image:width", content: "1600" }],
		["meta", { property: "og:image:height", content: "800" }],
		["meta", { property: "og:image:alt", content: "Quest Plus, a Minecraft quest mod with an animated tablet, quest blocks, quest givers and loot crates" }],
		["meta", { name: "twitter:card", content: "summary_large_image" }],
		["meta", { name: "twitter:image", content: IMAGE }],
		["link", { rel: "icon", href: "/QuestPlus/docs/questplus-logo.png" }],
		["link", { rel: "apple-touch-icon", href: "/QuestPlus/docs/questplus-logo.png" }],
		["link", { rel: "alternate", type: "text/plain", title: "LLM summary", href: SITE + "llms.txt" }],
	],
	transformPageData(page, { siteConfig }) {
		if (page.isNotFound) return;
		const url = pageUrl(page.relativePath);
		const home = page.relativePath === "index.md";
		const title = home ? page.title || NAME : `${page.title} | ${NAME}`;
		const description = page.description || siteConfig.site.description;
		const jsonLd = home
			? { "@context": "https://schema.org", "@graph": graph }
			: {
					"@context": "https://schema.org",
					"@graph": [
						{
							"@type": "TechArticle",
							"@id": url + "#page",
							headline: page.title,
							name: title,
							description,
							url,
							inLanguage: "en",
							image: page.frontmatter.image ? DOCS + page.frontmatter.image.replace(/^\//, "") : IMAGE,
							about: { "@id": ids.mod },
							isPartOf: { "@id": ids.site },
							author: { "@id": ids.author },
							...(page.lastUpdated ? { dateModified: new Date(page.lastUpdated).toISOString() } : {}),
						},
						...(page.frontmatter.faq
							? [
									{
										"@type": "FAQPage",
										"@id": url + "#faq",
										mainEntity: page.frontmatter.faq.map((f: { q: string; a: string }) => ({
											"@type": "Question",
											name: f.q,
											acceptedAnswer: { "@type": "Answer", text: f.a },
										})),
									},
								]
							: []),
						{
							"@type": "BreadcrumbList",
							itemListElement: [
								{ "@type": "ListItem", position: 1, name: NAME, item: DOCS },
								{ "@type": "ListItem", position: 2, name: page.title, item: url },
							],
						},
					],
				};
		const head: HeadConfig[] = [
			["link", { rel: "canonical", href: url }],
			["meta", { name: "keywords", content: page.frontmatter.keywords || KEYWORDS }],
			["meta", { property: "og:type", content: home ? "website" : "article" }],
			["meta", { property: "og:url", content: url }],
			["meta", { property: "og:title", content: title }],
			["meta", { property: "og:description", content: description }],
			["meta", { name: "twitter:title", content: title }],
			["meta", { name: "twitter:description", content: description }],
			["script", { type: "application/ld+json" }, JSON.stringify(jsonLd)],
		];
		page.frontmatter.head = [...(page.frontmatter.head || []), ...head];
	},
	locales: {
		root: { label: "English", lang: "en-US" },
	},
	themeConfig: {
		logo: "/questplus-logo.png",
		siteTitle: "QuestPlus Docs",
		search: { provider: "local" },
		nav: [
			{ text: "Get started", link: "/guide/install" },
			{ text: "Quest Studio", link: "/guide/studio" },
			{ text: "From FTB Quests", link: "/guide/ftb-quests-alternative" },
			{ text: "Integrations", link: "/reference/java-api" },
			{ text: "Commands", link: "/reference/commands" },
			{ text: "Open editor", link: SITE, target: "_blank" },
		],
		socialLinks: [{ icon: "github", link: LINKS.github, ariaLabel: "Quest Plus on GitHub" }],
		sidebar: [
			{
				text: "Get started",
				items: [
					{ text: "Install Quest Plus", link: "/guide/install" },
					{ text: "Use the tablet", link: "/guide/tablet" },
					{ text: "Quest blocks", link: "/guide/world-blocks" },
					{ text: "Quest givers and loot crates", link: "/guide/givers-and-crates" },
					{ text: "Teams", link: "/guide/teams" },
					{ text: "Settings and accessibility", link: "/guide/settings" },
					{ text: "Compatibility", link: "/guide/compatibility" },
					{ text: "Troubleshooting", link: "/guide/troubleshooting" },
					{ text: "Quest Studio", link: "/guide/studio" },
					{ text: "Modpack creators", link: "/guide/modpack-creators" },
					{ text: "Coming from FTB Quests", link: "/guide/ftb-quests-alternative" },
				],
			},
			{
				text: "Build a quest pack",
				items: [
					{ text: "Categories and quests", link: "/guide/categories-and-quests" },
					{ text: "Your first quest", link: "/guide/first-quest" },
					{ text: "Objectives", link: "/guide/objectives" },
					{ text: "Rewards and progression", link: "/guide/rewards" },
					{ text: "Translations", link: "/guide/localization" },
					{ text: "Import and publish", link: "/guide/import-and-publish" },
					{ text: "Backups and updates", link: "/guide/backups-and-updates" },
				],
			},
			{
				text: "Reference",
				items: [
					{ text: "Commands", link: "/reference/commands" },
					{ text: "Java integration", link: "/reference/java-api" },
					{ text: "Hub HTTP API", link: "/reference/web-api" },
					{ text: "Host the web service", link: "/reference/hosting" },
					{ text: "Build and contribute", link: "/reference/development" },
					{ text: "Pack format", link: "/reference/pack-format" },
					{ text: "Configuration", link: "/reference/configuration" },
					{ text: "Extensions and registry", link: "/reference/extensions" },
					{ text: "Capabilities and limits", link: "/reference/limitations" },
				],
			},
		],
		outline: { level: [2, 3] },
	},
});

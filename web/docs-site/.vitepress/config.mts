import { defineConfig } from "vitepress";

export default defineConfig({
	title: "QuestPlus",
	description: "Learn to play, create quest packs and integrate QuestPlus with illustrated Minecraft and Quest Studio guides.",
	lang: "en-US",
	base: "/QuestPlus/docs/",
	srcDir: "content",
	outDir: "dist",
	cleanUrls: false,
	appearance: "force-dark",
	markdown: { theme: "github-dark" },
	lastUpdated: true,
	head: [
		["meta", { name: "theme-color", content: "#18181b" }],
		["link", { rel: "icon", href: "/QuestPlus/docs/questplus-logo.png" }],
	],
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
			{ text: "Integrations", link: "/reference/java-api" },
			{ text: "Commands", link: "/reference/commands" },
			{ text: "Open editor", link: "https://quest.alexistb.com/", target: "_blank" },
		],
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

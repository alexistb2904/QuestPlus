import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'Quest Plus',
  description: 'Quest Plus guides and integration reference',
  lang: 'en-US',
  base: '/QuestPlus/docs/',
  srcDir: 'content',
  outDir: 'dist',
  cleanUrls: false,
  appearance: 'force-dark',
  markdown: { theme: 'github-dark' },
  lastUpdated: true,
  head: [
    ['meta', { name: 'theme-color', content: '#18181b' }],
    ['link', { rel: 'icon', href: '/QuestPlus/docs/questplus-mark.svg' }]
  ],
  locales: {
    root: { label: 'English', lang: 'en-US' }
    // Additional language roots can be added as translated pages are written.
  },
  themeConfig: {
    logo: '/questplus-mark.svg',
    siteTitle: 'QuestPlus Docs',
    search: { provider: 'local' },
    nav: [
      { text: 'Get started', link: '/guide/install' },
      { text: 'Quest Studio', link: '/guide/studio' },
      { text: 'Integrations', link: '/reference/java-api' },
      { text: 'Commands', link: '/reference/commands' },
      { text: 'Open editor', link: 'https://alexistb2904.github.io/QuestPlus/', target: '_blank' }
    ],
    sidebar: [
      {
        text: 'Get started',
        items: [
          { text: 'Install Quest Plus', link: '/guide/install' },
          { text: 'Use the tablet', link: '/guide/tablet' },
          { text: 'Quest Studio', link: '/guide/studio' }
        ]
      },
      {
        text: 'Build a quest pack',
        items: [
          { text: 'Categories and quests', link: '/guide/categories-and-quests' },
          { text: 'Objectives', link: '/guide/objectives' },
          { text: 'Rewards and progression', link: '/guide/rewards' },
          { text: 'Translations', link: '/guide/localization' },
          { text: 'Import and publish', link: '/guide/import-and-publish' }
        ]
      },
      {
        text: 'Reference',
        items: [
          { text: 'Commands', link: '/reference/commands' },
          { text: 'Java integration', link: '/reference/java-api' },
          { text: 'Hub HTTP API', link: '/reference/web-api' },
          { text: 'Pack format', link: '/reference/pack-format' },
          { text: 'Extensions and registry', link: '/reference/extensions' },
          { text: 'Capabilities and limits', link: '/reference/limitations' }
        ]
      }
    ],
    outline: { level: [2, 3] },
    footer: {
      message: 'QUEST PLUS  ·  BUILD YOUR ADVENTURE',
      copyright: 'Quest Plus'
    }
  }
})

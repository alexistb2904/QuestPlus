import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'Quest Plus',
  description: 'Quest Plus documentation for players and modpack authors',
  lang: 'en-US',
  base: '/QuestPlus/docs/',
  srcDir: 'content',
  outDir: 'dist',
  cleanUrls: false,
  appearance: 'dark',
  lastUpdated: true,
  head: [
    ['meta', { name: 'theme-color', content: '#241c2c' }],
    ['meta', { property: 'og:title', content: 'Quest Plus Documentation' }]
  ],
  locales: {
    root: { label: 'English', lang: 'en-US' }
    // Add fr, es or zh locale roots with translated navigation when content exists.
  },
  themeConfig: {
    siteTitle: 'QUEST PLUS',
    search: { provider: 'local' },
    nav: [
      { text: 'Guide', link: '/guide/install' },
      { text: 'Authoring', link: '/guide/studio' },
      { text: 'Reference', link: '/reference/commands' },
      { text: 'Quest Studio', link: 'https://alexistb2904.github.io/QuestPlus/', target: '_blank' }
    ],
    sidebar: [
      {
        text: 'Get started',
        items: [
          { text: 'Install Quest Plus', link: '/guide/install' },
          { text: 'Play and use the tablet', link: '/guide/tablet' },
          { text: 'Quest Studio overview', link: '/guide/studio' }
        ]
      },
      {
        text: 'Author a quest pack',
        items: [
          { text: 'Categories and quests', link: '/guide/categories-and-quests' },
          { text: 'Objectives and prerequisites', link: '/guide/objectives' },
          { text: 'Rewards and progression', link: '/guide/rewards' },
          { text: 'Translations and media', link: '/guide/localization' },
          { text: 'Import and publish', link: '/guide/import-and-publish' }
        ]
      },
      {
        text: 'Technical reference',
        items: [
          { text: 'Commands', link: '/reference/commands' },
          { text: 'Pack format', link: '/reference/pack-format' },
          { text: 'Extensions and registry', link: '/reference/extensions' },
          { text: 'Capabilities and limits', link: '/reference/limitations' }
        ]
      }
    ],
    outline: { level: [2, 3] },
    socialLinks: [{ icon: 'github', link: 'https://github.com/alexistb2904/QuestPlus' }],
    editLink: {
      pattern: 'https://github.com/alexistb2904/QuestPlus/edit/main/docs-site/content/:path',
      text: 'Edit this page on GitHub'
    },
    footer: {
      message: 'Made for modpack authors. Content is maintained alongside Quest Plus.',
      copyright: 'Quest Plus'
    }
  }
})

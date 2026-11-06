# Localization and media

## Quest text uses Minecraft language codes

Every category and quest should provide `en_us` as the source. Additional content locales use codes such as `fr_fr`, `es_es`, `zh_cn` or `de_de`.

```json5
title: {
  en_us: 'Explore the Nether',
  fr_fr: 'Explorez le Nether'
}
```

In Quest Studio, set the English title and description in Properties, then use the **Translations** view to add other languages. New locales can be seeded from English and edited independently.

Quest text, objective labels, reward text and annotations can be localized. Empty required English fields are validation errors.

## Interface language is separate

Quest Studio's interface currently supports **English, French, Spanish and simplified Chinese**. Changing it does not translate the content of your pack.

The documentation starts in English. Its VitePress configuration has a locale structure ready for later translated Markdown trees.

## Annotations

Annotations can be labels, headings, notes or backgrounds. The rich editor supports bold, italic, underlined and struck text, sizing, and embedded images or GIFs. Image data is included with exported definitions rather than hosted remotely.

Use media sparingly because base64 files increase pack size. The game does not support arbitrary hosted videos as in-tree annotations.

## Presentation guidance

Keep quest titles short, avoid repeating objective text as a description and test long translations at different GUI scales. For accessibility, keep contrast readable in the tablet and do not rely on color alone to convey prerequisite state.

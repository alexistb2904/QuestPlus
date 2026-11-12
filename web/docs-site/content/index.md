---
layout: home
markdownStyles: false
title: "Quest Plus: Minecraft quest mod for Fabric and NeoForge 26.1+"
titleTemplate: false
description: "Free quest mod for Minecraft Java 26.1, 26.2 and 26.3 on Fabric and NeoForge. Animated quest tablet, quest blocks, NPC quest givers, loot crates, teams and a visual editor with FTB Quests import."
---

<script setup>
import { withBase } from 'vitepress'
import { onMounted } from 'vue'

// Visitors who ask for reduced motion get paused clips with controls instead of autoplay.
onMounted(() => {
  if (!matchMedia('(prefers-reduced-motion: reduce)').matches) return
  for (const clip of document.querySelectorAll('.qp-home video')) {
    clip.pause()
    clip.controls = true
  }
})
</script>

<div class="qp-home">
<section class="qp-hero">
  <div class="qp-hero__copy">
    <h1>Quests you hold in your hands.</h1>
    <p>Quest Plus is a quest mod for Minecraft built around an animated tablet. Follow quest trees, deliver resources to blocks in your world, meet quest givers and open loot crates. Play the bundled adventures or build your own in Quest Studio.</p>
    <div class="qp-actions">
      <a class="qp-button qp-button--grass" :href="withBase('/guide/install.html')">Get started</a>
      <a class="qp-button qp-button--violet" href="https://quest.alexistb.com/">Open Quest Studio</a>
    </div>
    <p class="qp-hero__meta">Minecraft Java 26.1 to 26.3 on Fabric and NeoForge. Free on <a href="https://modrinth.com/mod/quest-plus">Modrinth</a> and <a href="https://www.curseforge.com/minecraft/mc-mods/quest-plus">CurseForge</a>.</p>
  </div>
  <video class="qp-frame qp-hero__clip" :src="withBase('/media/tablet.mp4')" :poster="withBase('/media/tablet-shader.webp')" width="1600" height="900" autoplay muted loop playsinline aria-label="The tablet opens in the player's hands, shows a quest tree and closes"></video>
</section>

<section class="qp-row">
  <a class="qp-frame" :href="withBase('/guide/tablet.html')"><img :src="withBase('/media/details.webp')" alt="The tablet showing the equipment quest tree beside the details of a quest" width="1600" height="900" loading="lazy"></a>
  <div class="qp-row__copy">
    <h2>Find your next quest at a glance</h2>
    <p>Press J and the tablet comes up in your hands. Browse chapters, follow a quest tree and read what an objective asks for. Pin up to three quests to your HUD and claim your rewards when you are done.</p>
    <p>More than 200 quests are bundled, from first tools to the End.</p>
    <a class="qp-link" :href="withBase('/guide/tablet.html')">Use the tablet →</a>
  </div>
</section>

<section class="qp-row qp-row--flip">
  <a class="qp-frame" :href="withBase('/guide/world-blocks.html')"><img :src="withBase('/media/hub.webp')" alt="A quest hub with a Submission Station under a hopper, a quest giver, a Quest Board and a Quest Relay under a redstone lamp" width="1600" height="900" loading="lazy"></a>
  <div class="qp-row__copy">
    <h2>Put quests in your world</h2>
    <p>Three craftable blocks turn a base into a quest hub.</p>
    <dl class="qp-blocks">
      <div><dt>Quest Board</dt><dd>A wall display for pinned quests and leaderboards, built from up to 256 connected modules.</dd></div>
      <div><dt>Submission Station</dt><dd>Takes items, fluids and energy from hoppers and pipes for delivery quests.</dd></div>
      <div><dt>Quest Relay</dt><dd>Turns quest progress into a redstone signal.</dd></div>
    </dl>
    <a class="qp-link" :href="withBase('/guide/world-blocks.html')">Craft the quest blocks →</a>
  </div>
</section>

<section class="qp-stage">
  <video class="qp-frame" :src="withBase('/media/loot-crate.mp4')" :poster="withBase('/media/loot-chest.webp')" width="1600" height="900" autoplay muted loop playsinline aria-label="A loot crate placed on the ground shakes, bursts open and hands out its rewards"></video>
  <div class="qp-row__copy">
    <h2>Crack open a loot crate <span class="qp-new">New reveal in 1.1.3</span></h2>
    <p>Use a crate on the ground and a chest rises, shakes and bursts open with your rewards. The server draws and saves the loot first, so the reveal is pure show.</p>
    <p>Pack creators turn any reward table into a crate, then choose its name, its color and which mobs drop it.</p>
    <a class="qp-link" :href="withBase('/guide/givers-and-crates.html#open-a-loot-crate')">How loot crates work →</a>
  </div>
</section>

<section class="qp-row qp-row--flip">
  <a class="qp-frame qp-frame--dialog" :href="withBase('/guide/givers-and-crates.html')"><img :src="withBase('/media/giver-offer.webp')" alt="Marlo the Cartographer offering the Blank Pages quest with its objectives and rewards" width="1280" height="800" loading="lazy"></a>
  <div class="qp-row__copy">
    <h2>Meet the quest givers <span class="qp-new">New in 1.1.3</span></h2>
    <p>Quest givers wait in villages, ruins and camps. Talk to one to hear a request, accept it, then come back for your reward.</p>
    <p>The bundled Explorers guild adds three of them, and every pack can define its own.</p>
    <a class="qp-link" :href="withBase('/guide/givers-and-crates.html')">Find a quest giver →</a>
  </div>
</section>

<section class="qp-row">
  <a class="qp-frame" :href="withBase('/guide/teams.html')"><img :src="withBase('/media/board-world.webp')" alt="A Quest Board listing three active quests beside the top adventurers ranking" width="1600" height="900" loading="lazy"></a>
  <div class="qp-row__copy">
    <h2>Progress as a team</h2>
    <p>Create a team from the tablet, invite your friends and share quest progression. Quest Boards rank players, teams and station contributions.</p>
    <a class="qp-link" :href="withBase('/guide/teams.html')">Play with a team →</a>
  </div>
</section>

<section class="qp-studio">
  <div class="qp-row__copy">
    <h2>Build your own adventure</h2>
    <p>Quest Studio is a visual editor that runs in your browser. Arrange quest trees, set objectives and rewards, design quest givers and crates, translate your pack and export a ZIP. It works offline, and an operator can connect it to a live server.</p>
    <div class="qp-actions">
      <a class="qp-button qp-button--violet" href="https://quest.alexistb.com/">Open Quest Studio</a>
      <a class="qp-link" :href="withBase('/guide/studio.html')">Read the Studio guide →</a>
    </div>
  </div>
  <a class="qp-frame" :href="withBase('/guide/studio.html')"><img :src="withBase('/media/studio.webp')" alt="Quest Studio with the quest library, a quest diagram, objectives and properties" width="1920" height="1080" loading="lazy"></a>
</section>

<section class="qp-row__copy qp-ftb">
  <h2>Coming from FTB Quests?</h2>
  <p>Quest Studio imports FTB Quests chapters in SNBT, JSON or JSON5, shows a conversion report and exports a Quest Plus pack for Minecraft 26.1 to 26.3. Operators can migrate existing player progress with a preview and a rollback.</p>
  <a class="qp-link" :href="withBase('/guide/ftb-quests-alternative.html')">Move a modpack from FTB Quests →</a>
</section>

<nav class="qp-paths" aria-label="Guides by audience">
  <h2>Choose your path</h2>
  <a :href="withBase('/guide/install.html')"><strong>Players</strong><span>Install the mod and open your tablet</span></a>
  <a :href="withBase('/guide/modpack-creators.html')"><strong>Modpack creators</strong><span>Create, test and ship your quests</span></a>
  <a :href="withBase('/guide/import-and-publish.html')"><strong>Server operators</strong><span>Connect Studio and publish a pack</span></a>
  <a :href="withBase('/reference/java-api.html')"><strong>Developers</strong><span>Integrate your own mechanics</span></a>
</nav>
</div>

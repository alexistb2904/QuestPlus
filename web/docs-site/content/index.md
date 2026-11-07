---
layout: home
hero:
    name: QUEST PLUS
    text: Your adventure, your quests.
    tagline: A Minecraft quest system for players, pack creators and server communities.
    image:
        src: /media/tablet.gif
        alt: The Quest Plus tablet open in Minecraft
    actions:
        - theme: brand
          text: Get started
          link: /guide/install
        - theme: alt
          text: Open Quest Studio
          link: https://alexistb2904.github.io/QuestPlus/
---

<script setup>
import { withBase } from 'vitepress'
</script>

<div class="home-intro">
  <h2>Give every adventure a direction.</h2>
  <p>Learn to explore quest trees, track your next objective and claim rewards from your Minecraft tablet. Then build your own adventures in Quest Studio.</p>
</div>

<div class="home-showcase">
  <article class="home-story">
    <img :src="withBase('/media/hud.webp')" alt="A quest objective tracked on the Minecraft HUD" loading="lazy" width="1600" height="900">
    <div><h3>Keep the adventure moving</h3><p>Open the tablet to explore quests and pin an objective to your HUD while you play.</p><a :href="withBase('/guide/tablet.html')">Discover the tablet →</a></div>
  </article>
  <article class="home-story">
    <img :src="withBase('/media/studio.webp')" alt="Quest Studio showing a quest tree and its properties" loading="lazy" width="1920" height="1080">
    <div><h3>Build a world of quests</h3><p>Use Quest Studio to shape categories, connect quests and configure objectives and rewards. Work offline or connect to your server.</p><a :href="withBase('/guide/studio.html')">Explore Quest Studio →</a></div>
  </article>
</div>

<div class="home-paths">
  <div><h2>Choose your path.</h2><p>Everything you need to play, create or integrate Quest Plus.</p></div>
  <a :href="withBase('/guide/install.html')"><strong>Players</strong><small>Install the mod and open your tablet</small><b>↗</b></a>
  <a :href="withBase('/guide/modpack-creators.html')"><strong>Modpack creators</strong><small>Create, test and ship your quests</small><b>↗</b></a>
  <a :href="withBase('/reference/java-api.html')"><strong>Developers</strong><small>Integrate your own mechanics</small><b>↗</b></a>
  <a :href="withBase('/guide/import-and-publish.html')"><strong>Server operators</strong><small>Connect and publish a pack</small><b>↗</b></a>
</div>

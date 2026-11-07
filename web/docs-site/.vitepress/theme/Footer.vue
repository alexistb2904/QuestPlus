<script setup lang="ts">
import { withBase } from "vitepress";
</script>

<template>
	<footer class="QPFooter">
		<div class="QPFooter__inner">
			<div class="QPFooter__brand">
				<img :src="withBase('/questplus-logo.png')" alt="" width="48" height="48" />
				<div>
					<strong>Quest Plus</strong>
					<p>Build your adventure.</p>
				</div>
			</div>
			<div class="QPFooter__explore">
				<strong>Explore Quest Plus</strong>
				<a :href="withBase('/guide/install.html')">Get started</a>
				<a :href="withBase('/guide/tablet.html')">The quest tablet</a>
				<a :href="withBase('/guide/studio.html')">Quest Studio guide</a>
				<a class="QPFooter__studio" href="/QuestPlus/">Open Quest Studio <span aria-hidden="true">↗</span></a>
			</div>
			<nav class="QPFooter__links" aria-label="Quest Plus links">
				<strong>Find us</strong>
				<a href="https://www.curseforge.com/minecraft/mc-mods/quest-plus" target="_blank" rel="noreferrer">CurseForge <span aria-hidden="true">↗</span></a>
				<a href="https://modrinth.com/mod/quest-plus" target="_blank" rel="noreferrer">Modrinth <span aria-hidden="true">↗</span></a>
				<a href="https://github.com/alexistb2904/QuestPlus" target="_blank" rel="noreferrer">GitHub <span aria-hidden="true">↗</span></a>
			</nav>
		</div>
		<div class="QPFooter__base">
			<span>QUEST PLUS <i>·</i> BUILD YOUR ADVENTURE</span><span>Community project for Minecraft</span>
		</div>
	</footer>
</template>

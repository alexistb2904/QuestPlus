# Play and use the tablet

## Opening the interface

Press **J** to open Quest Plus. Change the binding under Minecraft Controls. Depending on client libraries and settings, the interface appears inside a held animated tablet or as a 2D quest screen.

The tablet contains categories, quests, quest details, reward actions and player/team controls.

## Quest flow

1. Pick a category from the library.
2. Select a quest node to inspect objectives, prerequisites and rewards.
3. Complete gameplay tasks on the server. The server, not the client UI, decides progression.
4. When available, claim the reward in the quest details.
5. Use **Collect All** for eligible rewards if you have several pending claims.

You can pin **up to three quests** to the in-game HUD. Completed quests unpin automatically. New or unlocked quests may display category badges and completion/unlock notifications.

For manual objectives, use the action provided by the tablet or `/questplus trigger <quest>`. For item or XP hand-ins, use the submit action or the `/questplus submit` command. Submitting an item and merely owning one are different mechanics.

## Progression and teams

Quest definitions can require every objective, any objective, a sequence or a minimum count. Depending on the quest, completion can be one-time, manual restart, automatic repetition or tier-based.

The **Team** view manages invitations and shared progression. Team ownership and rewards are server-authoritative. Use `/questplus team info` and `/questplus team invitations` to inspect membership.

## Presentation settings

The tablet offers display mode, scale, lighting, background blur, animation and reduced-motion controls. HUD and notification placement are configurable.

If a shader causes a rendering problem, switch to 2D mode or disable shader-specific visual settings before reporting the issue. A screenshot alone is not evidence of compatibility across all shaders.

See [commands](/reference/commands) for the complete command tree.

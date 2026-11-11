# Troubleshooting

Start with the symptom below. Keep a copy of your exported pack and the relevant `logs/latest.log` before changing definitions or progress.

## The tablet does not open

Confirm that QuestPlus and Fabric API are installed for the same Minecraft Java version (26.1, 26.1.1, 26.1.2, 26.2 or 26.3) on the client and server. Check Minecraft Controls for the QuestPlus binding and conflicts with **J**. Try the flat 2D interface if the problem occurs during a 3D animation.

## A quest is missing or locked

Check its category, prerequisites and visibility settings. A hidden quest is revealed when its requirements are satisfied or it already has progress. A new default pack does not replace an existing world's definitions.

Operators can run `/questplus diagnose <quest>` and `/questplus progress <quest>`. Use the exact namespaced quest ID from Studio.

## An objective does not advance

1. Test in survival or adventure mode.
2. Read the objective type. Breaking, crafting, collecting, possessing and submitting are different events.
3. Check its target ID, tag, component rules, conditions and prerequisites.
4. For manual submissions, select **Submit** and verify that the required items or XP are available.
5. For custom machinery, fluids or energy, confirm that the relevant server integration is installed.

Changing a quest's mechanics can make existing partial progress incompatible. Renaming IDs does not migrate progress. Test upgrades on a copy of the world.

## A reward cannot be claimed

Make room in your inventory. Check for a required choice, a cooldown, a team policy or a reward excluded from **Collect All**. A completed objective is not proof that a reward has been delivered.

If a claim needs recovery after an interruption, an operator can inspect it with `/questplus recovery inspect <player>`. Record the result before resolving anything. Do not delete claim history or repeatedly reset the quest to force payment.

## Studio cannot save to the server

Keep the browser draft and export a ZIP. Check that the optional web service is running and that the initiating operator remains connected. A session link alone does not grant editing access.

| Message or situation       | Next action                                                          |
| -------------------------- | -------------------------------------------------------------------- |
| Approval required          | Request access, then approve the matching code in Minecraft          |
| Session expired or revoked | Start a new session with `/questplus editor`                         |
| Revision conflict          | Export the draft, reload the server version and reconcile your edits |
| Validation rejected        | Fix the listed quest, registry ID or unsupported field               |
| Service busy               | Follow the retry delay and keep the draft open                       |
| Static editor only         | Export a ZIP or configure the optional web service                   |

Wait for the Minecraft acknowledgment before treating a publication as applied. Closing the tab does not revoke a session. Use `/questplus editor revoke`.

## A reload failed

Read the first validation error in the server log. Check referenced categories, quest IDs, prerequisite cycles and installed mods. The server retains or restores the previous valid definitions when a reload fails.

Use `/questplus rollback` to restore the latest definition backup when appropriate. This does not undo gameplay or recover player data from an unrelated world backup.

## Report a reproducible issue

Include Minecraft, Fabric Loader, Fabric API and QuestPlus versions, the smallest affected quest, the action you performed and the expected result. Add a screenshot and the relevant log excerpt. For rendering issues, include shader and animation mod versions.

Remove session credentials, editor tokens, private session links and unrelated player information before sharing files in [an issue](https://github.com/alexistb2904/QuestPlus/issues).

# Totem no animation (Fabric, Minecraft 1.21.11)

- After a totem pop: opens inventory -> puts a totem into the LEFT hand -> closes inventory
  (random 15-23 ms before opening and before closing, random 88-94 ms before the totem goes into the left hand)
- After the swap it checks that the totem is really in the left hand; if not, it tries once more (2 attempts max), then leaves it alone
- If another totem pops while it is working, it puts a new totem again before closing the inventory
- Hides the totem pop animation
- Burning sounds 50% quieter
- Client-side only, does not touch rendering -> works with VulkanMod

Settings (constants): `TotemRefill.java` (timings), `BurningSounds.java` (sounds / volume).

## Build on GitHub
Upload the whole project (including the `.github` folder), open the **Actions** tab,
wait for the `build` job and download the artifact `Totem-no-animation` (the jar inside).

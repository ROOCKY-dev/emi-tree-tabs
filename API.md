# The Tree Tabs API

For mods that want to add to EMI Tree Tabs — most immediately
[Quartermaster](https://github.com/ROOCKY-dev/quartermaster), which remembers what your containers
held and tells the crafting list where things are.

**Why it exists.** Tree Tabs reaches into EMI's internals through mixins. That is the largest risk
this project carries, and the point of this package is that nothing else has to carry it: you talk
to these interfaces, and an EMI update that renames something breaks Tree Tabs alone.

Everything outside `dev.roocky.emitreetabs.api` is internal and will change without notice.

## Depending on it

There is no Maven artifact yet. Copy the five files in `dev/roocky/emitreetabs/api/` into your
project as compile-only sources, or add the released jar as a `compileOnly` dependency. Do **not**
bundle it.

## Using it without a hard dependency

The failure mode to design against is a crash on a user's machine in a build that compiled cleanly,
because the Tree Tabs they have installed is older than the one you built against. So the entry
point is one method whose signature is frozen, and everything else hangs off an interface:

```java
try {
    TreeTabsRegistry registry = TreeTabsApi.registry(TreeTabsApi.VERSION);
    if (registry != null) {
        registry.registerStockSource("quartermaster", new ChestStock());
        registry.registerLocateProvider("quartermaster", new ChestLocator());
        registry.addCraftingListListener(() -> refreshMyOverlay());
    }
} catch (Throwable ignored) {
    // Tree Tabs is absent, or too old. Carry on without it.
}
```

`Throwable`, not `Exception`: a missing class throws `NoClassDefFoundError` and a missing method
`NoSuchMethodError`, and both are `Error`s.

`TreeTabsApi.VERSION` is a compile-time constant, so passing it back states the version you were
*built* against — which is the question being asked. If this build cannot serve it, you get `null`
rather than a registry that would break halfway through.

Register during your own client init. Tree Tabs installs the registry during its init and does not
care which of you runs first.

## What the version means

| Change | Bumps `VERSION`? |
| --- | --- |
| A method added to `TreeTabsRegistry` | No — an older consumer never calls it |
| A method changed or removed | Yes, and the old version then gets `null` |
| A new interface added | No |

Version 1: stock sources, locate providers, crafting-list listeners.

## The three things you can register

### `StockSource` — materials the player has elsewhere

Its stock joins the pool the crafting list is costed against, so a tree needing 64 iron that finds
it in a registered source stops asking for it. The tooltip says where it came from, because "you
have it" and "you have it three rooms away" are different answers.

**Off until the player turns it on** (`useExternalStock`, in *Crafting list → Advanced*). Silently
changing what the list asks for, on the strength of another mod's idea of what the player owns, is
not a default worth taking.

Plain `ItemStack`s, not EMI types: a container holds stacks, and you should not have to learn EMI's
stack model to say what is in a box. Matching against tags is Tree Tabs' job.

### `LocateProvider` — where to find a shortfall

Asked about a material the crafting list says the player is short of, at the moment the cursor is
on it. Return one line per place, or an empty list. Tree Tabs shows at most three and truncates the
rest.

### `CraftingListListener` — the list changed

Fires after the list is rebuilt from every tree being worked on. That happens whenever anything
feeding it moves, so it fires often.

## Rules for all three

- **Called on the client thread, inside a pass.** Never block, never open a screen, never register
  or unregister from inside a callback.
- **Be cheap.** A `StockSource` is asked once per crafting-list pass; a `LocateProvider` is asked
  while a tooltip is being built. Cache on your side if the real answer is expensive.
- **Throwing is not fatal, but it is final.** A callback that throws is logged once and that
  integration is dropped for the session, rather than taking the crafting list down with it. The
  log says which mod, because the player would otherwise conclude EMI is broken.
- **Ids are stable strings**, conventionally your mod id. Registering the same id twice replaces
  the first, so reloading your own integration does not accumulate duplicates.

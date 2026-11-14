package com.totemnoanimation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * After a totem pop: open inventory -> move a totem to the LEFT hand (offhand) -> close inventory.
 *
 * Open inventory and close inventory each happen after a random delay of ACTION_MIN_MS..ACTION_MAX_MS
 * (15-23 ms). The totem swap into the LEFT hand happens after its own random delay of
 * SWAP_MIN_MS..SWAP_MAX_MS (88-94 ms). There is no other base delay.
 *
 * If another totem pops while this is running, the mod does NOT close the inventory:
 * it puts a new totem into the left hand again and only then closes.
 *
 * After the swap the mod checks (VERIFY_AFTER_MS later) that the totem is really in the left hand.
 * If it is not, it tries once more. If that fails too (2 attempts in total), it leaves it alone.
 */
public final class TotemRefill {
    /** Random delay before opening and before closing the inventory. */
    public static final int ACTION_MIN_MS = 15;
    public static final int ACTION_MAX_MS = 23;
    /** Random delay before putting the totem into the left hand. */
    public static final int SWAP_MIN_MS = 88;
    public static final int SWAP_MAX_MS = 94;

    /** The result of a swap is checked at least this long after the click (gives the server time to correct us). */
    private static final int VERIFY_AFTER_MS = 40;
    /** Total swap attempts per totem pop (first try + one retry). After that the mod leaves it alone. */
    private static final int MAX_SWAP_ATTEMPTS = 2;

    /**
     * The server tells the client that the totem is gone a few ms AFTER the pop message.
     * Before clicking, the mod waits (checking every SYNC_POLL_MS, at most SYNC_MAX_WAIT_MS after the pop)
     * until the client really sees the totem gone, so it never clicks a stale slot.
     */
    private static final int SYNC_POLL_MS = 5;
    private static final int SYNC_MAX_WAIT_MS = 100;

    private static final int OFFHAND_BUTTON = 40; // "swap with offhand" button of a slot click
    private static final int FIRST_SLOT = 9;      // PlayerScreenHandler: 9-35 inventory
    private static final int LAST_SLOT = 44;      // 36-44 hotbar
    private static final int OFFHAND_SLOT = 45;   // offhand

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "TotemNoAnimation-Scheduler");
        t.setDaemon(true);
        return t;
    });

    private static final AtomicBoolean BUSY = new AtomicBoolean(false);
    // These are only touched on the client thread:
    private static boolean openedByMod = false;
    private static boolean popPending = false; // a totem popped while the sequence was running
    private static int totemsAtPop = 0;
    private static long syncDeadlineNs = 0L;
    private static int swapAttempts = 0;
    private static long lastClickNs = 0L; // 0 = no click in the last swap step

    private TotemRefill() {
    }

    /** Called (on the client thread) when the player's totem pops. */
    public static void onTotemPop() {
        markPop();
        if (!BUSY.compareAndSet(false, true)) {
            // sequence already running -> remember it, we will put one more totem before closing
            popPending = true;
            return;
        }
        popPending = false;
        swapAttempts = 0;
        lastClickNs = 0L;
        scheduleIn(actionDelayMs(), TotemRefill::stepOpen);
    }

    private static void markPop() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        totemsAtPop = player == null ? 0 : countTotems(player);
        syncDeadlineNs = System.nanoTime() + SYNC_MAX_WAIT_MS * 1_000_000L;
    }

    private static long actionDelayMs() {
        return ThreadLocalRandom.current().nextInt(ACTION_MIN_MS, ACTION_MAX_MS + 1);
    }

    private static long swapDelayMs() {
        return ThreadLocalRandom.current().nextInt(SWAP_MIN_MS, SWAP_MAX_MS + 1);
    }

    private static void scheduleIn(long delayMs, Runnable task) {
        SCHEDULER.schedule(
                () -> MinecraftClient.getInstance().execute(() -> run(task)),
                delayMs,
                TimeUnit.MILLISECONDS
        );
    }

    private static void run(Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            TotemNoAnimationClient.LOGGER.error("Totem refill failed", t);
            finish();
        }
    }

    /** End of the sequence: closes the inventory if the mod opened it. */
    private static void finish() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (openedByMod && mc.currentScreen instanceof InventoryScreen) {
                mc.currentScreen.close();
            }
        } catch (Throwable ignored) {
        }
        openedByMod = false;
        popPending = false;
        swapAttempts = 0;
        lastClickNs = 0L;
        BUSY.set(false);
    }

    private static int findTotemSlot(ClientPlayerEntity player) {
        PlayerScreenHandler handler = player.playerScreenHandler;
        for (int i = FIRST_SLOT; i <= LAST_SLOT; i++) {
            if (handler.getSlot(i).getStack().isOf(Items.TOTEM_OF_UNDYING)) {
                return i;
            }
        }
        return -1;
    }

    /** Totems in inventory + hotbar + offhand. */
    private static int countTotems(ClientPlayerEntity player) {
        PlayerScreenHandler handler = player.playerScreenHandler;
        int count = 0;
        for (int i = FIRST_SLOT; i <= OFFHAND_SLOT; i++) {
            if (handler.getSlot(i).getStack().isOf(Items.TOTEM_OF_UNDYING)) {
                count++;
            }
        }
        return count;
    }

    // ---- action 1: open inventory ----
    private static void stepOpen() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.interactionManager == null || !player.isAlive() || findTotemSlot(player) < 0) {
            finish();
            return;
        }

        Screen current = mc.currentScreen;
        if (current == null) {
            openedByMod = true;
            mc.setScreen(new InventoryScreen(player));
        } else if (!(current instanceof InventoryScreen)) {
            finish(); // some other screen (chest, chat, ...) - don't touch it
            return;
        }
        // (if an inventory is already open we just use it; openedByMod stays as it was)
        scheduleIn(swapDelayMs(), TotemRefill::stepSwap);
    }

    // ---- action 2: totem -> left hand ----
    private static void stepSwap() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.interactionManager == null) {
            finish();
            return;
        }

        // The client has not been told yet that the popped totem is gone -> check again in a few ms
        if (countTotems(player) >= totemsAtPop && System.nanoTime() < syncDeadlineNs) {
            scheduleIn(SYNC_POLL_MS, TotemRefill::stepSwap);
            return;
        }

        popPending = false; // we are looking at the current state right now

        boolean clicked = false;
        if (!player.getStackInHand(Hand.OFF_HAND).isOf(Items.TOTEM_OF_UNDYING)) {
            int slot = findTotemSlot(player);
            if (slot >= 0) {
                mc.interactionManager.clickSlot(
                        player.playerScreenHandler.syncId, slot, OFFHAND_BUTTON, SlotActionType.SWAP, player);
                clicked = true;
                swapAttempts++;
            }
        }
        lastClickNs = clicked ? System.nanoTime() : 0L;
        scheduleIn(actionDelayMs(), TotemRefill::stepClose);
    }

    // ---- action 3: check the result, then close inventory (or go again) ----
    private static void stepClose() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;

        // Give the server time to correct us if it did not accept the swap
        if (lastClickNs != 0L) {
            long remainingMs = VERIFY_AFTER_MS - (System.nanoTime() - lastClickNs) / 1_000_000L;
            if (remainingMs > 0) {
                scheduleIn(remainingMs, TotemRefill::stepClose);
                return;
            }
        }

        boolean alive = player != null && player.isAlive();

        if (alive && popPending) {
            // another totem popped meanwhile -> fresh start, put the next totem
            popPending = false;
            swapAttempts = 0;
            if (findTotemSlot(player) >= 0) {
                placeAgain(mc);
                return;
            }
        } else if (alive
                && !player.getStackInHand(Hand.OFF_HAND).isOf(Items.TOTEM_OF_UNDYING)
                && swapAttempts < MAX_SWAP_ATTEMPTS
                && findTotemSlot(player) >= 0) {
            // the totem did not end up in the left hand -> put it again (once). After 2 failed attempts: leave it.
            placeAgain(mc);
            return;
        }
        finish();
    }

    /** Inventory still open -> just do the swap again; closed meanwhile -> open it again first. */
    private static void placeAgain(MinecraftClient mc) {
        if (mc.currentScreen instanceof InventoryScreen) {
            scheduleIn(swapDelayMs(), TotemRefill::stepSwap);
        } else {
            scheduleIn(actionDelayMs(), TotemRefill::stepOpen);
        }
    }
}

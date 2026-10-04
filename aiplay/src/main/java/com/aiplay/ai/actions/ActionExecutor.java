package com.aiplay.ai.actions;

import com.aiplay.ai.AIDecision;
import com.aiplay.ai.AIManager;
import com.aiplay.config.ConfigManager;
import com.aiplay.config.ModConfig;
import com.aiplay.input.Inputs;
import com.aiplay.items.ItemKnowledge;
import com.aiplay.items.ItemKnowledgeManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayDeque;
import java.util.List;

/** Executes the whitelisted actions one by one on the game thread. The model can never run arbitrary code. */
public final class ActionExecutor {
	private static final ArrayDeque<Action> queue = new ArrayDeque<>();
	private static int wait, moveTicks, pendingUse, lookTicks;
	private static String moveDir = "";
	private static boolean usedDirect;
	private static Boolean sprintOv, sneakOv;
	private static float lookYaw, lookPitch;
	private static int lookAt = -1;
	/** >0 while a healing sequence is running (controller retreats / does not attack). */
	public static int healBusy;

	private ActionExecutor() {}

	public static void clearAll() {
		queue.clear();
		wait = moveTicks = pendingUse = lookTicks = healBusy = 0;
		usedDirect = false;
		sprintOv = sneakOv = null;
		lookAt = -1;
	}

	/** New decision replaces older model actions but never cancels an internal (healing) sequence. */
	public static void replaceModelActions(List<Action> actions) {
		queue.removeIf(a -> !a.internal);
		for (Action a : actions) queue.add(a);
	}

	public static void enqueueFirst(Action a) { queue.addFirst(a); }
	public static void enqueue(Action a) { queue.add(a); }
	public static boolean busy() { return pendingUse > 0 || wait > 0 || !queue.isEmpty(); }
	public static boolean usingItemNow() { return pendingUse > 0; }

	private static boolean allowed(Action a, ModConfig c) {
		if (a.internal) return true;
		return switch (a.type) {
			case "move", "look", "jump", "sprint", "sneak" -> c.aiMovement;
			case "attack" -> c.aiCombat;
			case "use_item", "interact" -> c.aiItemUsage;
			case "switch_slot", "drop_item", "swap_to_hotbar" -> c.aiInventory;
			default -> true;
		};
	}

	public static void tick(MinecraftClient mc, Inputs.Intent in) {
		ClientPlayerEntity p = mc.player;
		ModConfig cfg = ConfigManager.cfg;
		if (healBusy > 0) healBusy--;
		if (sprintOv != null) in.sprint = sprintOv;
		if (sneakOv != null) in.sneak = sneakOv;
		if (moveTicks > 0) {
			in.fwd = in.back = in.left = in.right = false;
			switch (moveDir) {
				case "back" -> in.back = true;
				case "left" -> in.left = true;
				case "right" -> in.right = true;
				default -> in.fwd = true;
			}
			moveTicks--;
		}
		if (lookTicks > 0) {
			if (lookAt >= 0) {
				Entity e = mc.world.getEntityById(lookAt);
				if (e != null) Aim.face(p, e.getBoundingBox().getCenter());
			} else Aim.rotate(p, lookYaw, lookPitch, 40f, 30f);
			lookTicks--;
		}
		if (pendingUse > 0) {
			if (mc.currentScreen != null) { // no vanilla input handling while a screen is open: use directly
				if (!usedDirect) {
					mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
					p.swingHand(Hand.MAIN_HAND);
					usedDirect = true;
				}
			} else in.use = true;
			if (--pendingUse == 0) usedDirect = false;
		}
		if (wait > 0) { wait--; return; }
		Action a = queue.poll();
		if (a == null) return;
		if (!allowed(a, cfg)) { AIManager.logAction("ignored (module off): " + a.type); return; }
		try {
			run(mc, p, a, in);
		} catch (Exception e) {
			AIManager.logAction("action failed: " + a.type + " (" + e.getClass().getSimpleName() + ")");
		}
	}

	private static int containerSlot(int invSlot) { return invSlot < 9 ? 36 + invSlot : invSlot; }

	private static void run(MinecraftClient mc, ClientPlayerEntity p, Action a, Inputs.Intent in) {
		var im = mc.interactionManager;
		AIManager.logAction(a.toString());
		switch (a.type) {
			case "move" -> {
				moveDir = a.s("direction", "forward").toLowerCase();
				if (moveDir.startsWith("back")) moveDir = "back";
				int d = a.i("duration", 500);
				if (d < 100) d *= 1000;                      // tolerate seconds
				moveTicks = MathHelper.clamp(d / 50, 2, 60);
				wait = moveTicks;
			}
			case "look" -> {
				lookAt = a.has("at") ? a.i("at", -1) : -1;
				lookYaw = (float) a.d("yaw", p.getYaw());
				lookPitch = (float) a.d("pitch", p.getPitch());
				lookTicks = 8;
				wait = 6;
			}
			case "attack" -> {
				AIManager.strategy = AIDecision.Strategy.ATTACK;
				AIManager.targetId = a.has("target") && !a.s("target", "").equals("nearest") ? a.i("target", -1) : -1;
			}
			case "jump" -> in.jump = true;
			case "sprint" -> sprintOv = a.b("enabled", true);
			case "sneak" -> sneakOv = a.b("enabled", true);
			case "stop" -> {
				AIManager.strategy = AIDecision.Strategy.IDLE;
				boolean keepHeal = healBusy > 0;
				int hb = healBusy;
				clearAll();
				if (keepHeal) healBusy = hb;
			}
			case "wait" -> wait = MathHelper.clamp(a.i("duration", 500) / 50, 1, 60);
			case "switch_slot" -> {
				int s = a.i("slot", -1);
				if (s < 0 || s > 35) throw new IllegalArgumentException("bad slot");
				if (s < 9) p.getInventory().selectedSlot = s;
				else im.clickSlot(p.playerScreenHandler.syncId, s, p.getInventory().selectedSlot, SlotActionType.SWAP, p);
				wait = 1;
			}
			case "swap_to_hotbar" -> {
				int s = a.i("slot", -1), h = a.i("hotbar", p.getInventory().selectedSlot);
				if (s < 9 || s > 35 || h < 0 || h > 8) throw new IllegalArgumentException("bad slot");
				im.clickSlot(p.playerScreenHandler.syncId, s, h, SlotActionType.SWAP, p);
				wait = 1;
			}
			case "drop_item" -> {
				int s = a.i("slot", p.getInventory().selectedSlot);
				if (s < 0 || s > 35) throw new IllegalArgumentException("bad slot");
				im.clickSlot(p.playerScreenHandler.syncId, containerSlot(s), 1, SlotActionType.THROW, p);
				wait = 2;
			}
			case "interact" -> { pendingUse = 2; wait = 3; }
			case "use_item" -> useItem(p, a);
			default -> throw new IllegalArgumentException("unknown action");
		}
	}

	private static void useItem(ClientPlayerEntity p, Action a) {
		var im = MinecraftClient.getInstance().interactionManager;
		boolean critical = ItemKnowledgeManager.criticalNow(p);
		int slot;
		if (a.has("slot")) slot = a.i("slot", -1);
		else if (a.has("item")) slot = ItemKnowledgeManager.find(p, a.s("item", ""), critical);
		else slot = p.getInventory().selectedSlot;
		if (slot < 0 || slot > 35 || p.getInventory().getStack(slot).isEmpty()) {
			AIManager.logAction("use_item: item not found (" + a.s("item", "slot " + slot) + ")");
			return;
		}
		ItemStack st = p.getInventory().getStack(slot);
		if (ItemKnowledgeManager.onCooldown(p, st)) {
			AIManager.logAction("use_item: " + ItemKnowledgeManager.displayName(st) + " is on cooldown");
			return;
		}
		if (slot >= 9) { // bring it to the hotbar first, then use it next tick
			int h = p.getInventory().selectedSlot;
			im.clickSlot(p.playerScreenHandler.syncId, slot, h, SlotActionType.SWAP, p);
			Action again = Action.internal("use_item", "slot", h);
			again.internal = a.internal;
			enqueueFirst(again);
			wait = 2;
			return;
		}
		if (p.getInventory().selectedSlot != slot) {
			p.getInventory().selectedSlot = slot;
			Action again = Action.internal("use_item", "slot", slot);
			again.internal = a.internal;
			enqueueFirst(again);
			wait = 2;
			return;
		}
		if (ItemKnowledgeManager.isSplash(st) && ItemKnowledgeManager.healScore(st, true) > 0) p.setPitch(90f); // splash heal at own feet
		ItemKnowledge k = ItemKnowledgeManager.knowledgeFor(st);
		if (k != null) k.markUsed();
		int hold = Math.max(1, Math.min(80, st.getMaxUseTime(p) + 3));
		pendingUse = hold;
		wait = hold + 1;
		if (a.internal) healBusy = Math.max(healBusy, hold + 6);
	}
}

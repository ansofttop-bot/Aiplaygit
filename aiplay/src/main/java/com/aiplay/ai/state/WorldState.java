package com.aiplay.ai.state;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Compact, relevant world info around the player (not the whole world). */
public final class WorldState {
	private WorldState() {}

	public static boolean isHazard(BlockState s) {
		return s.getFluidState().isIn(FluidTags.LAVA) || s.isOf(Blocks.FIRE) || s.isOf(Blocks.SOUL_FIRE)
				|| s.isOf(Blocks.CACTUS) || s.isOf(Blocks.MAGMA_BLOCK) || s.isOf(Blocks.SWEET_BERRY_BUSH)
				|| s.isOf(Blocks.WITHER_ROSE) || s.isOf(Blocks.COBWEB);
	}

	private static boolean solid(ClientWorld w, BlockPos p) { return !w.getBlockState(p).getCollisionShape(w, p).isEmpty(); }

	private static BlockPos ahead(ClientPlayerEntity p, double d) {
		double yaw = Math.toRadians(p.getYaw());
		Vec3d v = p.getPos().add(-Math.sin(yaw) * d, 0, Math.cos(yaw) * d);
		return BlockPos.ofFloored(v.x, v.y, v.z);
	}

	/** 0 = clear, 1 = one-block step (jumpable), 2 = wall. */
	public static int obstacleAhead(ClientWorld w, ClientPlayerEntity p) {
		BlockPos f = ahead(p, 0.9);
		if (!solid(w, f)) return 0;
		if (!solid(w, f.up()) && !solid(w, f.up(2)) && !solid(w, p.getBlockPos().up(2))) return 1;
		return 2;
	}

	/** True if walking forward leads into lava/fire/cactus or a drop deeper than 3 blocks. */
	public static boolean unsafeAhead(ClientWorld w, ClientPlayerEntity p) {
		for (double d : new double[]{1.2, 2.2}) {
			BlockPos f = ahead(p, d);
			if (isHazard(w.getBlockState(f)) || isHazard(w.getBlockState(f.up())) || isHazard(w.getBlockState(f.down()))) return true;
			boolean ground = false;
			for (int dy = 0; dy <= 3; dy++) if (solid(w, f.down(dy))) { ground = true; break; }
			if (!ground && !p.isTouchingWater()) return true;
		}
		return false;
	}

	public static JsonObject collect(MinecraftClient c) {
		JsonObject o = new JsonObject();
		ClientPlayerEntity p = c.player;
		ClientWorld w = c.world;
		o.addProperty("in_water", p.isTouchingWater());
		o.addProperty("on_fire", p.isOnFire());
		o.addProperty("standing_on", Registries.BLOCK.getId(w.getBlockState(p.getBlockPos().down()).getBlock()).getPath());
		int ob = obstacleAhead(w, p);
		o.addProperty("obstacle_ahead", ob == 0 ? "none" : ob == 1 ? "one_block_step" : "wall");
		o.addProperty("danger_ahead", unsafeAhead(w, p));
		JsonArray hz = new JsonArray();
		BlockPos b = p.getBlockPos();
		outer:
		for (int dx = -3; dx <= 3; dx++) for (int dy = -2; dy <= 2; dy++) for (int dz = -3; dz <= 3; dz++) {
			BlockState s = w.getBlockState(b.add(dx, dy, dz));
			if (isHazard(s)) {
				hz.add(Registries.BLOCK.getId(s.getBlock()).getPath() + " at (" + dx + "," + dy + "," + dz + ")");
				if (hz.size() >= 6) break outer;
			}
		}
		o.add("hazards_nearby", hz);
		JsonArray items = new JsonArray();
		for (Entity e : w.getEntities()) {
			if (e instanceof ItemEntity ie && p.distanceTo(e) < 10 && items.size() < 5)
				items.add(ie.getStack().getName().getString() + " x" + ie.getStack().getCount() + " dist " + Math.round(p.distanceTo(e)));
		}
		o.add("dropped_items", items);
		return o;
	}
}

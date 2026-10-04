package com.aiplay.ai.actions;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Smooth head rotation helper. */
public final class Aim {
	private Aim() {}

	public static float yawTo(Vec3d from, Vec3d to) {
		return (float) (Math.atan2(to.z - from.z, to.x - from.x) * 180.0 / Math.PI) - 90f;
	}

	public static float pitchTo(Vec3d from, Vec3d to) {
		double dx = to.x - from.x, dz = to.z - from.z, dy = to.y - from.y;
		return (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180.0 / Math.PI);
	}

	/** Rotates towards (yaw,pitch) by at most the given steps. Returns true when aligned. */
	public static boolean rotate(ClientPlayerEntity p, float yaw, float pitch, float yawStep, float pitchStep) {
		float dy = MathHelper.wrapDegrees(yaw - p.getYaw());
		float dp = pitch - p.getPitch();
		p.setYaw(p.getYaw() + MathHelper.clamp(dy, -yawStep, yawStep));
		p.setPitch(MathHelper.clamp(p.getPitch() + MathHelper.clamp(dp, -pitchStep, pitchStep), -90f, 90f));
		return Math.abs(dy) < 6f && Math.abs(dp) < 8f;
	}

	public static boolean face(ClientPlayerEntity p, Vec3d target) {
		Vec3d eye = p.getEyePos();
		return rotate(p, yawTo(eye, target), pitchTo(eye, target), 38f, 28f);
	}
}

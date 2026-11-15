package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Red Skull on the Temple of Soul's summit, and the dimmed, ashen light (Vormir) that lies over the land round the
 * mountain. Both last until the Soul Stone has been taken from the temple's quest: then he departs and the colour comes
 * back. The server only decides when; the clients are told the site and whether it still holds.
 */
public final class RedSkullKeeper {
	/** Set once, for good, when the Soul Stone has been taken. */
	private static final AttachmentType<Boolean> DONE = AttachmentRegistry.<Boolean>builder()
			.persistent(Codec.BOOL).buildAndRegister(InfinityGauntlet.id("red_skull_done"));
	private static boolean sentDone;
	private static BlockPos sentSite;
	private static boolean sentOnce;

	private RedSkullKeeper() {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(RedSkullKeeper::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer(), server));
		// A stone released in an earlier session is long since taken (the quest does not survive a restart half way).
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (StoneRegistry.released(server, Stone.SOUL)) server.overworld().setAttached(DONE, true);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> { sentOnce = false; sentSite = null; RedSkull.clearTalk(); });
	}

	public static boolean done(MinecraftServer server) { return Boolean.TRUE.equals(server.overworld().getAttached(DONE)); }

	private static void send(ServerPlayer player, MinecraftServer server) {
		ServerPlayNetworking.send(player, new ModNetwork.SoulVormir(SoulTemple.site(server), !done(server)));
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) return;
		ServerLevel level = server.overworld();
		boolean done = done(server);
		if (!done) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				var inv = p.getInventory();
				for (int i = 0; i < inv.getContainerSize(); i++) {
					if (StoneKeeper.stonesIn(inv.getItem(i)).contains(Stone.SOUL)) { level.setAttached(DONE, true); done = true; break; }
				}
				if (done) break;
			}
		}
		BlockPos site = SoulTemple.site(server);
		if (!sentOnce || sentDone != done || !site.equals(sentSite)) {
			sentOnce = true;
			sentDone = done;
			sentSite = site;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) send(p, server);
		}
		Vec3 stand = SoulTemple.summitStand(site);
		if (!level.isLoaded(BlockPos.containing(stand))) return;
		List<RedSkull> here = level.getEntitiesOfClass(RedSkull.class, new AABB(stand, stand).inflate(24, 14, 24));
		if (done) {
			for (RedSkull r : here) r.depart();
			return;
		}
		for (int i = 1; i < here.size(); i++) here.get(i).discard();
		if (!here.isEmpty() || !SoulTemple.built(server)) return;
		boolean near = false;
		for (ServerPlayer p : level.players()) if (p.distanceToSqr(stand) < 110 * 110) { near = true; break; }
		if (near) spawn(level, site, stand);
	}

	/** On the summit, a few steps to the side of where travellers arrive, turned toward that spot. */
	private static void spawn(ServerLevel level, BlockPos site, Vec3 stand) {
		double ux = (site.getX() + 0.5 - stand.x) / 4, uz = (site.getZ() + 0.5 - stand.z) / 4;
		double px = -uz, pz = ux;
		Vec3[] spots = {stand.add(px * 3, 0, pz * 3), stand.add(-px * 3, 0, -pz * 3), stand.add(-ux * 2, 0, -uz * 2), stand};
		for (Vec3 s : spots) {
			AABB box = new AABB(s.x - 0.5, s.y, s.z - 0.5, s.x + 0.5, s.y + 2.7, s.z + 0.5);
			if (!level.noCollision(box) || level.getBlockState(BlockPos.containing(s).below()).isAir()) continue;
			RedSkull skull = ModEntities.RED_SKULL.create(level, EntitySpawnReason.STRUCTURE);
			if (skull == null) return;
			float yaw = (float) Math.toDegrees(Math.atan2(-(stand.x - s.x), stand.z - s.z));
			skull.snapTo(s.x, s.y, s.z, yaw, 0);
			skull.yBodyRot = yaw;
			skull.yHeadRot = yaw;
			level.addFreshEntity(skull);
			return;
		}
	}
}

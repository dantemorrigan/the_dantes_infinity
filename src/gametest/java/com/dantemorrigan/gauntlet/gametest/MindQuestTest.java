package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.boss.Loki;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.stones.MindCity;
import com.dantemorrigan.gauntlet.stones.MindArena;
import com.dantemorrigan.gauntlet.stones.MindQuest;
import com.dantemorrigan.gauntlet.stones.MindZombie;
import com.dantemorrigan.gauntlet.stones.StoneRegistry;
import com.dantemorrigan.gauntlet.uru.GauntletDamage;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Smoke: Ruined City built, map unrolled, 10 sun-immune Mind Zombies, Loki’s portal and Fenrir transformation, illusions, both advancements, and a single Mind Stone reward. */
public class MindQuestTest implements FabricClientGameTest {
	private static net.minecraft.server.level.ServerPlayer player(net.minecraft.server.MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static void defeatPlayer(net.minecraft.server.level.ServerPlayer player, LivingEntity attacker) {
		player.removeEffect(MobEffects.RESISTANCE);
		player.invulnerableTime = 0;
		player.hurtServer(player.level(), player.damageSources().mobAttack(attacker), 1000);
		if (player.isAlive()) throw new AssertionError("The encounter did not defeat the player");
	}

	private static void respawn(ClientGameTestContext ctx) {
		ctx.waitFor(mc -> mc.player != null && mc.player.isDeadOrDying(), 80);
		ctx.runOnClient(mc -> mc.player.respawn());
		ctx.waitFor(mc -> mc.player != null && mc.player.isAlive(), 100);
	}

	private static void returnToArena(net.minecraft.server.MinecraftServer server, Vec3 stand) {
		var p = player(server);
		p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 1500, 4));
		p.teleportTo(stand.x, stand.y, stand.z);
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("time set 6000");
			server.runCommand("gamemode creative @p");
			server.runCommand("gauntlet stones reset");

			// Map unrolls into a filled map in hand.
			server.runOnServer(s -> {
				var p = player(s);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.QUEST_MIND_CITY_MAP));
				var res = p.getMainHandItem().use(p.level(), p, InteractionHand.MAIN_HAND);
				if (res instanceof net.minecraft.world.InteractionResult.Success suc && suc.heldItemTransformedTo() != null) {
					p.setItemInHand(InteractionHand.MAIN_HAND, suc.heldItemTransformedTo());
				}
			});
			if (!server.computeOnServer(s -> player(s).getMainHandItem().is(Items.FILLED_MAP))) {
				throw new AssertionError("Ruined City map did not unroll into a filled map");
			}

			// Teleport to the natural Ruined City site (without forceBuild) and verify chunks shape and build the city.
			var site = server.computeOnServer(MindCity::site);
			server.runCommand("tp @p " + (site.getX() + 0.5) + " " + (site.getY() + 10) + " " + (site.getZ() + MindCity.R_FLAT + 3.5) + " 180 18");
			ctx.waitTicks(45);
			sp.getClientLevel().waitForChunksRender();
			if (!server.computeOnServer(MindCity::built)) {
				throw new AssertionError("Ruined City was not built naturally on chunk load");
			}
			ctx.takeScreenshot("mind_ruined_city");

			server.runOnServer(s -> {
				if (!s.overworld().getEntitiesOfClass(MindZombie.class, new AABB(site).inflate(48)).isEmpty()) {
					throw new AssertionError("Mind Zombies appeared before a player entered the city");
				}
			});
			Vec3 stand = MindCity.entranceStand(site);
			server.runCommand("gamemode survival @p");
			server.runOnServer(s -> {
				var p = player(s);
				p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 800, 4));
				p.teleportTo(stand.x, stand.y, stand.z);
			});
			ctx.waitTicks(10);
			// Verify 10 Mind Zombies are present in the city and do not burn in midday sunlight.
			int zombieCount = server.computeOnServer(s -> {
				var level = s.overworld();
				List<MindZombie> list = level.getEntitiesOfClass(MindZombie.class, new AABB(site).inflate(MindCity.R_BLEND + 24.0), MindZombie::isAlive);
				for (MindZombie z : list) {
					if (z.isOnFire()) throw new AssertionError("Mind Zombie should not burn in sunlight");
				}
				return list.size();
			});
			if (zombieCount != MindQuest.TOTAL_ZOMBIES) {
				throw new AssertionError("Expected 10 Mind Zombies in the Ruined City, found " + zombieCount);
			}

			server.runOnServer(s -> {
				var p = player(s);
				var level = s.overworld();
				var before = p.position();
				p.teleportTo(site.getX() + 40, before.y, before.z);
				if (!p.position().equals(before)) throw new AssertionError("Player teleported through the arena wall");
				p.teleportTo(before.x, site.getY() + 40, before.z);
				if (!p.position().equals(before)) throw new AssertionError("Player teleported above the arena");
				var nether = s.getLevel(net.minecraft.world.level.Level.NETHER);
				var exit = new net.minecraft.world.level.portal.TeleportTransition(nether, before, Vec3.ZERO, 0, 0,
						net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING);
				if (p.teleport(exit) != null || p.level() != level) throw new AssertionError("Player teleported out of the arena dimension");
				var zombie = level.getEntitiesOfClass(MindZombie.class, new AABB(site).inflate(48), MindZombie::isAlive).getFirst();
				if (zombie.hurtServer(level, level.damageSources().fall(), 1000f) || MindQuest.defeatedCount(s) != 0) {
					throw new AssertionError("Environmental damage bypassed the fight");
				}
			});
			ctx.takeScreenshot("mind_zombies");

			server.runCommand("spawnpoint @p " + BlockPos.containing(stand).getX() + " " + BlockPos.containing(stand).getY() + " " + BlockPos.containing(stand).getZ());
			server.runOnServer(s -> {
				var p = player(s);
				var level = s.overworld();
				var zombies = level.getEntitiesOfClass(MindZombie.class, new AABB(site).inflate(48), MindZombie::isAlive);
				zombies.getFirst().hurtServer(level, level.damageSources().playerAttack(p), 1000);
				if (MindQuest.defeatedCount(s) != 1) throw new AssertionError("Zombie wipe test did not get one kill");
				defeatPlayer(p, zombies.getLast());
			});
			respawn(ctx);
			ctx.waitTicks(45);
			server.runOnServer(s -> {
				if (MindArena.inside(site, player(s).position()) || MindQuest.defeatedCount(s) != 0
						|| !s.overworld().getEntitiesOfClass(MindZombie.class, new AABB(site).inflate(48), MindZombie::isAlive).isEmpty()) {
					throw new AssertionError("Zombie wipe did not respawn outside and reset the wave: player=" + player(s).position()
							+ ", defeated=" + MindQuest.defeatedCount(s) + ", zombies="
							+ s.overworld().getEntitiesOfClass(MindZombie.class, new AABB(site).inflate(48), MindZombie::isAlive).size());
				}
				returnToArena(s, stand);
			});
			ctx.waitTicks(12);
			System.out.println("MIND_ZOMBIE_RETRY_PASSED");

			// Defeat the first 9 Mind Zombies; verify progress is 9/10 and stone is not yet released.
			server.runOnServer(s -> {
				var p = player(s);
				var level = s.overworld();
				List<MindZombie> list = level.getEntitiesOfClass(MindZombie.class, new AABB(site).inflate(MindCity.R_BLEND + 24.0), MindZombie::isAlive);
				for (int i = 0; i < 9; i++) {
					MindZombie z = list.get(i);
					z.hurtServer(level, level.damageSources().playerAttack(p), 1000.0f);
				}
			});
			if (server.computeOnServer(MindQuest::defeatedCount) != 9) {
				throw new AssertionError("Expected 9 defeated Mind Zombies");
			}
			if (server.computeOnServer(s -> StoneRegistry.released(s, Stone.MIND))) {
				throw new AssertionError("Mind Stone should not be released before the 10th zombie is defeated");
			}

			// The 10th zombie opens Loki’s portal.
			server.runOnServer(s -> {
				var p = player(s);
				var level = s.overworld();
				List<MindZombie> list = level.getEntitiesOfClass(MindZombie.class, new AABB(site).inflate(MindCity.R_BLEND + 24.0), MindZombie::isAlive);
				if (list.size() != 1) throw new AssertionError("Expected 1 remaining Mind Zombie, found " + list.size());
				list.getFirst().hurtServer(level, level.damageSources().playerAttack(p), 1000.0f);
			});

			AABB city = new AABB(site).inflate(48);
			ctx.waitTicks(28);
			ctx.takeScreenshot("loki_portal");

			ctx.waitTicks(42);
			var firstBossId = server.computeOnServer(s -> {
				var bosses = s.overworld().getEntitiesOfClass(Loki.class, city, e -> e.isAlive() && !e.isIllusion());
				if (bosses.size() != 1) throw new AssertionError("Expected exactly one Loki, found " + bosses.size());
				if (StoneRegistry.released(s, Stone.MIND)) throw new AssertionError("Loki's arrival already released the Mind Stone");
				return bosses.getFirst().getUUID();
			});
			server.runOnServer(s -> {
				var boss = s.overworld().getEntity(firstBossId);
				if (boss != null) player(s).lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, boss.getEyePosition());
			});
			ctx.waitTicks(2);
			ctx.takeScreenshot("loki_arrival");
			server.runOnServer(s -> defeatPlayer(player(s), (Loki) s.overworld().getEntity(firstBossId)));
			respawn(ctx);
			ctx.waitTicks(48);
			server.runOnServer(s -> {
				if (MindArena.inside(site, player(s).position()) || MindQuest.defeatedCount(s) != 10
						|| !s.overworld().getEntitiesOfClass(Loki.class, city, Loki::isAlive).isEmpty()) {
					throw new AssertionError("Loki wipe did not wait outside for a fresh boss");
				}
				returnToArena(s, stand);
			});
			ctx.waitTicks(72);
			var bossId = server.computeOnServer(s -> {
				var level = s.overworld();
				var bosses = level.getEntitiesOfClass(Loki.class, city, e -> e.isAlive() && !e.isIllusion());
				if (bosses.size() != 1 || bosses.getFirst().getUUID().equals(firstBossId)
						|| bosses.getFirst().getHealth() != bosses.getFirst().getMaxHealth()
						|| !level.getEntitiesOfClass(MindZombie.class, city, MindZombie::isAlive).isEmpty()) {
					throw new AssertionError("Loki retry did not return with full health and skip the zombie wave");
				}
				return bosses.getFirst().getUUID();
			});
			System.out.println("MIND_LOKI_RETRY_PASSED");

			server.runOnServer(s -> {
				var level = s.overworld();
				var p = player(s);
				Loki boss = (Loki) level.getEntity(bossId);
				var types = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
				var melee = level.damageSources().playerAttack(p);
				float max = boss.getMaxHealth();
				boss.hurtServer(level, melee, 5);
				float meleeDamage = max - boss.getHealth();
				if (meleeDamage <= 0) throw new AssertionError("Loki was still invulnerable after the arrival");
				boss.setHealth(max);
				boss.invulnerableTime = 0;
				boss.hurtServer(level, new DamageSource(types.getOrThrow(DamageTypes.ARROW), p), 5);
				if (max - boss.getHealth() <= meleeDamage) throw new AssertionError("Arrows did not deal extra damage to Loki");
				boss.setHealth(max);
				boss.invulnerableTime = 0;
				boss.hurtServer(level, new DamageSource(types.getOrThrow(DamageTypes.PLAYER_EXPLOSION), p), 5);
				if (max - boss.getHealth() <= meleeDamage) throw new AssertionError("Explosions did not deal extra damage to Loki");
				boss.setHealth(max);
				boss.invulnerableTime = 0;
				GauntletDamage.hurt(level, boss, p, melee, 5);
				if (max - boss.getHealth() <= meleeDamage) throw new AssertionError("Infinity Stone powers did not deal extra damage to Loki");
				if (!com.dantemorrigan.gauntlet.power.MindStone.isBoss(boss)) {
					throw new AssertionError("Mind and Soul targeting can disable the real Loki");
				}
				Vec3 plaza = MindCity.plazaStand(site);
				boss.setPos(plaza);
				p.teleportTo(plaza.x, plaza.y + 1, plaza.z + 8);
				boss.setHealth(max);
			});
			boolean roofAttack = false;
			for (int i = 0; i < 360; i += 4) {
				int state = server.computeOnServer(s -> {
					Loki boss = (Loki) s.overworld().getEntity(bossId);
					if (boss.attack() == Loki.Attack.BARRAGE && boss.strikeTicks() > 0 && boss.getY() >= site.getY() + 4) return 1;
					return boss.getY() < site.getY() + 3 && boss.distanceToSqr(player(s)) < 36 ? 2 : 0;
				});
				if (state == 1) roofAttack = true;
				if (state == 2 && roofAttack) break;
				if (i == 356) throw new AssertionError("Loki did not complete a rooftop barrage cycle; cast observed: " + roofAttack);
				ctx.waitTicks(4);
			}
			System.out.println("MIND_LOKI_BARRAGE_PASSED");
			server.runOnServer(s -> {
				Loki boss = (Loki) s.overworld().getEntity(bossId);
				Vec3 plaza = MindCity.plazaStand(site);
				boss.setPos(plaza);
				player(s).teleportTo(plaza.x, plaza.y + 1, plaza.z + 8);
				boss.setHealth(boss.getMaxHealth() * .4f);
			});
			ctx.waitFor(mc -> {
				var entity = mc.level.getEntitiesOfClass(Loki.class, city, e -> !e.isIllusion()).stream().findFirst();
				return entity.filter(e -> e.transformationTicks() > 0 && !e.isEnraged()).isPresent();
			}, 80);
			ctx.takeScreenshot("loki_transformation");
			ctx.waitFor(mc -> mc.level.getEntitiesOfClass(Loki.class, city, e -> e.isEnraged()
					&& e.transformationTicks() > 0 && e.getBbHeight() >= 3.5f).size() == 1, 100);
			for (int i = 0; i < 170; i += 4) {
				if (server.computeOnServer(s -> player(s).hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS))) break;
				ctx.waitTicks(4);
			}
			server.runOnServer(s -> {
				Loki boss = (Loki) s.overworld().getEntity(bossId);
				if (!boss.isEnraged() || boss.getBbHeight() < 3.5f) throw new AssertionError("Loki did not become a giant Fenrir");
				var p = player(s);
				if (!p.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS)) throw new AssertionError("Fenrir’s roar did not stun the fighter");
				p.removeEffect(net.minecraft.world.effect.MobEffects.BLINDNESS);
				p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, boss.getEyePosition());
				var copies = s.overworld().getEntitiesOfClass(Loki.class, city, e -> e.isAlive() && e.isIllusion());
				if (copies.isEmpty()) throw new AssertionError("Enraged Loki did not summon an illusion");
				Loki copy = copies.getFirst();
				copy.hurtServer(s.overworld(), s.overworld().damageSources().playerAttack(player(s)), 1f);
				if (copy.isAlive()) throw new AssertionError("Loki's illusion survived one hit");
				var advancement = s.getAdvancements().get(InfinityGauntlet.id("shatter_loki_illusion"));
				if (advancement == null || !player(s).getAdvancements().getOrStartProgress(advancement).isDone()) {
					throw new AssertionError("Shattering an illusion did not award its advancement");
				}
			});
			ctx.takeScreenshot("loki_rage");

			server.runOnServer(s -> {
				var level = s.overworld();
				Loki boss = (Loki) level.getEntity(bossId);
				Vec3 away = player(s).position().subtract(boss.position()).multiply(1, 0, 1).normalize().scale(7);
				Vec3 viewer = boss.position().add(away).add(0, 2, 0);
				player(s).teleportTo(viewer.x, viewer.y, viewer.z);
				player(s).lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, boss.getEyePosition());
				// A finishing blow of the gauntlet; ordinary weapons are capped per hit, so 1000 from a sword is not a real scenario.
				boss.hurtByGauntlet(level, level.damageSources().playerAttack(player(s)), 1000f);
				if (boss.isAlive()) throw new AssertionError("Loki survived the finishing hit");
			});
			ctx.waitFor(mc -> mc.level.getEntitiesOfClass(Loki.class, city, e -> e.isDeadOrDying()
					&& !e.isEnraged() && e.deathTime >= 42).size() == 1, 110);
			ctx.takeScreenshot("loki_defeat_kneel");
			server.runOnServer(s -> {
				if (StoneRegistry.released(s, Stone.MIND)) throw new AssertionError("Stone released during the defeat cinematic");
			});
			ctx.waitFor(mc -> mc.level.getEntitiesOfClass(Loki.class, city, e -> !e.isIllusion()).isEmpty(), 150);
			server.runOnServer(s -> {
				if (s.overworld().getEntitiesOfClass(ExperienceOrb.class, city, orb -> orb.getValue() >= 73).isEmpty()) {
					throw new AssertionError("Loki did not drop high-value XP orbs after the defeat scene");
				}
			});
			ctx.waitTicks(35);
			ctx.takeScreenshot("loki_reward");
			server.runOnServer(s -> {
				if (StoneRegistry.released(s, Stone.MIND) || player(s).getInventory().countItem(ModItems.MIND_STONE) != 0) {
					throw new AssertionError("The stone was awarded before its reveal animation finished");
				}
			});
			for (int i = 0; i < 100 && !server.computeOnServer(s -> StoneRegistry.released(s, Stone.MIND)); i += 4) ctx.waitTicks(4);
			server.runOnServer(s -> {
				if (!StoneRegistry.released(s, Stone.MIND)) throw new AssertionError("Defeating Loki did not release the Mind Stone");
				var stones = s.overworld().getEntitiesOfClass(ItemEntity.class, city, item -> item.getItem().is(ModItems.MIND_STONE));
				if (!stones.isEmpty() || !player(s).getInventory().contains(item -> item.is(ModItems.MIND_STONE))) {
					throw new AssertionError("Expected the arena to deliver one Mind Stone to the fighter");
				}
				player(s).teleportTo(site.getX() + 40, site.getY() + 5, site.getZ() + .5);
				if (player(s).getX() < site.getX() + 39) throw new AssertionError("Barrier stayed closed after the reward");
				var advancement = s.getAdvancements().get(InfinityGauntlet.id("defeat_loki"));
				if (advancement == null || !player(s).getAdvancements().getOrStartProgress(advancement).isDone()) {
					throw new AssertionError("Defeating Loki did not award its advancement");
				}
			});
			ctx.waitTicks(80);
			if (server.computeOnServer(s -> !s.overworld().getEntitiesOfClass(Loki.class, city, Loki::isAlive).isEmpty())) {
				throw new AssertionError("Loki or his illusions remained after the encounter");
			}
			server.runOnServer(s -> player(s).teleportTo(stand.x, stand.y, stand.z));
			server.runCommand("gauntlet loki reset");
			ctx.waitTicks(105);
			server.runOnServer(s -> {
				var level = s.overworld();
				var bosses = level.getEntitiesOfClass(Loki.class, city, e -> e.isAlive() && !e.isIllusion());
				if (bosses.size() != 1) throw new AssertionError("Reset did not bring back one city Loki");
				Loki replay = bosses.getFirst();
				var source = level.damageSources().playerAttack(player(s));
				replay.setHealth(replay.getMaxHealth() * .45f + .25f);
				replay.hurtByGauntlet(level, source, 1000f);
				float phaseHealth = replay.getHealth();
				if (phaseHealth > replay.getMaxHealth() * .45f) throw new AssertionError("Hit did not reach the Fenrir transition");
				for (int i = 0; i < 12; i++) {
					replay.invulnerableTime = 0;
					replay.hurtByGauntlet(level, source, 1000f);
				}
				if (!replay.isAlive() || replay.getHealth() != phaseHealth) {
					throw new AssertionError("A burst bypassed the Fenrir transition");
				}
			});
			ctx.waitFor(mc -> mc.level.getEntitiesOfClass(Loki.class, city,
					e -> e.isEnraged() && e.transformationTicks() == 0).size() == 1, 130);
			server.runOnServer(s -> {
				var level = s.overworld();
				Loki replay = level.getEntitiesOfClass(Loki.class, city, e -> e.isAlive() && !e.isIllusion()).getFirst();
				replay.invulnerableTime = 0;
				replay.hurtByGauntlet(level, level.damageSources().playerAttack(player(s)), 1000f);
				if (replay.isAlive()) throw new AssertionError("City replay could not be defeated in the Fenrir phase");
			});
			ctx.waitTicks(98);
			server.runOnServer(s -> {
				var level = s.overworld();
				if (!level.getEntitiesOfClass(Loki.class, city, Loki::isAlive).isEmpty()
						|| !level.getEntitiesOfClass(ItemEntity.class, city, item -> item.getItem().is(ModItems.MIND_STONE)).isEmpty()
						|| player(s).getInventory().countItem(ModItems.MIND_STONE) != 1) {
					throw new AssertionError("City replay produced another Loki or Mind Stone");
				}
			});

			server.runCommand("execute in minecraft:the_nether run forceload add 0 0");
			BlockPos netherSpawn = new BlockPos(0, 135, 0);
			for (int i = 0; i < 200 && !server.computeOnServer(s -> s.getLevel(Level.NETHER).isPositionEntityTicking(netherSpawn)); i += 10) {
				ctx.waitTicks(10);
			}
			server.runCommand("execute in minecraft:the_nether positioned 0.5 135 0.5 run gauntlet loki spawn");
			server.runOnServer(s -> {
				var nether = s.getLevel(Level.NETHER);
				var bosses = nether.getEntitiesOfClass(Loki.class, new AABB(netherSpawn).inflate(5), Loki::isAlive);
				if (bosses.size() != 1 || MindQuest.isCurrentBoss(bosses.getFirst())) {
					throw new AssertionError("Spawn command did not create a standalone Loki in the Nether");
				}
			});
			InfinityGauntlet.LOGGER.info("Loki quest smoke passed");
		}
	}
}

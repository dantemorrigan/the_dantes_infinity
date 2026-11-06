package com.dantemorrigan.gauntlet.client.stormbreaker;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModComponents;
import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.client.celestial.CelestialSky;
import com.dantemorrigan.gauntlet.client.cutscene.Cutscene;
import com.dantemorrigan.gauntlet.client.fx.ClientPower;
import com.dantemorrigan.gauntlet.client.fx.FxParticle;
import com.dantemorrigan.gauntlet.client.fx.TimeRewindClient;
import com.dantemorrigan.gauntlet.client.fx.VfxPresets;
import com.dantemorrigan.gauntlet.stormbreaker.StormFx;
import com.dantemorrigan.gauntlet.stormbreaker.StormNetwork;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

public final class StormbreakerClient {
    private static final KeyMapping THROW = key("throw", GLFW.GLFW_KEY_X);
    private static final KeyMapping WRATH = key("wrath", GLFW.GLFW_KEY_H);
    private static final KeyMapping FLIGHT = key("flight", GLFW.GLFW_KEY_J);
    private static final KeyMapping RECALL = key("recall", GLFW.GLFW_KEY_K);
    private static final Map<Integer, Animation> ANIMATIONS = new HashMap<>();
    private static final Map<String, Long> COOLDOWNS = new HashMap<>();
    private static ClientLevel level;
    private static boolean primaryActive, flightDown, recallDown;
    private static JsonObject clips;
    private static Storm storm;

    private record Animation(String clip, long start, int length) {}
    private record Storm(int owner, Vec3 center, long start) {}
    public record Pose(Vec3 rotation, Vec3 translation) {}

    public static void init() {
        EntityRendererRegistry.register(ModEntities.STORMBREAKER, StormbreakerRenderer::new);
        for (StormFx fx : StormFx.values()) register(fx);
        ClientPlayNetworking.registerGlobalReceiver(StormNetwork.Visual.TYPE, (packet, context) -> receive(packet));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> reset(null));
        ClientPreAttackCallback.EVENT.register((mc, player, clicks) -> {
            if (!canUse(mc) || !player.getMainHandItem().is(ModItems.STORMBREAKER)) return false;
            if (clicks > 0 && !primaryActive) {
                primaryActive = true;
                send(StormNetwork.PRIMARY_START);
            }
            return true;
        });
        ClientTickEvents.END_CLIENT_TICK.register(StormbreakerClient::tick);
        HudElementRegistry.addLast(InfinityGauntlet.id("stormbreaker"), (g, delta) -> hud(g));
    }

    private static KeyMapping key(String name, int code) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.infinity_gauntlet.stormbreaker_" + name,
                InputConstants.Type.KEYSYM, code, ClientPower.CATEGORY));
    }

    private static void register(StormFx fx) {
        var spec = new VfxPresets.Spec(fx.frames, fx.ticksPerFrame, fx.frames * fx.ticksPerFrame, false, 1,
                fx != StormFx.BLOCK_SHARDS, VfxPresets.Orientation.CAMERA);
        ParticleProviderRegistry.getInstance().register(fx.particle, sprites -> (type, world, x, y, z, dx, dy, dz, random) -> {
            Vector3f direction = new Vector3f((float) dx, (float) dy, (float) dz);
            float size = direction.length();
            if (size < .001f) { size = 1; direction.set(0, 1, 0); }
            direction.normalize();
            var particle = new FxParticle(world, x, y + (fx.pivotY - .5f) * size, z, sprites, spec, size,
                    null, -1, null, 1f) {
                @Override
                public FacingCameraMode getFacingCameraMode() {
                    return switch (fx.facing) {
                        case VERTICAL -> (q, camera, partial) -> {
                            Vector3f right = new Vector3f(1, 0, 0).rotate(camera.rotation());
                            q.rotationY((float) Math.atan2(-right.z, right.x));
                        };
                        case DIRECTED -> (q, camera, partial) -> q.rotationY((float) Math.atan2(direction.x, direction.z));
                        case FLAT -> (q, camera, partial) -> q.rotationX(-Mth.HALF_PI);
                        default -> super.getFacingCameraMode();
                    };
                }
            };
            return fx.facing == StormFx.Facing.TRAIL ? particle.alignX(direction) : particle;
        });
    }

    private static void reset(ClientLevel next) {
        level = next;
        storm = null;
        primaryActive = false;
        flightDown = FLIGHT.isDown();
        recallDown = RECALL.isDown();
        ANIMATIONS.clear();
        COOLDOWNS.clear();
        clips = null;
    }

    private static void tick(Minecraft mc) {
        if (level != mc.level) reset(mc.level);
        if (level == null || mc.player == null) return;
        long now = level.getGameTime();
        ANIMATIONS.values().removeIf(a -> now - a.start > a.length);
        if (storm != null && now - storm.start > 113) storm = null;
        boolean controls = canUse(mc);
        boolean held = controls && mc.player.getMainHandItem().is(ModItems.STORMBREAKER);
        if (consume(THROW) && held) send(StormNetwork.THROW);
        if (consume(WRATH) && held) send(StormNetwork.WRATH);
        boolean flightClick = consume(FLIGHT);
        boolean recallClick = consume(RECALL);
        if (!flightDown && flightClick && held) send(StormNetwork.FLIGHT);
        if (!recallDown && recallClick && controls) send(StormNetwork.RECALL);
        flightDown = FLIGHT.isDown();
        recallDown = RECALL.isDown();
        if (primaryActive && (!held || !mc.options.keyAttack.isDown())) {
            primaryActive = false;
            if (ClientPlayNetworking.canSend(StormNetwork.Action.TYPE)) {
                send(held ? StormNetwork.PRIMARY_RELEASE : StormNetwork.PRIMARY_CANCEL);
            }
        }
    }

    private static boolean canUse(Minecraft mc) {
        return mc.player != null && mc.player.isAlive() && !mc.player.isSpectator() && mc.screen == null && mc.isWindowActive()
                && !Cutscene.inputLocked() && !TimeRewindClient.inputLocked()
                && ClientPlayNetworking.canSend(StormNetwork.Action.TYPE);
    }

    private static void send(int action) {
        ClientPlayNetworking.send(new StormNetwork.Action(action));
    }

    private static boolean consume(KeyMapping key) {
        boolean pressed = false;
        while (key.consumeClick()) pressed = true;
        return pressed;
    }

    private static void receive(StormNetwork.Visual packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (level != mc.level) reset(mc.level);
        long start = level.getGameTime() - packet.elapsed();
        if ("stop".equals(packet.clip())) {
            if (storm != null && storm.owner == packet.entity()) storm = null;
            ANIMATIONS.remove(packet.entity());
            return;
        }
        if ("ground_cancel".equals(packet.clip())) {
            Animation animation = ANIMATIONS.get(packet.entity());
            if (animation != null && "ground_charge".equals(animation.clip)) ANIMATIONS.remove(packet.entity());
            return;
        }
        JsonObject clip = clip(packet.clip());
        if (clip == null) return;
        if ("heavens_wrath".equals(packet.clip())) {
            storm = new Storm(packet.entity(), packet.origin(), start);
            if (packet.elapsed() != 0) return;
        }
        if (clip.has("keyframes")) ANIMATIONS.put(packet.entity(), new Animation(packet.clip(), start, clip.get("duration_ticks").getAsInt()));
        if (mc.player != null && packet.entity() == mc.player.getId()) {
            switch (packet.clip()) {
                case "heavens_wrath" -> COOLDOWNS.put("wrath", start + 440);
                case "lightning_cast" -> COOLDOWNS.put("lightning", start + 42);
                case "ground_slam" -> COOLDOWNS.put("ground_wave", start + 65);
                case "throw_release" -> COOLDOWNS.put("throw", start + 50);
                default -> { }
            }
        }
    }

    private static JsonObject clip(String name) {
        if (clips == null) {
            JsonObject data = VfxPresets.readJson(InfinityGauntlet.id("stormbreaker/visual_clips.json"));
            clips = data == null ? new JsonObject() : data.getAsJsonObject("clips");
        }
        return clips.getAsJsonObject(name);
    }

    public static boolean isPulling(int entity) {
        Animation animation = ANIMATIONS.get(entity);
        return animation != null && "reclaim_hand".equals(animation.clip);
    }

    public static Pose pose(int entity, float partial) {
        if (level == null) return null;
        Animation animation = ANIMATIONS.get(entity);
        if (animation == null) return null;
        float age = level.getGameTime() - animation.start + partial;
        if (age < 0 || age > animation.length) return null;
        JsonArray frames = clip(animation.clip).getAsJsonArray("keyframes");
        if (frames.isEmpty()) return null;
        JsonObject last = frames.get(0).getAsJsonObject();
        for (int i = 1; i < frames.size(); i++) {
            JsonObject next = frames.get(i).getAsJsonObject();
            float to = next.get("tick").getAsFloat();
            if (age <= to) {
                float from = last.get("tick").getAsFloat();
                float fraction = Mth.clamp((age - from) / (to - from), 0, 1);
                if (animation.clip.startsWith("combo_")) fraction *= fraction * (3 - 2 * fraction);
                return new Pose(vec(last, "rotation_xyz_deg").lerp(vec(next, "rotation_xyz_deg"), fraction),
                        vec(last, "translation_xyz_blocks").lerp(vec(next, "translation_xyz_blocks"), fraction));
            }
            last = next;
        }
        return new Pose(vec(last, "rotation_xyz_deg"), vec(last, "translation_xyz_blocks"));
    }

    private static Vec3 vec(JsonObject frame, String key) {
        JsonArray a = frame.getAsJsonArray(key);
        return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }

    public static void firstPerson(PoseStack stack, int entity, float partial, boolean left) {
        Pose motion = pose(entity, partial);
        if (motion == null) return;
        Vec3 t = motion.translation.scale(.57);
        Vec3 r = motion.rotation;
        float side = left ? -1 : 1;
        stack.translate(side * t.x, t.y, -t.z);
        stack.mulPose(Axis.XP.rotationDegrees((float) r.x * .55f));
        stack.mulPose(Axis.YP.rotationDegrees((float) r.y * .48f * side));
        stack.mulPose(Axis.ZP.rotationDegrees((float) r.z * .5f * side));
    }

    public static float skyFactor(Vec3 camera, float partial) {
        if (level == null || storm == null) return 0;
        float age = level.getGameTime() - storm.start + partial;
        return Mth.clamp(Math.min(age / 24, (112 - age) / 26), 0, 1)
                * (float) Mth.clamp((112 - camera.distanceTo(storm.center)) / 45, 0, 1);
    }

    public static float activeSky(float partial) {
        return skyFactor(Minecraft.getInstance().gameRenderer.getMainCamera().position(), partial);
    }

    public static void drawClouds(PoseStack pose, float partial) {
        float factor = activeSky(partial);
        if (factor < .01f) return;
        float age = level.getGameTime() - storm.start + partial;
        float flash = 0;
        for (int strike : new int[]{36, 50, 65, 83}) {
            float since = age - strike;
            if (since >= 0 && since < 5) flash = Math.max(flash, 1 - since / 5);
        }
        drawCloudDome(pose, 152, age * .00048f, 0, factor, .62f + flash * .38f);
        drawCloudDome(pose, 128, -age * .00032f, .57f, factor * .54f, .58f + flash * .42f);
    }

    private static void drawCloudDome(PoseStack pose, float radius, float drift, float rotation, float alpha, float light) {
        var texture = InfinityGauntlet.id("textures/environment/stormbreaker_clouds.png");
        int color = ARGB.colorFromFloat(alpha, light * .82f, light * .94f, light);
        CelestialSky.draw(pose, texture, buffer -> {
            var matrix = pose.last().pose();
            // Concentric strips close at radius zero. Planar UVs stay continuous across the entire cap.
            for (int ring = 0; ring < 6; ring++) {
                float inner = ring / 6f, outer = (ring + 1) / 6f;
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.TAU / 24, b = (i + 1) * Math.TAU / 24;
                    cloudVertex(buffer, matrix, radius, outer, a, drift, rotation, color);
                    cloudVertex(buffer, matrix, radius, outer, b, drift, rotation, color);
                    cloudVertex(buffer, matrix, radius, inner, b, drift, rotation, color);
                    cloudVertex(buffer, matrix, radius, inner, a, drift, rotation, color);
                    cloudVertex(buffer, matrix, radius, inner, a, drift, rotation, color);
                    cloudVertex(buffer, matrix, radius, inner, b, drift, rotation, color);
                    cloudVertex(buffer, matrix, radius, outer, b, drift, rotation, color);
                    cloudVertex(buffer, matrix, radius, outer, a, drift, rotation, color);
                }
            }
        });
    }

    private static void cloudVertex(com.mojang.blaze3d.vertex.BufferBuilder buffer, org.joml.Matrix4f matrix,
            float radius, float distance, double angle, float drift, float rotation, int color) {
        float x = distance * (float) Math.cos(angle), z = distance * (float) Math.sin(angle);
        float y = (float) Math.sqrt(1 - distance * distance);
        float u = .5f + distance * .42f * (float) Math.cos(angle + rotation) + drift;
        float v = .5f + distance * .42f * (float) Math.sin(angle + rotation);
        buffer.addVertex(matrix, x * radius, y * radius * .84f - 11, z * radius).setUv(u, v).setColor(color);
    }

    private static void hud(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || level == null || mc.screen != null || mc.options.hideGui
                || !mc.player.getMainHandItem().is(ModItems.STORMBREAKER)) return;
        boolean bound = mc.player.getMainHandItem().has(ModComponents.STORMBREAKER_SEAL);
        String[] icons = {"combo", "ground_wave", "lightning", "throw", "wrath", "flight", "guard"};
        String[] labels = {"", "", "", THROW.getTranslatedKeyMessage().getString(),
                WRATH.getTranslatedKeyMessage().getString(), FLIGHT.getTranslatedKeyMessage().getString(), ""};
        int x = g.guiWidth() - 132, y = g.guiHeight() - 45;
        g.fill(x - 4, y - 2, x + 126, y + 27, 0xC30A1729);
        Animation charge = ANIMATIONS.get(mc.player.getId());
        if (charge != null && "ground_charge".equals(charge.clip)) {
            float fill = Mth.clamp((level.getGameTime() - charge.start)
                    / (float) com.dantemorrigan.gauntlet.stormbreaker.Stormbreaker.GROUND_MAX_CHARGE, 0, 1);
            g.fill(x - 4, y - 5, x + 126, y - 2, 0xE021394E);
            g.fill(x - 4, y - 5, x - 4 + Math.round(130 * fill), y - 2, 0xFF83E4FF);
        }
        for (int i = 0; i < icons.length; i++) {
            String id = icons[i];
            boolean cooldown = (bound || i == icons.length - 1) && level.getGameTime() < COOLDOWNS.getOrDefault(id, 0L);
            String state = cooldown || !bound && i == icons.length - 1 ? "_cooldown" : "_ready";
            g.blit(RenderPipelines.GUI_TEXTURED, InfinityGauntlet.id("textures/gui/sprites/stormbreaker/" + id + state + ".png"),
                    x + i * 18, y, 0, 0, 16, 16, 24, 24, 24, 24);
            if (!labels[i].isEmpty()) g.text(mc.font, labels[i], x + i * 18 + 5, y + 17, 0xFFC4E8FF, true);
        }
    }

    private StormbreakerClient() {}
}

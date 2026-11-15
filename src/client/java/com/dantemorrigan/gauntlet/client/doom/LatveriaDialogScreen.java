package com.dantemorrigan.gauntlet.client.doom;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.doom.LatveriaNpc;
import com.dantemorrigan.gauntlet.doom.LatveriaQuests;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Interactive RPG dialog screen for Latveria's NPCs and townsfolk, featuring NPC skin portrait rendering,
 * typewriter text animation with voice murmur audio cues, and branching quest choices.
 */
public final class LatveriaDialogScreen extends Screen {
	private static final Identifier EMBLEM = InfinityGauntlet.id("textures/gui/doctor_doom/latveria_emblem.png");

	private final int entityId;
	private final int role;
	private int flags;
	private String nodeId;
	private int revealTick;
	private int totalChars;
	private final List<Option> currentOptions = new ArrayList<>();

	private record Option(Component label, String nextNode, int serverAction, boolean enabled) {}

	public LatveriaDialogScreen(int entityId, int role, int flags) {
		super(speakerName(role));
		this.entityId = entityId;
		this.role = role;
		this.flags = flags;
		this.nodeId = (flags & LatveriaQuests.FLAG_CONQUERED) != 0 ? "king" : "root";
	}

	public void updateState(int newFlags) {
		this.flags = newFlags;
		if (role == LatveriaNpc.ROLE_MECHANIC && (flags & LatveriaQuests.FLAG_HAS_DISRUPTOR) != 0 && "plan".equals(nodeId)) {
			this.nodeId = "given";
		} else if (role == LatveriaNpc.ROLE_TAVERN_KEEPER && (flags & LatveriaQuests.FLAG_HAS_CLOAK) != 0 && "cloak_offer".equals(nodeId)) {
			this.nodeId = "cloak_given";
		}
		this.revealTick = 0;
		rebuildWidgets();
	}

	private static Component speakerName(int role) {
		return switch (role) {
			case LatveriaNpc.ROLE_MECHANIC -> Component.translatable("dialog.infinity_gauntlet.npc.mechanic.name");
			case LatveriaNpc.ROLE_TAVERN_KEEPER -> Component.translatable("dialog.infinity_gauntlet.npc.tavern.name");
			default -> Component.translatable("dialog.infinity_gauntlet.npc.citizen.name");
		};
	}

	private Component speakerSubtitle() {
		if ((flags & LatveriaQuests.FLAG_CONQUERED) != 0) {
			return Component.translatable("dialog.infinity_gauntlet.npc.subtitle.subject");
		}
		return switch (role) {
			case LatveriaNpc.ROLE_MECHANIC -> Component.translatable("dialog.infinity_gauntlet.npc.mechanic.subtitle");
			case LatveriaNpc.ROLE_TAVERN_KEEPER -> Component.translatable("dialog.infinity_gauntlet.npc.tavern.subtitle");
			default -> Component.translatable("dialog.infinity_gauntlet.npc.citizen.subtitle");
		};
	}

	private Component currentSpeech() {
		boolean conquered = (flags & LatveriaQuests.FLAG_CONQUERED) != 0;
		boolean genOff = (flags & LatveriaQuests.FLAG_GENERATOR_OFF) != 0;
		if (role == LatveriaNpc.ROLE_MECHANIC) {
			if (conquered) return Component.translatable("dialog.infinity_gauntlet.mechanic.king");
			return switch (nodeId) {
				case "who" -> Component.translatable("dialog.infinity_gauntlet.mechanic.who");
				case "plan" -> Component.translatable("dialog.infinity_gauntlet.mechanic.plan");
				case "given" -> Component.translatable("dialog.infinity_gauntlet.mechanic.given");
				case "sabotaged" -> Component.translatable("dialog.infinity_gauntlet.mechanic.sabotaged");
				default -> Component.translatable(genOff
						? "dialog.infinity_gauntlet.mechanic.sabotaged"
						: "dialog.infinity_gauntlet.mechanic.root");
			};
		}
		if (role == LatveriaNpc.ROLE_TAVERN_KEEPER) {
			if (conquered && "king".equals(nodeId)) return Component.translatable("dialog.infinity_gauntlet.tavern.king");
			return switch (nodeId) {
				case "cellar" -> Component.translatable("dialog.infinity_gauntlet.tavern.cellar");
				case "cloak_offer" -> Component.translatable("dialog.infinity_gauntlet.tavern.cloak_offer");
				case "cloak_given" -> Component.translatable("dialog.infinity_gauntlet.tavern.cloak_given");
				default -> Component.translatable("dialog.infinity_gauntlet.tavern.root");
			};
		}
		// Citizens
		int variant = Math.floorMod(role - LatveriaNpc.ROLE_CITIZEN_1, 4) + 1;
		if (conquered) {
			return Component.translatable("dialog.infinity_gauntlet.citizen.king." + ((variant % 2) + 1));
		}
		if ("hints".equals(nodeId)) {
			return Component.translatable("dialog.infinity_gauntlet.citizen.hints");
		}
		return Component.translatable("dialog.infinity_gauntlet.citizen.fear." + ((variant % 2) + 1));
	}

	private void buildOptions() {
		currentOptions.clear();
		boolean conquered = (flags & LatveriaQuests.FLAG_CONQUERED) != 0;
		boolean hasDisruptor = (flags & LatveriaQuests.FLAG_HAS_DISRUPTOR) != 0;
		boolean genOff = (flags & LatveriaQuests.FLAG_GENERATOR_OFF) != 0;
		boolean hasCloak = (flags & LatveriaQuests.FLAG_HAS_CLOAK) != 0;
		boolean hasWheat = (flags & LatveriaQuests.FLAG_HAS_WHEAT) != 0;

		if (role == LatveriaNpc.ROLE_MECHANIC) {
			if (conquered) {
				currentOptions.add(new Option(Component.translatable("dialog.infinity_gauntlet.opt.king_thanks"), "close", LatveriaQuests.ACTION_CITIZEN_BLESSING, true));
				return;
			}
			if (!"who".equals(nodeId)) {
				currentOptions.add(new Option(Component.translatable("dialog.infinity_gauntlet.mechanic.opt.who"), "who", LatveriaQuests.ACTION_TALK_SOUND, true));
			}
			if (!"plan".equals(nodeId) && !genOff) {
				currentOptions.add(new Option(Component.translatable("dialog.infinity_gauntlet.mechanic.opt.plan"), "plan", LatveriaQuests.ACTION_TALK_SOUND, true));
			}
			if (!hasDisruptor && !genOff) {
				currentOptions.add(new Option(Component.translatable("dialog.infinity_gauntlet.mechanic.opt.take_disruptor"), "given", LatveriaQuests.ACTION_CLAIM_DISRUPTOR, true));
			} else if (genOff && !"sabotaged".equals(nodeId)) {
				currentOptions.add(new Option(Component.translatable("dialog.infinity_gauntlet.mechanic.opt.sabotaged"), "sabotaged", LatveriaQuests.ACTION_TALK_SOUND, true));
			}
			currentOptions.add(new Option(Component.translatable("dialog.infinity_gauntlet.opt.leave"), "close", 0, true));
			return;
		}

		if (role == LatveriaNpc.ROLE_TAVERN_KEEPER) {
			if (conquered && "king".equals(nodeId)) {
				if (!hasCloak) {
					currentOptions.add(new Option(Component.translatable("dialog.infinity_gauntlet.tavern.opt.ask_cloak"), "cloak_offer", LatveriaQuests.ACTION_TALK_SOUND, true));
				}
				currentOptions.add(new Option(Component.translatable("dialog.infinity_gauntlet.opt.king_thanks"), "close", LatveriaQuests.ACTION_CITIZEN_BLESSING, true));
				return;
			}
			if (!"cellar".equals(nodeId)) {
				currentOptions.add(new Option(Component.translatable("dialog.infinity_gauntlet.tavern.opt.cellar"), "cellar", LatveriaQuests.ACTION_TALK_SOUND, true));
			}
			if (!"cloak_offer".equals(nodeId) && !hasCloak) {
				currentOptions.add(new Option(Component.translatable("dialog.infinity_gauntlet.tavern.opt.ask_cloak"), "cloak_offer", LatveriaQuests.ACTION_TALK_SOUND, true));
			}
			if (!hasCloak) {
				Component tradeLabel = Component.translatable(hasWheat
						? "dialog.infinity_gauntlet.tavern.opt.give_wheat"
						: "dialog.infinity_gauntlet.tavern.opt.need_wheat", LatveriaQuests.WHEAT_COST);
				currentOptions.add(new Option(tradeLabel, "cloak_given", LatveriaQuests.ACTION_TRADE_WHEAT_FOR_CLOAK, hasWheat));
			}
			currentOptions.add(new Option(Component.translatable("dialog.infinity_gauntlet.opt.leave"), "close", 0, true));
			return;
		}

		// Citizens
		if (conquered) {
			currentOptions.add(new Option(Component.translatable("dialog.infinity_gauntlet.opt.king_bless"), "close", LatveriaQuests.ACTION_CITIZEN_BLESSING, true));
		} else {
			if (!"hints".equals(nodeId)) {
				currentOptions.add(new Option(Component.translatable("dialog.infinity_gauntlet.citizen.opt.hints"), "hints", LatveriaQuests.ACTION_TALK_SOUND, true));
			}
			currentOptions.add(new Option(Component.translatable("dialog.infinity_gauntlet.opt.leave"), "close", 0, true));
		}
	}

	@Override
	protected void init() {
		buildOptions();
		String raw = currentSpeech().getString();
		totalChars = Math.max(1, raw.length());

		int panelW = Math.min(390, width - 24);
		int panelH = 176;
		int x0 = (width - panelW) / 2;
		int y0 = height - panelH - 12;

		int btnY = y0 + panelH - 8 - currentOptions.size() * 22;
		for (Option opt : currentOptions) {
			Button btn = Button.builder(opt.label, b -> selectOption(opt))
					.bounds(x0 + 14, btnY, panelW - 28, 20)
					.build();
			btn.active = opt.enabled;
			addRenderableWidget(btn);
			btnY += 22;
		}
	}

	private void selectOption(Option opt) {
		if (opt.serverAction != 0 && ClientPlayNetworking.canSend(ModNetwork.LatveriaDialogAction.TYPE)) {
			ClientPlayNetworking.send(new ModNetwork.LatveriaDialogAction(entityId, opt.serverAction));
		}
		if ("close".equals(opt.nextNode)) {
			onClose();
			return;
		}
		this.nodeId = opt.nextNode;
		this.revealTick = 0;
		playClientMurmur();
		rebuildWidgets();
	}

	@Override
	public void tick() {
		super.tick();
		int charsShown = revealTick * 3;
		if (charsShown < totalChars) {
			revealTick++;
			if (revealTick % 3 == 0) {
				playClientMurmur();
			}
		}
	}

	private void playClientMurmur() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		float basePitch = switch (role) {
			case LatveriaNpc.ROLE_MECHANIC -> 0.76f;
			case LatveriaNpc.ROLE_TAVERN_KEEPER -> 0.88f;
			default -> 1.0f;
		};
		float pitch = basePitch + (mc.player.getRandom().nextFloat() - 0.5f) * 0.14f;
		mc.player.playSound(SoundEvents.VILLAGER_AMBIENT, 0.35f, pitch);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		int panelW = Math.min(390, width - 24);
		int panelH = 176;
		int x0 = (width - panelW) / 2;
		int y0 = height - panelH - 12;
		int x1 = x0 + panelW;
		int y1 = y0 + panelH;

		// Outer gold/emerald border and dark glass background
		g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, 0xFF2E6B3F);
		g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFFD6AC3A);
		g.fill(x0, y0, x1, y1, 0xF20B120E);

		// Header bar
		g.fill(x0, y0, x1, y0 + 44, 0xCC132419);
		g.fill(x0, y0 + 44, x1, y0 + 45, 0xFF2E6B3F);

		// NPC Portrait box (32x32) rendered from their 64x64 player skin (face at u=8, v=8, 8x8 + hat at u=40, v=8, 8x8)
		int px = x0 + 12, py = y0 + 6;
		g.fill(px - 2, py - 2, px + 34, py + 34, 0xFFD6AC3A);
		g.fill(px - 1, py - 1, px + 33, py + 33, 0xFF18281E);
		Identifier skin = LatveriaNpcRenderer.skinForRole(role);
		g.blit(RenderPipelines.GUI_TEXTURED, skin, px, py, 8, 8, 32, 32, 8, 8, 64, 64, -1);
		g.blit(RenderPipelines.GUI_TEXTURED, skin, px, py, 40, 8, 32, 32, 8, 8, 64, 64, -1);

		// Speaker name, subtitle, and Latverian emblem
		g.text(font, title.copy().withStyle(s -> s.withBold(true)), px + 42, py + 6, 0xFFFFE08A, true);
		g.text(font, speakerSubtitle(), px + 42, py + 19, 0xFF9CFFB4, false);
		g.blit(RenderPipelines.GUI_TEXTURED, EMBLEM, x1 - 30, y0 + 12, 0, 0, 20, 20, 64, 64, 64, 64, -1);

		// Typewriter speech text
		String full = currentSpeech().getString();
		int shown = Mth.clamp(revealTick * 3, 1, full.length());
		Component visibleComp = Component.literal(full.substring(0, shown));
		List<FormattedCharSequence> lines = font.split(visibleComp, panelW - 28);
		int ty = y0 + 54;
		for (FormattedCharSequence line : lines) {
			g.text(font, line, x0 + 14, ty, 0xFFE6F0EA, false);
			ty += 11;
		}

		super.extractRenderState(g, mx, my, delta);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}

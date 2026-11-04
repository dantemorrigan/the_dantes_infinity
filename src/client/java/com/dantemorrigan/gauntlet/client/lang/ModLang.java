package com.dantemorrigan.gauntlet.client.lang;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The mod's own language switch. When the setting is not "auto", every translation key that belongs to the mod
 * is answered from the chosen language file (see ClientLanguageMixin); the rest of Minecraft keeps its language.
 */
public final class ModLang {
	public static final String AUTO = "auto";
	public static final String[] CODES = {AUTO, "en", "ru"};

	private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();

	/** Translation for a mod key in the chosen language, or {@code null} to let Minecraft answer. */
	public static String override(String key) {
		String code = GauntletConfig.get().language;
		if (code == null || AUTO.equals(code) || !key.contains("infinity_gauntlet")) return null;
		String file = "ru".equals(code) ? "ru_ru" : "en_us";
		String value = file(file).get(key);
		if (value == null && !"en_us".equals(file)) value = file("en_us").get(key);
		return value;
	}

	public static String current() {
		String c = GauntletConfig.get().language;
		return "en".equals(c) || "ru".equals(c) ? c : AUTO;
	}

	/** Saves the choice and makes already-built translatable components pick it up. */
	public static void set(String code) {
		GauntletConfig.get().language = code;
		GauntletConfig.save();
		RefreshedLanguage.refresh();
	}

	private static Map<String, String> file(String name) {
		return CACHE.computeIfAbsent(name, n -> {
			Map<String, String> map = new HashMap<>();
			try (var in = ModLang.class.getResourceAsStream("/assets/infinity_gauntlet/lang/" + n + ".json")) {
				if (in != null) {
					JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
					for (Map.Entry<String, JsonElement> e : json.entrySet()) map.put(e.getKey(), e.getValue().getAsString());
				}
			} catch (Exception ignored) {
				// fall back to Minecraft's own lookup
			}
			return map;
		});
	}

	private ModLang() {}
}

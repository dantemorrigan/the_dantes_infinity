package com.dantemorrigan.gauntlet.client.lang;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * Thin wrapper around the active language. Translatable components cache their text per Language instance,
 * so injecting a new instance makes every already-built component (book pages, tooltips…) re-translate.
 */
final class RefreshedLanguage extends Language {
	private final Language inner;

	RefreshedLanguage(Language inner) { this.inner = inner; }

	static void refresh() {
		Language current = Language.getInstance();
		Language.inject(new RefreshedLanguage(current instanceof RefreshedLanguage r ? r.inner : current));
	}

	@Override public String getOrDefault(String key, String fallback) { return inner.getOrDefault(key, fallback); }
	@Override public boolean has(String key) { return inner.has(key); }
	@Override public boolean isDefaultRightToLeft() { return inner.isDefaultRightToLeft(); }
	@Override public FormattedCharSequence getVisualOrder(FormattedText text) { return inner.getVisualOrder(text); }
}

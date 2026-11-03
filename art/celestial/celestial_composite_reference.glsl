// Reference helper for your custom renderer; NOT a standalone Minecraft core shader.
// Inputs are samples at identical UVs, original celestial texture + crack overlay.
vec4 compositeCelestialDamage(vec4 base, vec4 crack, float progressAlpha) {
    float brightness = max(base.r, max(base.g, base.b));
    float mask = base.a * clamp((brightness - 0.04) / 0.12, 0.0, 1.0);
    float a = crack.a * mask * clamp(progressAlpha, 0.0, 1.0);
    return vec4(mix(base.rgb, crack.rgb, a), base.a);
}

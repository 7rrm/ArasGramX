package tw.nekomimi.nekogram;

import static org.telegram.messenger.AndroidUtilities.dpf2;

/**
 * MeeroX: shared metrics for the six typing-status indicators.
 *
 * Telegram draws a different drawable for each activity - typing, recording
 * audio, sending a file, recording video, choosing a sticker, playing a game -
 * and each one picked its own stroke weight and opacity in isolation. Side by
 * side they do not look like one family: the record indicator strokes at 2dp
 * while the sticker one uses 0.8dp, and only some of them fade.
 *
 * iOS treats all of them as one lightweight glyph next to the name, thinner
 * than Android's and fading as it animates rather than holding full opacity.
 * Keeping the numbers here means changing that impression is a single edit
 * instead of six, and stops the six drifting apart again.
 *
 * Everything is expressed as a ratio of the drawable's own value, so each
 * indicator keeps its own proportions and only the weight changes.
 */
public class MeeroStatus {

    /** iOS strokes these lighter than Android's flat 2dp. */
    private static final float STROKE_SCALE = 0.75f;
    /** Dots read smaller on iOS than the 1.33x density Android uses. */
    private static final float DOT_SCALE = 0.88f;
    /** How far the trailing element of an animation fades out. */
    private static final float TRAIL_FADE = 0.22f;

    public static boolean enabled() {
        try {
            return NekoConfig.meeroIosWaveform.Bool();
        } catch (Throwable e) {
            return false;
        }
    }

    /** Stroke width for an indicator that would otherwise use {@code base}. */
    public static float stroke(float baseDp) {
        return dpf2(enabled() ? baseDp * STROKE_SCALE : baseDp);
    }

    /** Radius for a dot that would otherwise use {@code base}. */
    public static float dot(float baseRadius) {
        return enabled() ? baseRadius * DOT_SCALE : baseRadius;
    }

    /**
     * Alpha for element {@code index} of {@code count}, fading outwards.
     *
     * @param baseAlpha the alpha the drawable already decided on
     */
    public static int fade(int baseAlpha, int index, int count) {
        if (!enabled() || count <= 1) {
            return baseAlpha;
        }
        final float factor = Math.max(0f, 1f - TRAIL_FADE * index);
        return (int) (baseAlpha * factor);
    }
}

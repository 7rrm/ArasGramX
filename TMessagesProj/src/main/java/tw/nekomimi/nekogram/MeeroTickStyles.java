package tw.nekomimi.nekogram;

import org.telegram.messenger.R;

/**
 * MeeroX v93: the delivery-tick style tables, shared by Theme (which draws
 * the ticks inside chats) and by MeeroSettingsActivity (which previews them
 * in the picker dialog) - one list, so the two can never drift apart.
 *
 * Index 0 is the original v57 iOS artwork, byte-identical. 1..7 shipped in
 * v92; 8..15 are v93. Every style is a pair: the single sent mark and the
 * second mark of the read pair.
 */
public final class MeeroTickStyles {
    public static final int COUNT = 16;

    public static final int[] SINGLES = new int[]{
            R.drawable.ios_tick_check, R.drawable.meero_tick_heart,
            R.drawable.meero_tick_star, R.drawable.meero_tick_dot,
            R.drawable.meero_tick_sparkle, R.drawable.meero_tick_bolt,
            R.drawable.meero_tick_moon, R.drawable.meero_tick_gem,
            R.drawable.meero_tick_drop, R.drawable.meero_tick_bell,
            R.drawable.meero_tick_shield, R.drawable.meero_tick_infinity,
            R.drawable.meero_tick_paw, R.drawable.meero_tick_leaf,
            R.drawable.meero_tick_flame, R.drawable.meero_tick_plane,
    };

    public static final int[] SECONDS = new int[]{
            R.drawable.ios_tick_halfcheck, R.drawable.meero_tick_heart_half,
            R.drawable.meero_tick_star_half, R.drawable.meero_tick_dot_half,
            R.drawable.meero_tick_sparkle_half, R.drawable.meero_tick_bolt_half,
            R.drawable.meero_tick_moon_half, R.drawable.meero_tick_gem_half,
            R.drawable.meero_tick_drop_half, R.drawable.meero_tick_bell_half,
            R.drawable.meero_tick_shield_half, R.drawable.meero_tick_infinity_half,
            R.drawable.meero_tick_paw_half, R.drawable.meero_tick_leaf_half,
            R.drawable.meero_tick_flame_half, R.drawable.meero_tick_plane_half,
    };

    private MeeroTickStyles() {
    }
}

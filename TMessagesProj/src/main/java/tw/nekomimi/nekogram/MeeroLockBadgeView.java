package tw.nekomimi.nekogram;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

/**
 * MeeroX v108/v109: the gradient lock badge (glow ring + gradient disc +
 * white lock glyph), extracted from {@link MeeroCodeLockView} so other MeeroX
 * lock surfaces (v109: the hidden-chats vault header) share the exact same
 * lock identity. Colors are fully theme-accent driven, so it adapts to
 * light/dark themes automatically.
 */
public class MeeroLockBadgeView extends View {

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable lockDrawable;

    public MeeroLockBadgeView(Context context) {
        super(context);
        lockDrawable = context.getResources().getDrawable(R.drawable.baseline_lock_48).mutate();
        lockDrawable.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), size = Math.min(w, getHeight());
        float cx = w / 2f, cy = getHeight() / 2f;
        int accent = Theme.getColor(Theme.key_featuredStickers_addButton);

        // soft outer glow ring
        ringPaint.setShader(new RadialGradient(cx, cy, size / 2f,
                Theme.multAlpha(accent, 0.22f), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, size / 2f, ringPaint);

        // gradient disc
        float disc = size * 0.78f;
        fillPaint.setShader(new LinearGradient(cx - disc / 2f, cy - disc / 2f, cx + disc / 2f, cy + disc / 2f,
                accent, Theme.multAlpha(accent, 0.72f), Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, disc / 2f, fillPaint);

        // lock glyph centered
        int glyph = (int) (disc * 0.52f);
        int left = (int) (cx - glyph / 2f), top = (int) (cy - glyph / 2f);
        lockDrawable.setBounds(left, top, left + glyph, top + glyph);
        lockDrawable.draw(canvas);
    }
}

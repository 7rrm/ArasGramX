package tw.nekomimi.nekogram;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

/**
 * MeeroX v108: the on-screen 8-digit code lock ("شاشة رمز القفل").
 *
 * The user asked for the Turboteil-style unlock screen he screenshotted, but
 * nicer: an opaque full-screen surface with a gradient lock badge, title and
 * detail text, eight rounded digit boxes (accent-highlighted next box, red
 * flash + shake on a wrong code) and a built-in numeric keypad - no system
 * keyboard pops, so the locked content can never peek out behind it.
 *
 * One component serves every entry point (chat gate, hidden-chats vault and
 * the settings set/change flows) through {@link #setup} + {@link Delegate}.
 * Colors are fully theme-driven; layout forces LTR where the keypad digits
 * must keep 1-9 order on RTL locales.
 */
public class MeeroCodeLockView extends FrameLayout {

    public interface Delegate {
        /** Called once the 8th digit lands. true = accepted (host dismisses),
         *  false = rejected (host asks {@link #signalWrongCode()}). */
        boolean onCode(String code);
    }

    private static final String[] LETTERS = {"", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ"};

    private final StringBuilder value = new StringBuilder(8);

    private ImageView backButton;
    private TextView titleView;
    private TextView subtitleView;
    private CodeBoxesView boxesView;
    private TextView errorView;
    private ImageView fingerprintButton;

    private CharSequence errorText;
    private Runnable onBack;
    private Runnable onBiometric;
    private Delegate delegate;

    public MeeroCodeLockView(Context context) {
        super(context);
        build(context);
    }

    /** Wires content + callbacks. Null onBack hides the back arrow, null
     *  onBiometric hides the fingerprint key. Call AFTER construction. */
    public void setup(CharSequence title, CharSequence subtitle, CharSequence wrongText,
                      Runnable onBack, Runnable onBiometric, Delegate delegate) {
        this.errorText = wrongText;
        this.onBack = onBack;
        this.onBiometric = onBiometric;
        this.delegate = delegate;
        titleView.setText(title);
        subtitleView.setText(subtitle);
        backButton.setVisibility(onBack != null ? VISIBLE : INVISIBLE);
        fingerprintButton.setVisibility(onBiometric != null ? VISIBLE : INVISIBLE);
        value.setLength(0);
        errorView.setAlpha(0f);
        boxesView.setError(false);
        boxesView.invalidate();
    }

    /** Wrong code: red flash + shake + field reset, error line fades in. */
    public void signalWrongCode() {
        value.setLength(0);
        boxesView.setError(true);
        boxesView.invalidate();
        errorView.setText(errorText);
        errorView.animate().alpha(1f).setDuration(150).start();
        ObjectAnimator shake = ObjectAnimator.ofFloat(boxesView, TRANSLATION_X,
                0, -dp(12), dp(12), -dp(8), dp(8), -dp(4), dp(4), 0);
        shake.setDuration(420);
        shake.start();
    }

    private void onDigit(int digit) {
        if (value.length() >= 8) return;
        value.append(digit);
        boxesView.setError(false);
        boxesView.invalidate();
        errorView.animate().alpha(0f).setDuration(120).start();
        if (value.length() == 8) {
            final String code = value.toString();
            // one frame later so the last box visibly fills first
            AndroidUtilities.runOnUIThread(() -> {
                if (delegate != null && value.length() == 8) {
                    delegate.onCode(code);
                }
            }, 60);
        }
    }

    private void onBackspace() {
        if (value.length() > 0) {
            value.setLength(value.length() - 1);
            boxesView.invalidate();
        }
    }

    private void onClear() {
        if (value.length() > 0) {
            value.setLength(0);
            boxesView.invalidate();
        }
    }

    private int dp(float v) {
        return AndroidUtilities.dp(v);
    }

    private void build(Context context) {
        setClickable(true); // swallows touches to the locked content below
        setLayoutDirection(LAYOUT_DIRECTION_LTR); // digits keep 1-9 order

        int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        int grayColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);

        // --- back arrow (top-left, screenshot style) ---
        float statusBarDp = AndroidUtilities.statusBarHeight / getResources().getDisplayMetrics().density;
        backButton = new ImageView(context);
        backButton.setImageResource(R.drawable.baseline_arrow_back_24);
        backButton.setColorFilter(textColor);
        backButton.setScaleType(ImageView.ScaleType.CENTER);
        backButton.setBackground(circleRipple(Theme.multAlpha(textColor, 0.10f)));
        backButton.setContentDescription(LocaleController.getString(R.string.Back));
        backButton.setOnClickListener(v -> { if (onBack != null) onBack.run(); });
        addView(backButton, LayoutHelper0.createFrame(42, 42, Gravity.TOP | Gravity.LEFT,
                12, statusBarDp + 10, 0, 0));

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(dp(28), dp(18), dp(28), AndroidUtilities.navigationBarHeight + dp(10));
        scroll.addView(column, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        addView(scroll, LayoutHelper0.createFrame(LayoutHelper0.MATCH_PARENT, LayoutHelper0.MATCH_PARENT, Gravity.CENTER));

        // --- gradient lock badge (v109: shared standalone component) ---
        MeeroLockBadgeView badge = new MeeroLockBadgeView(context);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(104), dp(104));
        badgeLp.topMargin = dp(18);
        column.addView(badge, badgeLp);

        titleView = new TextView(context);
        titleView.setTextColor(textColor);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(20);
        column.addView(titleView, titleLp);

        subtitleView = new TextView(context);
        subtitleView.setTextColor(grayColor);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.5f);
        subtitleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(8);
        column.addView(subtitleView, subLp);

        // --- 8 digit boxes ---
        boxesView = new CodeBoxesView(context);
        LinearLayout.LayoutParams boxesLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        boxesLp.topMargin = dp(28);
        column.addView(boxesView, boxesLp);

        errorView = new TextView(context);
        errorView.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
        errorView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        errorView.setGravity(Gravity.CENTER);
        errorView.setAlpha(0f);
        column.addView(errorView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(20)));

        // --- keypad ---
        LinearLayout keypad = new LinearLayout(context);
        keypad.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams keypadLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        keypadLp.topMargin = dp(22);
        column.addView(keypad, keypadLp);

        final float screenWidth = AndroidUtilities.displaySize.x;
        final int keySize = (int) Math.min(dp(72), (screenWidth - dp(96)) / 3f);

        int digit = 1;
        for (int row = 0; row < 4; row++) {
            LinearLayout rowLayout = new LinearLayout(context);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setWeightSum(3);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (row > 0) rowLp.topMargin = dp(10);
            keypad.addView(rowLayout, rowLp);
            for (int col = 0; col < 3; col++) {
                FrameLayout cell = new FrameLayout(context);
                cell.setLayoutParams(new LinearLayout.LayoutParams(0, keySize + dp(8), 1f));
                rowLayout.addView(cell);
                if (row < 3 || col == 1) {
                    final int d = (row < 3) ? digit : 0;
                    cell.addView(makeDigitKey(context, d, keySize), centerLp(keySize));
                    if (row < 3) digit++;
                } else if (row == 3 && col == 0) {
                    fingerprintButton = new ImageView(context);
                    fingerprintButton.setImageResource(R.drawable.baseline_fingerprint_24);
                    fingerprintButton.setColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton));
                    fingerprintButton.setScaleType(ImageView.ScaleType.CENTER);
                    fingerprintButton.setBackground(circleRipple(Theme.multAlpha(grayColor, 0.10f)));
                    fingerprintButton.setVisibility(INVISIBLE);
                    fingerprintButton.setOnClickListener(v -> { if (onBiometric != null) onBiometric.run(); });
                    cell.addView(fingerprintButton, centerLp(keySize));
                } else {
                    ImageView backspace = new ImageView(context);
                    backspace.setImageResource(R.drawable.baseline_backspace_24);
                    backspace.setColorFilter(textColor);
                    backspace.setScaleType(ImageView.ScaleType.CENTER);
                    backspace.setBackground(circleRipple(Theme.multAlpha(grayColor, 0.10f)));
                    backspace.setContentDescription(LocaleController.getString(R.string.AccDescrBackspace));
                    backspace.setOnClickListener(v -> onBackspace());
                    backspace.setOnLongClickListener(v -> { onClear(); return true; });
                    cell.addView(backspace, centerLp(keySize));
                }
            }
        }
    }

    private FrameLayout.LayoutParams centerLp(int size) {
        return new FrameLayout.LayoutParams(size, size, Gravity.CENTER);
    }

    private View makeDigitKey(Context context, final int digit, int keySize) {
        int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        int grayColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        TextView key = new TextView(context);
        String letters = digit == 0 ? "+" : LETTERS[digit - 1];
        if (letters.isEmpty()) {
            key.setText(String.valueOf(digit));
        } else {
            // digit big + letters small, same style as the screenshot keypad
            SpannableString span = new SpannableString(digit + "  " + letters);
            int start = String.valueOf(digit).length() + 2;
            span.setSpan(new RelativeSizeSpan(0.42f), start, span.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            key.setText(span);
        }
        key.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 25);
        key.setTextColor(textColor);
        key.setTypeface(AndroidUtilities.bold());
        key.setGravity(Gravity.CENTER);
        key.setBackground(digitKeyBackground(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText)));
        key.setOnClickListener(v -> onDigit(digit));
        return key;
    }

    /** Filled soft circle + ripple for digit keys. */
    private Drawable digitKeyBackground(int grayColor) {
        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.OVAL);
        normal.setColor(Theme.multAlpha(grayColor, 0.10f));
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.BLACK);
        ColorStateList rippleColor = ColorStateList.valueOf(Theme.multAlpha(grayColor, 0.22f));
        return new RippleDrawable(rippleColor, normal, mask);
    }

    private Drawable circleRipple(int rippleColorValue) {
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.BLACK);
        return new RippleDrawable(ColorStateList.valueOf(rippleColorValue), null, mask);
    }

    // ---------------- inner pieces ----------------

    /** The eight rounded boxes: idle gray stroke, accent stroke on the next
     *  box, accent dot + soft fill for entered digits, red on signalWrongCode. */
    private class CodeBoxesView extends View {
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private boolean error;

        CodeBoxesView(Context context) {
            super(context);
            strokePaint.setStyle(Paint.Style.STROKE);
            dotPaint.setStyle(Paint.Style.FILL);
        }

        void setError(boolean e) {
            error = e;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
            int gray = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
            int red = Theme.getColor(Theme.key_text_RedRegular);

            float gap = dp(8);
            float box = Math.min(dp(38), (getWidth() - gap * 7) / 8f);
            float total = box * 8 + gap * 7;
            float x = (getWidth() - total) / 2f;
            float y = (getHeight() - box * 1.18f) / 2f;
            float h = box * 1.18f;
            float radius = dp(11);

            int len = value.length();
            for (int i = 0; i < 8; i++) {
                rect.set(x + i * (box + gap), y, x + i * (box + gap) + box, y + h);

                boolean isNext = !error && i == len;
                if (i < len) {
                    // entered: soft accent fill + dot
                    fillPaint.setColor(Theme.multAlpha(accent, 0.10f));
                    canvas.drawRoundRect(rect, radius, radius, fillPaint);
                    strokePaint.setColor(accent);
                    strokePaint.setStrokeWidth(dp(1.8f));
                    canvas.drawRoundRect(rect, radius, radius, strokePaint);
                    dotPaint.setColor(accent);
                    canvas.drawCircle(rect.centerX(), rect.centerY(), dp(6), dotPaint);
                } else {
                    strokePaint.setStrokeWidth(error ? dp(1.8f) : isNext ? dp(2.3f) : dp(1.6f));
                    strokePaint.setColor(error ? red : isNext ? accent : Theme.multAlpha(gray, 0.55f));
                    canvas.drawRoundRect(rect, radius, radius, strokePaint);
                }
            }
        }
    }

    /** Local copy so the view never imports the heavy Components LayoutHelper. */
    private static final class LayoutHelper0 {
        static final int MATCH_PARENT = -1;

        static FrameLayout.LayoutParams createFrame(float width, float height, int gravity) {
            return createFrame(width, height, gravity, 0, 0, 0, 0);
        }

        static FrameLayout.LayoutParams createFrame(float width, float height, int gravity,
                                                    float leftMargin, float topMargin, float rightMargin, float bottomMargin) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    width == MATCH_PARENT ? FrameLayout.LayoutParams.MATCH_PARENT : AndroidUtilities.dp(width),
                    height == MATCH_PARENT && height < 0 ? FrameLayout.LayoutParams.MATCH_PARENT : AndroidUtilities.dp(height),
                    gravity);
            lp.leftMargin = AndroidUtilities.dp(leftMargin);
            lp.topMargin = AndroidUtilities.dp(topMargin);
            lp.rightMargin = AndroidUtilities.dp(rightMargin);
            lp.bottomMargin = AndroidUtilities.dp(bottomMargin);
            return lp;
        }
    }
}

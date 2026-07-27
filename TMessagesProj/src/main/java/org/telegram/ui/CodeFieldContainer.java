package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;

import androidx.core.graphics.ColorUtils;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class CodeFieldContainer extends LinearLayout {
    public final static int TYPE_PASSCODE = 10;

    /**
     * MeeroX: iOS 26 code entry metrics.
     *
     * Telegram for iOS lays this screen out in CodeInputView.swift. Its boxes
     * are much softer and further apart than Android's, and that difference -
     * a 4dp radius against iOS's 15 - is what makes the stock screen read as
     * Android at a glance.
     *
     *   height       = compact ? 44.0 : 51.0
     *   itemSize     = floor(24.0 * height / 28.0)   // width follows height
     *   itemSpacing  = 15.0                          // when there is no prefix
     *   cornerRadius = height == 28.0 ? 12.0 : 15.0
     *   fontSize     = floor(13.0 * height / 28.0)
     *   borderWidth  = 1.0 + UIScreenPixel
     *
     * The width is deliberately derived rather than hardcoded, so a compact
     * layout keeps iOS's own proportions instead of squashing the boxes.
     */
    private static final float IOS_HEIGHT_DP = 51f;
    private static final float IOS_HEIGHT_COMPACT_DP = 44f;
    private static final float IOS_WIDTH_RATIO = 24f / 28f;
    private static final float IOS_SPACING_DP = 15f;
    private static final float IOS_RADIUS_DP = 15f;
    private static final float IOS_FONT_RATIO = 13f / 28f;
    /** iOS's own threshold for switching to the compact layout. */
    private static final int IOS_COMPACT_WIDTH_DP = 375;
    private static final int IOS_COMPACT_COUNT = 5;

    public static boolean meeroIosCode() {
        try {
            return tw.nekomimi.nekogram.NekoConfig.meeroIosCode.Bool();
        } catch (Throwable ignore) {
            return false;
        }
    }

    /** iOS drops to the shorter box on narrow screens or long codes. */
    private static boolean meeroCompact(int count) {
        final int widthDp = (int) (AndroidUtilities.displaySize.x / AndroidUtilities.density);
        return widthDp <= 320 || (widthDp <= IOS_COMPACT_WIDTH_DP && count > IOS_COMPACT_COUNT);
    }

    private static float meeroBoxHeight(int count) {
        return meeroCompact(count) ? IOS_HEIGHT_COMPACT_DP : IOS_HEIGHT_DP;
    }

    /**
     * MeeroX: the layout height a host should give this container.
     *
     * The callers in LoginActivity hardcode 42dp, which was exactly the old
     * box height. iOS's box is 51dp, so the bottom 9dp of every box - the
     * whole lower curve - was clipped and each outline read as an upside-down
     * U. They also run before setNumbersCount, so they cannot know the digit
     * count yet; WRAP_CONTENT lets onMeasure claim the right height once the
     * boxes exist.
     */
    public static int meeroHostHeight() {
        return meeroIosCode() ? LayoutHelper.WRAP_CONTENT : 42;
    }

    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    float strokeWidth;
    public boolean ignoreOnTextChange;
    public boolean isFocusSuppressed;

    public CodeNumberField[] codeField;

    public CodeFieldContainer(Context context) {
        super(context);
        paint.setStyle(Paint.Style.STROKE);
        setOrientation(HORIZONTAL);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // MeeroX: the hosts in LoginActivity size this slot before
        // setNumbersCount has run, so the boxes did not exist yet and the
        // slot fell back to the old 42dp. iOS's box is 51dp, so the bottom
        // 9dp - the whole lower curve - was being clipped away, which is what
        // made every outline read as an upside-down U.
        //
        // Measuring the children first and then claiming their real height
        // keeps the slot correct no matter when the digits get added.
        if (meeroIosCode() && codeField != null && codeField.length > 0) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                    AndroidUtilities.dp(meeroBoxHeight(codeField.length)), MeasureSpec.EXACTLY));
            paint.setStrokeWidth(strokeWidth = AndroidUtilities.dp(1f) + 1f);
            return;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        // iOS strokes these at 1pt plus a single screen pixel; Android's 1.5dp
        // reads noticeably heavier against the softer corners.
        paint.setStrokeWidth(strokeWidth = meeroIosCode()
                ? AndroidUtilities.dp(1f) + 1f
                : AndroidUtilities.dp(1.5f));
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof CodeNumberField) {
                CodeNumberField codeField = (CodeNumberField) child;
                if (!isFocusSuppressed) {
                    if (child.isFocused()) {
                        codeField.animateFocusedProgress(1f);
                    } else if (!child.isFocused()) {
                        codeField.animateFocusedProgress(0);
                    }
                }
                float successProgress = codeField.getSuccessProgress();
                int focusClr = ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhiteInputField), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated), codeField.getFocusedProgress());
                int errorClr = ColorUtils.blendARGB(focusClr, Theme.getColor(Theme.key_text_RedBold), codeField.getErrorProgress());
                paint.setColor(ColorUtils.blendARGB(errorClr, Theme.getColor(Theme.key_checkbox), successProgress));
                AndroidUtilities.rectTmp.set(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
                AndroidUtilities.rectTmp.inset(strokeWidth, strokeWidth);
                if (successProgress != 0) {
                    float offset = -Math.max(0, strokeWidth * (codeField.getSuccessScaleProgress() - 1f));
                    AndroidUtilities.rectTmp.inset(offset, offset);
                }

                // iOS rounds these to 15pt, against Android's 4dp. That single
                // number is the clearest tell between the two screens.
                final float radius = AndroidUtilities.dp(meeroIosCode() ? IOS_RADIUS_DP : 4);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, radius, radius, paint);
            }
        }
        super.dispatchDraw(canvas);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child instanceof CodeNumberField) {
            CodeNumberField field = (CodeNumberField) child;
            canvas.save();
            float progress = ((CodeNumberField) child).enterAnimation;
            AndroidUtilities.rectTmp.set(child.getX(), child.getY(), child.getX() + child.getMeasuredWidth(), child.getY() + child.getMeasuredHeight());
            AndroidUtilities.rectTmp.inset(strokeWidth, strokeWidth);
            canvas.clipRect(AndroidUtilities.rectTmp);
            if (field.replaceAnimation) {
                float s = progress * 0.5f + 0.5f;
                child.setAlpha(progress);
                canvas.scale(s, s, field.getX() + field.getMeasuredWidth() / 2f, field.getY() + field.getMeasuredHeight() / 2f);
            } else {
                child.setAlpha(1f);
                canvas.translate(0, child.getMeasuredHeight() * (1f - progress));
            }
            super.drawChild(canvas, child, drawingTime);
            canvas.restore();

            float exitProgress = field.exitAnimation;
            if (exitProgress < 1f) {
                canvas.save();
                float s = (1f - exitProgress) * 0.5f + 0.5f;
                canvas.scale(s, s, field.getX() + field.getMeasuredWidth() / 2f, field.getY() + field.getMeasuredHeight() / 2f);
                bitmapPaint.setAlpha((int) (255 * (1f - exitProgress)));
                canvas.drawBitmap(field.exitBitmap, field.getX(), field.getY(), bitmapPaint);
                canvas.restore();
            }
            return true;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    public void setNumbersCount(int length, int currentType) {
        if (codeField == null || codeField.length != length) {
            if (codeField != null) {
                for (CodeNumberField f : codeField) {
                    removeView(f);
                }
            }
            codeField = new CodeNumberField[length];
            for (int a = 0; a < length; a++) {
                final int num = a;
                codeField[a] = new CodeNumberField(getContext()) {
                    @Override
                    public boolean dispatchKeyEvent(KeyEvent event) {
                        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                            return false;
                        }
                        int keyCode = event.getKeyCode();
                        if (num >= codeField.length) {
                            return false;
                        }
                        if (event.getAction() == KeyEvent.ACTION_UP) {
                            if (keyCode == KeyEvent.KEYCODE_DEL && codeField[num].length() == 1) {
                                codeField[num].startExitAnimation();
                                codeField[num].setText("");
                                return true;
                            } else if (keyCode == KeyEvent.KEYCODE_DEL && codeField[num].length() == 0 && num > 0) {
                                codeField[num - 1].setSelection(codeField[num - 1].length());
                                for (int i = 0; i < num; i++) {
                                    if (i == num - 1) {
                                        codeField[num - 1].requestFocus();
                                    } else {
                                        codeField[i].clearFocus();
                                    }
                                }
                                codeField[num - 1].startExitAnimation();
                                codeField[num - 1].setText("");
                                return true;
                            } else {
                                if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                                    String str = Integer.toString(keyCode - KeyEvent.KEYCODE_0);
                                    if (codeField[num].getText() != null && str.equals(codeField[num].getText().toString())) {
                                        if (num >= length - 1) {
                                            processNextPressed();
                                        } else {
                                            codeField[num + 1].requestFocus();
                                        }
                                        return true;
                                    }
                                    if (codeField[num].length() > 0) {
                                        codeField[num].startExitAnimation();
                                    }
                                    codeField[num].setText(str);
                                }
                                return true;
                            }
                        } else {
                            return isFocused();
                        }
                    }
                };

                codeField[a].setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                if (meeroIosCode()) {
                    // iOS scales the digit with the box and sets it in the
                    // system monospaced face, so the digits never shift
                    // sideways as they are typed.
                    codeField[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP,
                            (float) Math.floor(IOS_FONT_RATIO * meeroBoxHeight(length)));
                    codeField[a].setTypeface(android.graphics.Typeface.MONOSPACE);
                } else {
                    codeField[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                    codeField[a].setTypeface(AndroidUtilities.bold());
                }
                codeField[a].setMaxLines(1);
                codeField[a].setPadding(0, 0, 0, 0);
                codeField[a].setGravity(Gravity.CENTER);
                if (currentType == 3) {
                    codeField[a].setEnabled(false);
                    codeField[a].setInputType(InputType.TYPE_NULL);
                    codeField[a].setVisibility(GONE);
                } else {
                    codeField[a].setInputType(InputType.TYPE_CLASS_PHONE);
                }
                int width;
                int height;
                int gapSize;
                if (meeroIosCode() && currentType != LoginActivity.AUTH_TYPE_MISSED_CALL) {
                    // iOS derives the width from the height, so the box keeps
                    // its proportions whichever of the two heights applies.
                    final float h = meeroBoxHeight(length);
                    height = Math.round(h);
                    width = (int) Math.floor(IOS_WIDTH_RATIO * h);
                    gapSize = Math.round(IOS_SPACING_DP);
                } else if (currentType == TYPE_PASSCODE) {
                    width = 42;
                    height = 47;
                    gapSize = 10;
                } else if (currentType == LoginActivity.AUTH_TYPE_MISSED_CALL) {
                    width = 28;
                    height = 34;
                    gapSize = 5;
                } else {
                    width = 34;
                    height = 42;
                    gapSize = 7;
                }
                addView(codeField[a], LayoutHelper.createLinear(width, height, Gravity.CENTER_HORIZONTAL, 0, 0, a != length - 1 ? gapSize: 0, 0));
                codeField[a].addTextChangedListener(new TextWatcher() {

                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (ignoreOnTextChange) {
                            return;
                        }
                        int len = s.length();
                        if (len >= 1) {
                            boolean next = false;
                            int n = num;
                            if (len > 1) {
                                String text = s.toString();
                                ignoreOnTextChange = true;
                                for (int a = 0; a < Math.min(length - num, len); a++) {
                                    if (a == 0) {
                                        s.replace(0, len, text.substring(a, a + 1));
                                    } else {
                                        n++;
                                        if (num + a < codeField.length) {
                                            codeField[num + a].setText(text.substring(a, a + 1));
                                        }
                                    }
                                }
                                ignoreOnTextChange = false;
                            }


                            if (n + 1 >= 0 && n + 1 < codeField.length) {
                                codeField[n + 1].setSelection(codeField[n + 1].length());
                                codeField[n + 1].requestFocus();
                            }
                            if ((n == length - 1 || n == length - 2 && len >= 2) && getCode().length() == length) {
                                processNextPressed();
                            }
                        }
                    }
                });
                codeField[a].setOnEditorActionListener((textView, i, keyEvent) -> {
                    if (i == EditorInfo.IME_ACTION_NEXT) {
                        processNextPressed();
                        return true;
                    }
                    return false;
                });
            }
        } else {
            for (int a = 0; a < codeField.length; a++) {
                codeField[a].setText("");
            }
        }
    }

    protected void processNextPressed() {

    }

    public String getCode() {
        if (codeField == null) {
            return "";
        }
        StringBuilder codeBuilder = new StringBuilder();
        for (int a = 0; a < codeField.length; a++) {
            codeBuilder.append(PhoneFormat.stripExceptNumbers(codeField[a].getText().toString()));
        }
        return codeBuilder.toString();
    }

    public void setCode(String savedCode) {
        codeField[0].setText(savedCode);
    }

    public void setText(String code) {
        setText(code, false);
    }

    public void setText(String code, boolean fromPaste) {
        if (codeField == null) {
            return;
        }
        int startFrom = 0;
        if (fromPaste) {
            for (int i = 0; i < codeField.length; i++) {
                if (codeField[i].isFocused()) {
                    startFrom = i;
                    break;
                }
            }
        }
        for (int i = startFrom; i < Math.min(codeField.length, startFrom + code.length()); i++) {
            codeField[i].setText(Character.toString(code.charAt(i - startFrom)));
        }
    }

}

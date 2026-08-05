package tw.nekomimi.nekogram.ui

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.Theme
import tw.nekomimi.nekogram.MeeroGlassTheme

@SuppressLint("ViewConstructor", "NewApi")
class PopupBuilder @JvmOverloads constructor(anchor: View, dialog: Boolean = false) : ActionBarMenuItem(anchor.context, null, Theme.ACTION_BAR_WHITE_SELECTOR_COLOR, -0x4c4c4d) {

    private val itemViews = mutableListOf<TextView>()
    private var selectedIndex = -1

    init {

        setAnchor(anchor)

        isShowOnTop = dialog

        isVerticalScrollBarEnabled = true

    }

    /**
     * MeeroX v132: glass identity for every select popup (approved mock
     * popup-mock-v132.html). v131 only retinted the sheet, which read as
     * "nothing changed"; now the popup wears the full Glass Night styling:
     * fixed sheet color, a 1.5dp rose->violet edge ring, the fixed press
     * tint (the stock themed ripple leaked through before - plain TextView
     * rows were never covered by setPopupItemsSelectorColor), and the
     * CURRENT selection drawn in rose with a gradient check badge.
     *
     * Everything lives behind the live master switch: with the design off
     * none of it runs and the popup is byte-identical to stock.
     */
    private fun applyMeeroGlassSkin() {

        if (!MeeroGlassTheme.enabled()) return

        try {

            redrawPopup(MeeroGlassTheme.sheetBg())

            // 1.5dp rose->violet edge ring, 12dp to match the stock sheet
            // asset exactly (popup_fixed_alert4 measures ~11.7dp; the stock
            // body+shadow 9-patch stays underneath, so nothing breaks).
            // Drawn by our own tiny Drawable: GradientDrawable.setGradientStroke
            // is not in this module's compile SDK (v132 build lesson).
            getPopupLayout().foreground = EdgeRing()

            for (i in 0 until itemViews.size) {
                val tv = itemViews[i]
                tv.setBackgroundDrawable(Theme.createSelectorDrawable(MeeroGlassTheme.press(), 1))
                if (i == selectedIndex) {
                    tv.setTextColor(MeeroGlassTheme.ACC1)
                    tv.paint.isFakeBoldText = true
                    val badge = CheckBadge()
                    badge.setBounds(0, 0, AndroidUtilities.dp(18f), AndroidUtilities.dp(18f))
                    tv.compoundDrawablePadding = AndroidUtilities.dp(8f)
                    tv.setCompoundDrawablesRelative(null, null, badge, null)
                } else {
                    tv.setTextColor(MeeroGlassTheme.ink())
                    tv.paint.isFakeBoldText = false
                    tv.setCompoundDrawablesRelative(null, null, null, null)
                }
            }

        } catch (ignore: Throwable) {

        }

    }

    /** 1.5dp rose->violet rounded ring, drawn over the popup sheet's edge. */
    private class EdgeRing : Drawable() {

        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rectF = RectF()

        init {
            stroke.style = Paint.Style.STROKE
            stroke.strokeWidth = AndroidUtilities.dp(1.5f).toFloat()
        }

        override fun onBoundsChange(bounds: Rect) {
            stroke.shader = null
            val inset = stroke.strokeWidth / 2f
            stroke.shader = LinearGradient(
                bounds.left.toFloat(), bounds.top.toFloat(),
                bounds.right.toFloat(), bounds.bottom.toFloat(),
                intArrayOf(0x88FF4E8A.toInt(), 0x557B5CFF, 0x22FFFFFF),
                null, Shader.TileMode.CLAMP
            )
            rectF.set(
                bounds.left + inset, bounds.top + inset,
                bounds.right - inset, bounds.bottom - inset
            )
        }

        override fun draw(canvas: Canvas) {
            canvas.drawRoundRect(rectF, AndroidUtilities.dp(12f).toFloat(), AndroidUtilities.dp(12f).toFloat(), stroke)
        }

        override fun setAlpha(alpha: Int) {
            stroke.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    /** Gradient rose->violet oval with a white check, for the selected row. */
    private class CheckBadge : Drawable() {

        private val circle = Paint(Paint.ANTI_ALIAS_FLAG)
        private val mark = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rectF = RectF()

        init {
            mark.color = -0x1 // white
            mark.textSize = AndroidUtilities.dp(11f).toFloat()
            mark.isFakeBoldText = true
            mark.textAlign = Paint.Align.CENTER
        }

        override fun onBoundsChange(bounds: Rect) {
            circle.shader = LinearGradient(
                bounds.left.toFloat(), bounds.top.toFloat(),
                bounds.right.toFloat(), bounds.bottom.toFloat(),
                0xFFFF4E8A.toInt(), 0xFF7B5CFF.toInt(), Shader.TileMode.CLAMP
            )
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            rectF.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
            canvas.drawOval(rectF, circle)
            val cy = rectF.centerY() - (mark.descent() + mark.ascent()) / 2f
            canvas.drawText("\u2713", rectF.centerX(), cy, mark)
        }

        override fun setAlpha(alpha: Int) {
            circle.alpha = alpha
            mark.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    @JvmOverloads
    fun setItems(items: Array<CharSequence?>, selected: Int = -1, listener: (Int, CharSequence) -> Unit) {

        selectedIndex = selected

        itemViews.clear()

        removeAllSubItems()

        for (item in items) {
            if (item == null) continue
            itemViews.add(addSubItem(items.indexOf(item), item))
        }

        setDelegate {

            listener(it, items[it]!!)

        }

        applyMeeroGlassSkin()

    }

    @JvmOverloads
    fun setItems(items: List<CharSequence?>, selected: Int = -1, listener: (Int, CharSequence) -> Unit) {

        selectedIndex = selected

        itemViews.clear()

        removeAllSubItems()

        for (item in items) {
            if (item == null) continue
            itemViews.add(addSubItem(items.indexOf(item), item))
        }

        setDelegate {

            listener(it, items[it]!!)

        }

        applyMeeroGlassSkin()

    }

    fun show() {

        applyMeeroGlassSkin()

        toggleSubMenu()

    }

}

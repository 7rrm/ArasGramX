package tw.nekomimi.nekogram.ui

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
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
     * MeeroX v133: the user picked the "iOS Sheet" re-design (approved mock
     * popup-options-v133.html, option 1) over v132's edge-ring + badge look.
     * The popup now reads like a native iPhone menu: a fixed-colour rounded
     * sheet, NO outer ring of any kind, hairline 0.5dp separators between
     * rows, the fixed press tint underneath (the stock themed ripple leaked
     * through before - plain TextView rows were never covered by
     * setPopupItemsSelectorColor), and the CURRENT selection in rose bold
     * with a thin outlined check at the reading-end side.
     *
     * Everything lives behind the live master switch: with the design off
     * none of it runs and the popup is byte-identical to stock.
     */
    private fun applyMeeroGlassSkin() {

        if (!MeeroGlassTheme.enabled()) return

        try {

            redrawPopup(MeeroGlassTheme.sheetBg())

            // v132 drew a gradient EdgeRing as the popup's foreground. The
            // iOS Sheet has no ring at all - clear it so a popup re-shown
            // after a theme change never keeps a stale ring.
            getPopupLayout().foreground = null

            val hairColor = if (MeeroGlassTheme.isNight()) 0x268C8CA0 else 0x1F141428

            for (i in 0 until itemViews.size) {

                val tv = itemViews[i]

                // Press feedback is its own layer, the separator sits on top
                // of it so a pressed row's tint runs underneath the hairline
                // exactly like iOS draws it. Last row carries no separator.
                val press = Theme.createSelectorDrawable(MeeroGlassTheme.press(), 1)
                tv.background = if (i < itemViews.size - 1) {
                    LayerDrawable(arrayOf(press, Hairline(hairColor)))
                } else {
                    press
                }

                if (i == selectedIndex) {
                    tv.setTextColor(MeeroGlassTheme.ACC1)
                    tv.paint.isFakeBoldText = true
                    val mark = CheckMark()
                    mark.setBounds(0, 0, AndroidUtilities.dp(16f), AndroidUtilities.dp(16f))
                    tv.compoundDrawablePadding = AndroidUtilities.dp(8f)
                    tv.setCompoundDrawablesRelative(null, null, mark, null)
                } else {
                    tv.setTextColor(MeeroGlassTheme.ink())
                    tv.paint.isFakeBoldText = false
                    tv.setCompoundDrawablesRelative(null, null, null, null)
                }

            }

        } catch (ignore: Throwable) {

        }

    }

    /**
     * 1px hairline pinned to the row's bottom edge, inset 16dp from the
     * text side like iOS menu separators. Draws nothing on the last row
     * because the skin simply never assigns it there.
     */
    private class Hairline(private val color: Int) : Drawable() {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val baseAlpha = color ushr 24

        override fun draw(canvas: Canvas) {
            val b = bounds
            // Assigning color also rewrites paint.alpha to color's own, so
            // propagate manually: base alpha times the propagated factor.
            val propagated = (baseAlpha * propagatedAlpha) / 255
            paint.color = (propagated shl 24) or (color and 0x00FFFFFF)
            val one = AndroidUtilities.dpf2(1f).let { if (it < 1f) 1f else it }
            // Inset from the trailing (reading-end) edge only; in RTL the
            // compound tick sits at the end, so the line starts clean under
            // the text block on both directions.
            val inset = AndroidUtilities.dp(16f).toFloat()
            canvas.drawRect(b.left + inset, b.bottom - one, b.right.toFloat(), b.bottom.toFloat(), paint)
        }

        private var propagatedAlpha = 255

        override fun setAlpha(alpha: Int) {
            propagatedAlpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    /** Thin outlined check in the accent colour, replacing v132's filled badge. */
    private class CheckMark : Drawable() {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()

        init {
            paint.color = MeeroGlassTheme.ACC1
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = AndroidUtilities.dpf2(2f)
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            path.reset()
            path.moveTo(b.left + w * 0.22f, b.top + h * 0.55f)
            path.lineTo(b.left + w * 0.44f, b.top + h * 0.76f)
            path.lineTo(b.left + w * 0.80f, b.top + h * 0.30f)
            canvas.drawPath(path, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
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

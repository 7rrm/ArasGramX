package tw.nekomimi.nekogram.ui

import android.annotation.SuppressLint
import android.view.View
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.Theme
import tw.nekomimi.nekogram.MeeroGlassTheme

@SuppressLint("ViewConstructor")
class PopupBuilder @JvmOverloads constructor(anchor: View, dialog: Boolean = false) : ActionBarMenuItem(anchor.context, null, Theme.ACTION_BAR_WHITE_SELECTOR_COLOR, -0x4c4c4d) {

    init {

        setAnchor(anchor)

        isShowOnTop = dialog

        isVerticalScrollBarEnabled = true

    }

    /**
     * MeeroX v131: the select popups (bubble styles, icon pickers, every
     * ConfigCellSelectBox row) sat on the themed submenu palette, which
     * clashes with the fixed glass design (the user's report #4). While the
     * master glass switch is on, the popup wears the fixed sheet palette;
     * off - the constructor values run verbatim. Live read, stock when off.
     */
    private fun applyMeeroGlassSkin() {

        if (!MeeroGlassTheme.enabled()) return

        try {

            redrawPopup(MeeroGlassTheme.sheetBg())

            setPopupItemsColor(MeeroGlassTheme.ink(), false)

            setPopupItemsColor(MeeroGlassTheme.ink(), true)

            setPopupItemsSelectorColor(MeeroGlassTheme.press())

        } catch (ignore: Throwable) {

        }

    }

    fun setItems(items: Array<CharSequence?>, listener: (Int, CharSequence) -> Unit) {

        removeAllSubItems()

        for (item in items) {
            if (item == null) continue
            addSubItem(items.indexOf(item), item)
        }

        setDelegate {

            listener(it, items[it]!!)

        }

        applyMeeroGlassSkin()

    }

    fun setItems(items: List<CharSequence?>, listener: (Int, CharSequence) -> Unit) {

        removeAllSubItems()

        for (item in items) {
            if (item == null) continue
            addSubItem(items.indexOf(item), item)
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

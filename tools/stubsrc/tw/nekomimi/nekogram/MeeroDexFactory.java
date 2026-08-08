package tw.nekomimi.nekogram;

import android.app.AppComponentFactory;

/**
 * MeeroX v168 - occupies the manifest's appComponentFactory slot.
 *
 * Reason to exist: the merged manifest inherits
 * androidx.core.app.CoreComponentFactory from libraries, and that class
 * lives inside the now-ENCRYPTED dex - the boot classloader would crash
 * resolving it before our stub got a chance to run. Declaring this
 * framework-based factory (it ships plain, inside the stub dex) takes
 * the slot and plain framework behaviour is inherited as-is.
 *
 * Loaded only on API 28+ (the attribute did not exist before); on API 27
 * the system ignores the attribute, the class is never resolved, and the
 * missing superclass is therefore harmless there.
 */
public class MeeroDexFactory extends AppComponentFactory {
    // Everything intentionally inherited: the framework
    // AppComponentFactory implements plain class instantiation, which is
    // exactly what we need - MeeroDexApp does the vault work.
}

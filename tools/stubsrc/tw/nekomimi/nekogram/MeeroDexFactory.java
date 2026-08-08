package tw.nekomimi.nekogram;

import android.app.AppComponentFactory;
import android.app.Application;

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
 *
 * v172 fix (owned defect - logged): in v171 this factory blindly created
 * the full MeeroDexApp in EVERY process, including the :meeroboot splash
 * process. The splash therefore ran the 42 MB vault decrypt itself
 * before it could draw a single pixel: the prep screen never appeared
 * and the one-time ANR came back. The splash needs only framework code
 * plus its own stub classes, so it now boots with a PLAIN Application -
 * vault-free, instant - while the main process keeps the proven v169
 * path. MeeroDexApp carries a second guard for API 27, where this
 * factory is never consulted.
 */
public class MeeroDexFactory extends AppComponentFactory {

    @Override
    public Application instantiateApplication(ClassLoader cl, String className)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        if (isPrepProcess()) {
            // Pure-stub boot for the prep splash: no vault decrypt, no
            // application swap - just framework + MeeroBootActivity.
            return new Application();
        }
        return super.instantiateApplication(cl, className);
    }

    static boolean isPrepProcess() {
        try {
            final String p = Application.getProcessName();
            return p != null && p.endsWith(":meeroboot");
        } catch (Throwable t) {
            return false;
        }
    }

    // Everything else intentionally inherited: the framework
    // AppComponentFactory implements plain class instantiation, which is
    // exactly what we need - MeeroDexApp does the vault work.
}

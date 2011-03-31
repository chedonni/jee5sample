package org.freejava.podcaster;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends AbstractUIPlugin {

    // The plug-in ID
    public static final String PLUGIN_ID = "org.freejava.podcaster";

    // The shared instance
    private static Activator plugin;

    /**
     * The constructor
     */
    public Activator() {
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext
     * )
     */
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext
     * )
     */
    public void stop(BundleContext context) throws Exception {
        plugin = null;
        super.stop(context);
    }

    /**
     * Returns the shared instance
     *
     * @return the shared instance
     */
    public static Activator getDefault() {
        return plugin;
    }

    /**
     * Returns an image descriptor for the image file at the given plug-in
     * relative path
     *
     * @param path
     *            the path
     * @return the image descriptor
     */
    public static ImageDescriptor getImageDescriptor(String path) {
        return imageDescriptorFromPlugin(PLUGIN_ID, path);
    }

    public static synchronized void logError(String message, Throwable ex) {
        if (message == null)
            message = ""; //$NON-NLS-1$
        Status errorStatus = new Status(IStatus.ERROR, PLUGIN_ID, IStatus.OK,
                message, ex);
        ex.printStackTrace();
        if (getDefault() != null) {
            getDefault().getLog().log(errorStatus); 
        } else {
            System.err.println(message);
            if (ex != null) ex.printStackTrace();
        }
    }


    public static synchronized void logWarning(String message) {
        if (message == null)
            message = ""; //$NON-NLS-1$
        Status warningStatus = new Status(IStatus.WARNING, PLUGIN_ID,
                IStatus.OK, message, null);
        if (getDefault() != null) {
            getDefault().getLog().log(warningStatus); 
        } else {
            System.err.println(message);
        }
    }
    public static synchronized void logWarning(String message, Throwable ex) {
        if (message == null)
            message = ""; //$NON-NLS-1$
        Status warningStatus = new Status(IStatus.WARNING, PLUGIN_ID,
                IStatus.OK, message, ex);
        ex.printStackTrace();
        if (getDefault() != null) {
            getDefault().getLog().log(warningStatus); 
        } else {
            System.err.println(message);
            if (ex != null) ex.printStackTrace();
        }
    }
}

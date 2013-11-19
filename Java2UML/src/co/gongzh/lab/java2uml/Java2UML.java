package co.gongzh.lab.java2uml;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class Java2UML extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "co.gongzh.lab.java2uml"; //$NON-NLS-1$

	// The shared instance
	private static Java2UML plugin;
	
	/**
	 * The constructor
	 */
	public Java2UML() {
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
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
	public static Java2UML getDefault() {
		return plugin;
	}

}

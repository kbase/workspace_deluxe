package us.kbase.workspace.listener;

import java.util.Map;

/** A configuration agent for a workspace event listener. Given a configuration,
 * properly creates and configures an event listener.
 * @author gaprice@lbl.gov
 *
 */
public interface WorkspaceEventListenerFactory {
	
	/** Given a configuration, creates a listener.
	 * @param cfg the listener configuration.
	 * @return the new listener.
	 * @throws ListenerInitializationException if the listener could not be initialized.
	 */
	WorkspaceEventListener configure(Map<String, String> cfg)
			throws ListenerInitializationException;
}

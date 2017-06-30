package us.kbase.workspace.listener;

import java.util.Map;

/** Doesn't have a zero argument constructor, and so fails to start.
 * @author gaprice@lbl.gov
 *
 */
public class BadListenerFactory implements WorkspaceEventListenerFactory {

	public BadListenerFactory(final int foo) {}
	
	@Override
	public WorkspaceEventListener configure(Map<String, String> cfg)
			throws ListenerInitializationException {
		return new BadListener();
	}
	
	public class BadListener implements WorkspaceEventListener {

		@Override
		public void createWorkspace(long id) {}

		@Override
		public void cloneWorkspace(long id) {}
		
	}

}

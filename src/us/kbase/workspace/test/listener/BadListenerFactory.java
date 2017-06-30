package us.kbase.workspace.test.listener;

import java.util.Map;

import us.kbase.workspace.listener.ListenerInitializationException;
import us.kbase.workspace.listener.WorkspaceEventListener;
import us.kbase.workspace.listener.WorkspaceEventListenerFactory;

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

		@Override
		public void setWorkspaceMetadata(long id) {}
		
	}

}

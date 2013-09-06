import java.net.URL;

import org.junit.Test;

import us.kbase.workspace.WorkspaceClient;

public class TestClientImport {
	
	@Test
	public void checkClientImport() throws Exception {
		WorkspaceClient c = new WorkspaceClient(new URL("http://johanngambolputtydevonausfernschplendenschlittercrasscrenbon.com"));
		c.isAuthAllowedForHttp();
		//ok all imports work
	}
}
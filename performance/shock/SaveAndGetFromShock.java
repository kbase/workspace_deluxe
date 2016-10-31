package shock;

import static utils.Utils.makeString;
import static utils.Utils.printElapse;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.output.NullOutputStream;

import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockNode;
import us.kbase.shock.client.ShockNodeId;

public class SaveAndGetFromShock {

//	public static final String SHOCK_URL = "http://localhost:7044";
	public static final String SHOCK_URL = "https://ci.kbase.us/services/shock-api";
	public static final int COUNT = 10000;
	public static final int FILE_SIZE = 400;
	
	public static void main(String[] args) throws Exception {
		final String strtoken = args[0];
		final String contents = makeString(FILE_SIZE);
		final AuthToken token = AuthService.validateToken(strtoken);
		final BasicShockClient bsc = new BasicShockClient(new URL(SHOCK_URL), token);
		final byte[] conbytes = contents.getBytes();
		final List<String> ids = new LinkedList<>();
		final long presave = System.nanoTime();
		for (int i = 0; i < COUNT; i++) {
			final ByteArrayInputStream bais = new ByteArrayInputStream(conbytes);
			final ShockNode node = bsc.addNode(bais, "foo", "text");
			ids.add(node.getId().getId());
		}
		printElapse("shock load", presave);
		final long preget = System.nanoTime();
		for (final String id: ids) {
			bsc.getFile(new ShockNodeId(id), new NullOutputStream());
		}
		printElapse("shock get", preget);
	}

}

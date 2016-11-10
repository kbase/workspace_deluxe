package us.kbase.workspace.performance.workspace;

import static us.kbase.workspace.performance.utils.Utils.makeString;
import static us.kbase.workspace.performance.utils.Utils.printElapse;

import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.common.service.UObject;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.WorkspaceClient;

/** Saves test objects of a (approximate) specified size to a preexisting workspace.
 * @author gaprice@lbl.gov
 *
 */
public class SaveTestObjects {
	
//	public static final String WS_URL = "https://ci.kbase.us/services/ws";
	public static final String WS_URL = "http://localhost:7058";
	public static final String WORKSPACE = "TestObjs";
	public static final String TYPE = "Empty.AType-0.1";
	public static final int ITERS = 10;
	public static final int BATCH_SIZE = 1000;
	public static final int APPROX_OBJ_SIZE = 400;

	public static void main(String[] args) throws Exception {
		final String strtoken = args[0];
		final String contents = makeString(400);
		final AuthToken token = AuthService.validateToken(strtoken);
		final WorkspaceClient ws = new WorkspaceClient(new URL(WS_URL), token);
		ws.setIsInsecureHttpConnectionAllowed(true);
		int totalCount = 1;
		for (int i = 0; i < ITERS; i++) {
			final List<ObjectSaveData> objs = new LinkedList<>();
			final SaveObjectsParams sop = new SaveObjectsParams().withWorkspace(WORKSPACE)
					.withObjects(objs);
			for (int j = 0; j < BATCH_SIZE; j++) {
				final Map<String, Object> obj = new HashMap<>();
				obj.put("id", totalCount); // ensure object gets new shocknode
				obj.put("s", contents);
				objs.add(new ObjectSaveData().withName("obj-" + totalCount).withType(TYPE)
						.withData(new UObject(obj)));
				totalCount++;
			}
			final long now = System.nanoTime();
			ws.saveObjects(sop);
			printElapse("save", now);
		}
	}

}

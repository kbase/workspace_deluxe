package sorting;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import us.kbase.auth.AuthConfig;
import us.kbase.auth.AuthToken;
import us.kbase.auth.ConfigurableAuthService;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.UObject;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.WorkspaceClient;

/** Load objects with varying metadata to test that sorting only occurs when expected and does
 * not occur in memory.
 * @author gaprice@lbl.gov
 *
 */
public class LoadSortData {
	
	private static final String WS_NAME = "sorttest3";
	private static final String WS_URL = "http://localhost:20000";
	private static final int VER_COUNT = 10000;
	private static final int OBJ_COUNT = 100;
	private static final String TYPE = "Empty.AType-0.1";
	private static final String AUTH_URL = "https://ci.kbase.us/services/auth/api/legacy/KBase";
	
	private static final String LONG_400;
	static {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 400; i++) {
			sb.append("a");
		}
		LONG_400 = sb.toString();
	}

	public static void main(String[] args) throws Exception {
		final ConfigurableAuthService auth = new ConfigurableAuthService(new AuthConfig()
				.withKBaseAuthServerURL(new URL(AUTH_URL)));
		final AuthToken token = auth.validateToken(args[0]);
		System.out.println("loading objects with token from user " + token.getUserName());
		final Map<String, String> meta = buildBaseMetadata();
		final WorkspaceClient ws = new WorkspaceClient(new URL(WS_URL), token);
		ws.setIsInsecureHttpConnectionAllowed(true);
		final Instant now = Instant.now();
		for (int i = 0; i < OBJ_COUNT; i++) {
			// should really batch these into one call but I'm lazy
			saveObject(ws, "obj" + i, meta);
		}
		for (int i = 0; i < VER_COUNT - OBJ_COUNT; i ++) {
			if (i % 100 == 0) {
				System.out.println(i);
			}
			// should really batch these into one call but I'm still lazy
			saveObject(ws, "obj" + (int) (Math.random() * OBJ_COUNT), meta);
		}
		final Duration done = Duration.between(now, Instant.now());
		System.out.println(String.format("%s.%s sec",
				done.getSeconds(), first3MillisDigits(done)));
	}

	private static String first3MillisDigits(final Duration d) {
		//hack hack hack
		String pre = ((d.getNano() / 1_000_000_000.0) + "").substring(2);
		if (pre.length() < 3) {
			return pre;
		} else {
			return pre.substring(0, 3);
		}
		
	}

	private static void saveObject(
			final WorkspaceClient ws,
			final String name,
			final Map<String, String> meta)
			throws IOException, JsonClientException {
		meta.put("sortkey", "" + (Math.random() * VER_COUNT));
		ws.saveObjects(new SaveObjectsParams()
				.withWorkspace(WS_NAME)
				.withObjects(Arrays.asList(new ObjectSaveData()
						.withData(new UObject(new HashMap<>()))
						.withMeta(meta)
						.withName(name)
						.withType(TYPE))));
	}

	private static Map<String, String> buildBaseMetadata() {
		final Map<String, String> ret = new HashMap<>();
		// adds ~ 10K of metadata
		for (int i = 0; i < 13; i++) {
			ret.put(LONG_400 + i, LONG_400);
		}
		return ret;
	}

}

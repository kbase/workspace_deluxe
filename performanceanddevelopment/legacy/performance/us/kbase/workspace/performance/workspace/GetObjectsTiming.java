package legacy.performance.us.kbase.workspace.performance.workspace;

import static legacy.performance.us.kbase.workspace.performance.utils.Utils.printElapse;

import java.net.URL;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import us.kbase.common.service.Tuple11;
import us.kbase.workspace.GetObjects2Params;
import us.kbase.workspace.ListObjectsParams;
import us.kbase.workspace.ObjectSpecification;
import us.kbase.workspace.WorkspaceClient;

public class GetObjectsTiming {
	
//	public static final String WS_URL = "https://ci.kbase.us/services/ws";
	public static final String WS_URL = "http://localhost:7058";
//	public static final String WORKSPACE = "ReferenceTaxons";
	public static final String WORKSPACE = "TestObjs";
//	public static final String TYPE = "KBaseGenomeAnnotations.Taxon";
	public static final String TYPE = "Empty.AType-0.1";
	
	public static final int ITERS = 20; //10
	public static final long BATCH_SIZE = 10000L;
	public static final int MAX_LIST = 10000;
	
	public static void main(String[] args) throws Exception {
		final WorkspaceClient ws = new WorkspaceClient(new URL(WS_URL));
		for (int i = 0; i < ITERS; i++) {
			final long preiter = System.nanoTime();
			long totalsize = 0;
			final List<ObjectSpecification> in = new LinkedList<>();
			final long end = (i + 1) * BATCH_SIZE;
			for (long j = i * BATCH_SIZE; j < end; j += MAX_LIST) {
				final long maxid = j + MAX_LIST > end ? end : j + MAX_LIST;
				final long prelist = System.nanoTime();
				final List<Tuple11<Long, String, String, String, Long, String, Long, String,
						String, Long, Map<String, String>>> oi = ws.listObjects(
								new ListObjectsParams()
									.withWorkspaces(Arrays.asList(WORKSPACE))
									.withType(TYPE)
									.withMinObjectID(j + 1)
									.withMaxObjectID(maxid)
								);
				printElapse(String.format("list iter %s:%s", i, j), prelist);
				for (final Tuple11<Long, String, String, String, Long, String, Long, String,
						String, Long, Map<String, String>> t: oi) {
					in.add(new ObjectSpecification()
							.withWsid(t.getE7())
							.withObjid(t.getE1())
							.withVer(t.getE5())
							);
					totalsize += t.getE10();
				}
			}
			System.out.println(String.format("Objects: #%s, %sMB", in.size(),
					totalsize / 1000000.0));
			final long preget = System.nanoTime();
			ws.getObjects2(new GetObjects2Params().withObjects(in));
			printElapse("get", preget);
			printElapse("iter", preiter);
		}
	}
	
}

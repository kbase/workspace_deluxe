package performance.nameByPrefix;

import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import us.kbase.common.service.ServerException;
import us.kbase.common.service.UObject;
import us.kbase.workspace.CloneWorkspaceParams;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceIdentity;

/** Set up objects to test speed of the name_by_prefix function. Assumes
 *  Empty.AType-0.1 (which has no restrictions on the save object, e.g. 1
 *  optional field) exists in the target workspace.
 * @author gaprice@lbl.gov
 *
 */
public class NamebyPrefix {
	
	public static final String TYPE = "Empty.AType-0.1";
	public static final String wsURL = "http://localhost:7058";
	public static final String ORIGINAL_WORKSPACE_NAME =
			"getnamesbyprefix_original";
	public static final int CLONE_COUNT = 9;
	public static final int READ_TIME_OUT = 60 * 60 * 1000; // 1hr in ms
	
	public static String PASSWORD;
	
	public static void main(String[] args) throws Exception {
		PASSWORD = args[0];
		try {
			loadOneMillion();
		} catch (ServerException se) {
			System.out.println(se.getData());
			throw se;
		}
	}
	
	private static String intToName(int in) {
		final char[] seq = new char[4];
		final int start = (int) 'a';
		final int abc = 26;
		for (int i = seq.length - 1; i >= 0; i--) {
			seq[i] = (char)(start + in % abc);
			in = in / abc;
		}
		return new String(seq);
	}
	
	private static void loadOneMillion() throws Exception {
		WorkspaceClient cli = new WorkspaceClient(new URL(wsURL), "kbasetest",
				PASSWORD);
		cli.setIsInsecureHttpConnectionAllowed(true);
		cli.setConnectionReadTimeOut(READ_TIME_OUT);
		cli.createWorkspace(new CreateWorkspaceParams().withGlobalread("r")
				.withWorkspace(ORIGINAL_WORKSPACE_NAME));
		List<ObjectSaveData> o = new LinkedList<ObjectSaveData>();
		for (int i = 0; i < 100000; i++) {
			o.add(new ObjectSaveData()
					.withData(new UObject(new HashMap<String, String>()))
					.withName(intToName(i))
					.withType(TYPE));
		}
		for (int i = 0; i < 10; i++) {
			System.out.println(String.format("saving objects from %s to %s",
					i * 10000, (i + 1) * 10000 - 1));
			cli.saveObjects(new SaveObjectsParams()
					.withWorkspace(ORIGINAL_WORKSPACE_NAME)
					// sublist is inclusive to exclusive
					.withObjects(o.subList(i * 10000, (i + 1) * 10000)));
		}
		
		for (int i = 1; i <= CLONE_COUNT; i++) {
			System.out.println(String.format(
					"Clone %s of %s.", i, CLONE_COUNT));
			cli.cloneWorkspace(new CloneWorkspaceParams().withGlobalread("r")
					.withWsi(new WorkspaceIdentity()
							.withWorkspace(ORIGINAL_WORKSPACE_NAME))
					.withWorkspace(ORIGINAL_WORKSPACE_NAME + (i + 1)));
			}
		System.out.println("Done");

	}

}

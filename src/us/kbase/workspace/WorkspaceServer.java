package us.kbase.workspace;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import us.kbase.auth.AuthToken;
import us.kbase.common.service.JsonServerMethod;
import us.kbase.common.service.JsonServerServlet;
import us.kbase.common.service.JsonServerSyslog;
import us.kbase.common.service.RpcContext;
import us.kbase.common.service.Tuple11;
import us.kbase.common.service.Tuple12;
import us.kbase.common.service.Tuple7;
import us.kbase.common.service.Tuple9;
import us.kbase.common.service.UObject;

//BEGIN_HEADER
import us.kbase.common.service.ServiceChecker;
import us.kbase.common.service.ServiceChecker.ServiceException;
import static us.kbase.common.utils.ServiceUtils.checkAddlArgs;
import static us.kbase.workspace.kbase.ArgUtils.getGlobalWSPerm;
import static us.kbase.workspace.kbase.ArgUtils.wsInfoToTuple;
import static us.kbase.workspace.kbase.ArgUtils.wsInfoToMetaTuple;
import static us.kbase.workspace.kbase.ArgUtils.objInfoToMetaTuple;
import static us.kbase.workspace.kbase.ArgUtils.translateObjectProvInfo;
import static us.kbase.workspace.kbase.ArgUtils.translateObjectData;
import static us.kbase.workspace.kbase.ArgUtils.objInfoToTuple;
import static us.kbase.workspace.kbase.ArgUtils.translateObjectInfoList;
import static us.kbase.workspace.kbase.ArgUtils.longToBoolean;
import static us.kbase.workspace.kbase.IdentifierUtils.processObjectIdentifier;
import static us.kbase.workspace.kbase.IdentifierUtils.processObjectIdentifiers;
import static us.kbase.workspace.kbase.IdentifierUtils.processObjectSpecifications;
import static us.kbase.workspace.kbase.IdentifierUtils.processSubObjectIdentifiers;
import static us.kbase.workspace.kbase.IdentifierUtils.processWorkspaceIdentifier;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

//import org.apache.commons.lang3.builder.ToStringBuilder;

import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.db.FuncDetailedInfo;
import us.kbase.typedobj.db.ModuleDefId;
import us.kbase.typedobj.db.TypeChange;
import us.kbase.typedobj.db.TypeDetailedInfo;
import us.kbase.workspace.database.DependencyStatus;
import us.kbase.workspace.database.ListObjectsParameters;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder.ResourceUsageConfiguration;
import us.kbase.workspace.database.ObjectIDWithRefPath;
import us.kbase.workspace.database.Types;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.WorkspaceUserMetadata;
import us.kbase.workspace.kbase.InitWorkspaceServer.InitReporter;
import us.kbase.workspace.kbase.InitWorkspaceServer;
import us.kbase.workspace.kbase.InitWorkspaceServer.WorkspaceInitResults;
import us.kbase.workspace.kbase.KBaseWorkspaceConfig;
import us.kbase.workspace.kbase.WorkspaceAdministration;
import us.kbase.workspace.kbase.WorkspaceServerMethods;
//END_HEADER

/**
 * <p>Original spec-file module name: Workspace</p>
 * <pre>
 * The Workspace Service (WSS) is primarily a language independent remote storage
 * and retrieval system for KBase typed objects (TO) defined with the KBase
 * Interface Description Language (KIDL). It has the following primary features:
 * - Immutable storage of TOs with
 *         - user defined metadata 
 *         - data provenance
 * - Versioning of TOs
 * - Referencing from TO to TO
 * - Typechecking of all saved objects against a KIDL specification
 * - Collecting typed objects into a workspace
 * - Sharing workspaces with specific KBase users or the world
 * - Freezing and publishing workspaces
 * </pre>
 */
public class WorkspaceServer extends JsonServerServlet {
    private static final long serialVersionUID = 1L;
    private static final String version = "0.0.1";
    private static final String gitUrl = "https://github.com/mrcreosote/workspace_deluxe";
    private static final String gitCommitHash = "69c528958723a8c279ade8302b6490d9f7aca763";

    //BEGIN_CLASS_HEADER
	//TODO JAVADOC really low priority, sorry
	//TODO INIT timestamps for startup script

	private static final String VER = "0.8.0-dev4";
	private static final String GIT =
			"https://github.com/kbase/workspace_deluxe";

	private static final long MAX_RPC_PACKAGE_SIZE = 1005000000;
	private static final int MAX_RPC_PACKAGE_MEM_USE = 100000000;
	
	private static Map<String, String> wsConfig = null;
	
	private final Workspace ws;
	private final WorkspaceServerMethods wsmeth;
	private final Types types;
	private final WorkspaceAdministration wsadmin;
	
	private final URL handleManagerUrl;
	private final AuthToken handleMgrToken;
	
	private ThreadLocal<List<WorkspaceObjectData>> resourcesToDelete =
			new ThreadLocal<List<WorkspaceObjectData>>();
	
	
	public static void clearConfigForTests() {
		wsConfig = null;
	}
	
	@Override
	protected File generateTempFile() {
		return ws.getTempFilesManager().generateTempFile("rpc", "json");
	}
	
	public TempFilesManager getTempFilesManager() {
		return ws.getTempFilesManager();
	}

	@Override
	protected void onRpcMethodDone() {
		if (resourcesToDelete.get() != null &&
				!resourcesToDelete.get().isEmpty()) {
			for (final WorkspaceObjectData o : resourcesToDelete.get())
				try {
					o.destroy();
				} catch (Exception ignore) {}
			resourcesToDelete.set(null);
		}
	}
	
	public ResourceUsageConfiguration getWorkspaceResourceUsageConfig() {
		return ws.getResourceConfig();
	}
	
	public void setResourceUsageConfiguration(
			final ResourceUsageConfiguration cfg) {
		ws.setResourceConfig(cfg);
	}
	
	public void setUpLogger() {
		final Logger rootLogger = ((Logger) LoggerFactory.getLogger(
				org.slf4j.Logger.ROOT_LOGGER_NAME));
		rootLogger.setLevel(Level.OFF);
		rootLogger.detachAndStopAllAppenders();
	}
	
	private class WorkspaceInitReporter extends InitReporter {

		@Override
		public void reportInfo(final String info) {
			logInfo(info);
			System.out.println(info);
		}

		@Override
		public void handleFail(final String fail) {
			logErr(fail);
			System.out.println(fail);
			startupFailed();
		}
		
	}
	
	public DependencyStatus checkHandleManager() {
		try {
			ServiceChecker.checkService(handleManagerUrl);
			return new DependencyStatus(
					true, "OK", "Handle manager", "Unknown");
		} catch (ServiceException se) {
			//tested manually, don't change without testing
			return new DependencyStatus(
					false, se.getMessage(), "Handle manager", "Unknown");
		}
	}
	
    //END_CLASS_HEADER

    public WorkspaceServer() throws Exception {
        super("Workspace");
        //BEGIN_CONSTRUCTOR
		setUpLogger();
		setMaxRPCPackageSize(MAX_RPC_PACKAGE_SIZE);
		setMaxRpcMemoryCacheSize(MAX_RPC_PACKAGE_MEM_USE);
		//assign config once per jvm, otherwise you could wind up with
		//different threads talking to different mongo instances
		//E.g. first thread's config applies to all threads.
		if (wsConfig == null) {
			wsConfig = new HashMap<String, String>();
			wsConfig.putAll(super.config);
		}
		
		final KBaseWorkspaceConfig cfg = new KBaseWorkspaceConfig(wsConfig);
		for (final String info: cfg.getInfoMessages()) {
			logInfo(info);
			System.out.println(info);
		}
		for (final String error: cfg.getErrors()) {
			logErr(error);
			System.out.println(error);
		}
		
		Workspace ws = null;
		WorkspaceServerMethods wsmeth = null;
		Types types = null;
		WorkspaceAdministration wsadmin = null;
		URL handleManagerUrl = null;
		AuthToken handleMgrToken = null;
		//TODO TEST add server startup tests
		if (cfg.hasErrors()) {
			logErr("Workspace server configuration has errors - all calls will fail");
			System.out.println(
					"Workspace server configuration has errors - all calls will fail");
			startupFailed();
		} else {

			final WorkspaceInitReporter rep = new WorkspaceInitReporter();
			final WorkspaceInitResults res =
					InitWorkspaceServer.initWorkspaceServer(cfg, rep);

			if (!rep.isFailed()) {
				ws = res.getWs();
				wsmeth = res.getWsmeth();
				types = res.getTypes();
				wsadmin = res.getWsAdmin();
				handleManagerUrl = res.getHandleManagerUrl();
				handleMgrToken = res.getHandleMgrToken();
				setRpcDiskCacheTempDir(ws.getTempFilesManager().getTempDir());
			}
		}
		this.ws = ws;
		this.wsmeth = wsmeth;
		this.types = types;
		this.wsadmin = wsadmin;
		this.handleManagerUrl = handleManagerUrl;
		this.handleMgrToken = handleMgrToken;
        //END_CONSTRUCTOR
    }

    /**
     * <p>Original spec-file function name: ver</p>
     * <pre>
     * Returns the version of the workspace service.
     * </pre>
     * @return   parameter "ver" of String
     */
    @JsonServerMethod(rpc = "Workspace.ver", async=true)
    public String ver(RpcContext jsonRpcContext) throws Exception {
        String returnVal = null;
        //BEGIN ver
		returnVal = VER;
        //END ver
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: create_workspace</p>
     * <pre>
     * Creates a new workspace.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.CreateWorkspaceParams CreateWorkspaceParams}
     * @return   parameter "info" of original type "workspace_info" (Information about a workspace. ws_id id - the numerical ID of the workspace. ws_name workspace - name of the workspace. username owner - name of the user who owns (e.g. created) this workspace. timestamp moddate - date when the workspace was last modified. int max_objid - the maximum object ID appearing in this workspace. Since cloning a workspace preserves object IDs, this number may be greater than the number of objects in a newly cloned workspace. permission user_permission - permissions for the authenticated user of this workspace. permission globalread - whether this workspace is globally readable. lock_status lockstat - the status of the workspace lock. usermeta metadata - arbitrary user-supplied metadata about the workspace.) &rarr; tuple of size 9: parameter "id" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_", ".", or "-" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "owner" of original type "username" (Login name of a KBase user account.), parameter "moddate" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is either the character Z (representing the UTC timezone) or the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time) 2013-04-03T08:56:32Z (UTC time)), parameter "max_objid" of Long, parameter "user_permission" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "globalread" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "lockstat" of original type "lock_status" (The lock status of a workspace. One of 'unlocked', 'locked', or 'published'.), parameter "metadata" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String
     */
    @JsonServerMethod(rpc = "Workspace.create_workspace", async=true)
    public Tuple9<Long, String, String, String, Long, String, String, String, Map<String,String>> createWorkspace(CreateWorkspaceParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        Tuple9<Long, String, String, String, Long, String, String, String, Map<String,String>> returnVal = null;
        //BEGIN create_workspace
		returnVal = wsmeth.createWorkspace(params, wsmeth.getUser(authPart));
        //END create_workspace
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: alter_workspace_metadata</p>
     * <pre>
     * Change the metadata associated with a workspace.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.AlterWorkspaceMetadataParams AlterWorkspaceMetadataParams}
     */
    @JsonServerMethod(rpc = "Workspace.alter_workspace_metadata", async=true)
    public void alterWorkspaceMetadata(AlterWorkspaceMetadataParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        //BEGIN alter_workspace_metadata
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		final WorkspaceUserMetadata meta = params.getNew() == null || params.getNew().isEmpty() ?
				null : new WorkspaceUserMetadata(params.getNew());
		final List<String> remove = params.getRemove() == null || params.getRemove().isEmpty() ?
				null : params.getRemove();
		
		if (meta == null && remove == null) {
			throw new IllegalArgumentException(
					"Must provide metadata keys to add or remove");
		}
		final WorkspaceIdentifier wsi = processWorkspaceIdentifier(params.getWsi());
		final WorkspaceUser user = wsmeth.getUser(authPart);
		ws.setWorkspaceMetadata(user, wsi, meta, remove);
        //END alter_workspace_metadata
    }

    /**
     * <p>Original spec-file function name: clone_workspace</p>
     * <pre>
     * Clones a workspace.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.CloneWorkspaceParams CloneWorkspaceParams}
     * @return   parameter "info" of original type "workspace_info" (Information about a workspace. ws_id id - the numerical ID of the workspace. ws_name workspace - name of the workspace. username owner - name of the user who owns (e.g. created) this workspace. timestamp moddate - date when the workspace was last modified. int max_objid - the maximum object ID appearing in this workspace. Since cloning a workspace preserves object IDs, this number may be greater than the number of objects in a newly cloned workspace. permission user_permission - permissions for the authenticated user of this workspace. permission globalread - whether this workspace is globally readable. lock_status lockstat - the status of the workspace lock. usermeta metadata - arbitrary user-supplied metadata about the workspace.) &rarr; tuple of size 9: parameter "id" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_", ".", or "-" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "owner" of original type "username" (Login name of a KBase user account.), parameter "moddate" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is either the character Z (representing the UTC timezone) or the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time) 2013-04-03T08:56:32Z (UTC time)), parameter "max_objid" of Long, parameter "user_permission" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "globalread" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "lockstat" of original type "lock_status" (The lock status of a workspace. One of 'unlocked', 'locked', or 'published'.), parameter "metadata" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String
     */
    @JsonServerMethod(rpc = "Workspace.clone_workspace", async=true)
    public Tuple9<Long, String, String, String, Long, String, String, String, Map<String,String>> cloneWorkspace(CloneWorkspaceParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        Tuple9<Long, String, String, String, Long, String, String, String, Map<String,String>> returnVal = null;
        //BEGIN clone_workspace
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		Set<ObjectIDNoWSNoVer> exclude = null;
		if (params.getExclude() != null && !params.getExclude().isEmpty()) {
			exclude = new HashSet<ObjectIDNoWSNoVer>();
			int count = 1;
			for (final ObjectIdentity o: params.getExclude()) {
				try {
					exclude.add(ObjectIDNoWSNoVer.create(
							o.getName(), o.getObjid()));
				} catch (IllegalArgumentException e) {
					throw new IllegalArgumentException(String.format(
							"Error with excluded object #%s: %s",
							count, e.getLocalizedMessage()), e);
				}
				count++;
			}
		}
		final Permission p = getGlobalWSPerm(params.getGlobalread());
		final WorkspaceIdentifier wsi =
				processWorkspaceIdentifier(params.getWsi());
		final WorkspaceInformation meta = ws.cloneWorkspace(
				wsmeth.getUser(authPart),
				wsi,
				params.getWorkspace(),
				p.equals(Permission.READ),
				params.getDescription(),
				new WorkspaceUserMetadata(params.getMeta()),
				exclude);
		returnVal = wsInfoToTuple(meta);
        //END clone_workspace
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: lock_workspace</p>
     * <pre>
     * Lock a workspace, preventing further changes.
     *         WARNING: Locking a workspace is permanent. A workspace, once locked,
     *         cannot be unlocked.
     *         
     *         The only changes allowed for a locked workspace are changing user
     *         based permissions or making a private workspace globally readable,
     *         thus permanently publishing the workspace. A locked, globally readable
     *         workspace cannot be made private.
     * </pre>
     * @param   wsi   instance of type {@link us.kbase.workspace.WorkspaceIdentity WorkspaceIdentity}
     * @return   parameter "info" of original type "workspace_info" (Information about a workspace. ws_id id - the numerical ID of the workspace. ws_name workspace - name of the workspace. username owner - name of the user who owns (e.g. created) this workspace. timestamp moddate - date when the workspace was last modified. int max_objid - the maximum object ID appearing in this workspace. Since cloning a workspace preserves object IDs, this number may be greater than the number of objects in a newly cloned workspace. permission user_permission - permissions for the authenticated user of this workspace. permission globalread - whether this workspace is globally readable. lock_status lockstat - the status of the workspace lock. usermeta metadata - arbitrary user-supplied metadata about the workspace.) &rarr; tuple of size 9: parameter "id" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_", ".", or "-" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "owner" of original type "username" (Login name of a KBase user account.), parameter "moddate" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is either the character Z (representing the UTC timezone) or the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time) 2013-04-03T08:56:32Z (UTC time)), parameter "max_objid" of Long, parameter "user_permission" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "globalread" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "lockstat" of original type "lock_status" (The lock status of a workspace. One of 'unlocked', 'locked', or 'published'.), parameter "metadata" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String
     */
    @JsonServerMethod(rpc = "Workspace.lock_workspace", async=true)
    public Tuple9<Long, String, String, String, Long, String, String, String, Map<String,String>> lockWorkspace(WorkspaceIdentity wsi, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        Tuple9<Long, String, String, String, Long, String, String, String, Map<String,String>> returnVal = null;
        //BEGIN lock_workspace
		final WorkspaceIdentifier wsid = processWorkspaceIdentifier(wsi);
		returnVal = wsInfoToTuple(ws.lockWorkspace(
				wsmeth.getUser(authPart), wsid));
        //END lock_workspace
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_workspacemeta</p>
     * <pre>
     * Retrieves the metadata associated with the specified workspace.
     * Provided for backwards compatibility. 
     * @deprecated Workspace.get_workspace_info
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.GetWorkspacemetaParams GetWorkspacemetaParams} (original type "get_workspacemeta_params")
     * @return   parameter "metadata" of original type "workspace_metadata" (Meta data associated with a workspace. Provided for backwards compatibility. To be replaced by workspace_info. ws_name id - name of the workspace username owner - name of the user who owns (who created) this workspace timestamp moddate - date when the workspace was last modified int objects - the approximate number of objects currently stored in the workspace. permission user_permission - permissions for the currently logged in user for the workspace permission global_permission - default permissions for the workspace for all KBase users ws_id num_id - numerical ID of the workspace @deprecated Workspace.workspace_info) &rarr; tuple of size 7: parameter "id" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_", ".", or "-" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "owner" of original type "username" (Login name of a KBase user account.), parameter "moddate" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is either the character Z (representing the UTC timezone) or the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time) 2013-04-03T08:56:32Z (UTC time)), parameter "objects" of Long, parameter "user_permission" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "global_permission" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "num_id" of original type "ws_id" (The unique, permanent numerical ID of a workspace.)
     */
    @JsonServerMethod(rpc = "Workspace.get_workspacemeta", authOptional=true, async=true)
    public Tuple7<String, String, String, Long, String, String, Long> getWorkspacemeta(GetWorkspacemetaParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        Tuple7<String, String, String, Long, String, String, Long> returnVal = null;
        //BEGIN get_workspacemeta
		checkAddlArgs(params.getAdditionalProperties(), GetWorkspacemetaParams.class);
		final WorkspaceIdentifier wksp = processWorkspaceIdentifier(
				params.getWorkspace(), params.getId());
		final WorkspaceInformation meta = ws.getWorkspaceInformation(
				wsmeth.getUser(params.getAuth(), authPart), wksp);
		returnVal = wsInfoToMetaTuple(meta);
        //END get_workspacemeta
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_workspace_info</p>
     * <pre>
     * Get information associated with a workspace.
     * </pre>
     * @param   wsi   instance of type {@link us.kbase.workspace.WorkspaceIdentity WorkspaceIdentity}
     * @return   parameter "info" of original type "workspace_info" (Information about a workspace. ws_id id - the numerical ID of the workspace. ws_name workspace - name of the workspace. username owner - name of the user who owns (e.g. created) this workspace. timestamp moddate - date when the workspace was last modified. int max_objid - the maximum object ID appearing in this workspace. Since cloning a workspace preserves object IDs, this number may be greater than the number of objects in a newly cloned workspace. permission user_permission - permissions for the authenticated user of this workspace. permission globalread - whether this workspace is globally readable. lock_status lockstat - the status of the workspace lock. usermeta metadata - arbitrary user-supplied metadata about the workspace.) &rarr; tuple of size 9: parameter "id" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_", ".", or "-" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "owner" of original type "username" (Login name of a KBase user account.), parameter "moddate" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is either the character Z (representing the UTC timezone) or the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time) 2013-04-03T08:56:32Z (UTC time)), parameter "max_objid" of Long, parameter "user_permission" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "globalread" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "lockstat" of original type "lock_status" (The lock status of a workspace. One of 'unlocked', 'locked', or 'published'.), parameter "metadata" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String
     */
    @JsonServerMethod(rpc = "Workspace.get_workspace_info", authOptional=true, async=true)
    public Tuple9<Long, String, String, String, Long, String, String, String, Map<String,String>> getWorkspaceInfo(WorkspaceIdentity wsi, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        Tuple9<Long, String, String, String, Long, String, String, String, Map<String,String>> returnVal = null;
        //BEGIN get_workspace_info
		final WorkspaceIdentifier wksp = processWorkspaceIdentifier(wsi);
		final WorkspaceInformation meta = ws.getWorkspaceInformation(
				wsmeth.getUser(authPart), wksp);
		returnVal = wsInfoToTuple(meta);
        //END get_workspace_info
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_workspace_description</p>
     * <pre>
     * Get a workspace's description.
     * </pre>
     * @param   wsi   instance of type {@link us.kbase.workspace.WorkspaceIdentity WorkspaceIdentity}
     * @return   parameter "description" of String
     */
    @JsonServerMethod(rpc = "Workspace.get_workspace_description", authOptional=true, async=true)
    public String getWorkspaceDescription(WorkspaceIdentity wsi, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        String returnVal = null;
        //BEGIN get_workspace_description
		final WorkspaceIdentifier wksp = processWorkspaceIdentifier(wsi);
		returnVal = ws.getWorkspaceDescription(wsmeth.getUser(authPart), wksp);
        //END get_workspace_description
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: set_permissions</p>
     * <pre>
     * Set permissions for a workspace.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.SetPermissionsParams SetPermissionsParams}
     */
    @JsonServerMethod(rpc = "Workspace.set_permissions", async=true)
    public void setPermissions(SetPermissionsParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        //BEGIN set_permissions
		wsmeth.setPermissions(params, authPart);
        //END set_permissions
    }

    /**
     * <p>Original spec-file function name: set_global_permission</p>
     * <pre>
     * Set the global permission for a workspace.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.SetGlobalPermissionsParams SetGlobalPermissionsParams}
     */
    @JsonServerMethod(rpc = "Workspace.set_global_permission", async=true)
    public void setGlobalPermission(SetGlobalPermissionsParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        //BEGIN set_global_permission
		wsmeth.setGlobalPermission(params, wsmeth.getUser(authPart));
        //END set_global_permission
    }

    /**
     * <p>Original spec-file function name: set_workspace_description</p>
     * <pre>
     * Set the description for a workspace.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.SetWorkspaceDescriptionParams SetWorkspaceDescriptionParams}
     */
    @JsonServerMethod(rpc = "Workspace.set_workspace_description", async=true)
    public void setWorkspaceDescription(SetWorkspaceDescriptionParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        //BEGIN set_workspace_description
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		final WorkspaceIdentifier wsi = processWorkspaceIdentifier(
				params.getWorkspace(), params.getId());
		ws.setWorkspaceDescription(wsmeth.getUser(authPart), wsi,
				params.getDescription());
        //END set_workspace_description
    }

    /**
     * <p>Original spec-file function name: get_permissions_mass</p>
     * <pre>
     * Get permissions for multiple workspaces.
     * </pre>
     * @param   mass   instance of type {@link us.kbase.workspace.GetPermissionsMassParams GetPermissionsMassParams}
     * @return   parameter "perms" of type {@link us.kbase.workspace.WorkspacePermissions WorkspacePermissions}
     */
    @JsonServerMethod(rpc = "Workspace.get_permissions_mass", authOptional=true, async=true)
    public WorkspacePermissions getPermissionsMass(GetPermissionsMassParams mass, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        WorkspacePermissions returnVal = null;
        //BEGIN get_permissions_mass
		checkAddlArgs(mass.getAdditionalProperties(), mass.getClass());
		returnVal = wsmeth.getPermissions(mass.getWorkspaces(), wsmeth.getUser(authPart), false);
        //END get_permissions_mass
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_permissions</p>
     * <pre>
     * Get permissions for a workspace.
     * @deprecated get_permissions_mass
     * </pre>
     * @param   wsi   instance of type {@link us.kbase.workspace.WorkspaceIdentity WorkspaceIdentity}
     * @return   parameter "perms" of mapping from original type "username" (Login name of a KBase user account.) to original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.)
     */
    @JsonServerMethod(rpc = "Workspace.get_permissions", authOptional=true, async=true)
    public Map<String,String> getPermissions(WorkspaceIdentity wsi, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        Map<String,String> returnVal = null;
        //BEGIN get_permissions
        returnVal = wsmeth.getPermissions(wsi, wsmeth.getUser(authPart));
        //END get_permissions
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: save_object</p>
     * <pre>
     * Saves the input object data and metadata into the selected workspace,
     *         returning the object_metadata of the saved object. Provided
     *         for backwards compatibility.
     *         
     * @deprecated Workspace.save_objects
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.SaveObjectParams SaveObjectParams} (original type "save_object_params")
     * @return   parameter "metadata" of original type "object_metadata" (Meta data associated with an object stored in a workspace. Provided for backwards compatibility. obj_name id - name of the object. type_string type - type of the object. timestamp moddate - date when the object was saved obj_ver instance - the version of the object string command - Deprecated. Always returns the empty string. username lastmodifier - name of the user who last saved the object, including copying the object username owner - Deprecated. Same as lastmodifier. ws_name workspace - name of the workspace in which the object is stored string ref - Deprecated. Always returns the empty string. string chsum - the md5 checksum of the object. usermeta metadata - arbitrary user-supplied metadata about the object. obj_id objid - the numerical id of the object. @deprecated object_info) &rarr; tuple of size 12: parameter "id" of original type "obj_name" (A string used as a name for an object. Any string consisting of alphanumeric characters and the characters |._- that is not an integer is acceptable.), parameter "type" of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1), parameter "moddate" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is either the character Z (representing the UTC timezone) or the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time) 2013-04-03T08:56:32Z (UTC time)), parameter "instance" of Long, parameter "command" of String, parameter "lastmodifier" of original type "username" (Login name of a KBase user account.), parameter "owner" of original type "username" (Login name of a KBase user account.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_", ".", or "-" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "ref" of String, parameter "chsum" of String, parameter "metadata" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String, parameter "objid" of original type "obj_id" (The unique, permanent numerical ID of an object.)
     */
    @JsonServerMethod(rpc = "Workspace.save_object", authOptional=true, async=true)
    public Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String,String>, Long> saveObject(SaveObjectParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String,String>, Long> returnVal = null;
        //BEGIN save_object
		final SaveObjectsParams sop = new SaveObjectsParams()
			.withWorkspace(params.getWorkspace()).withObjects(Arrays.asList(
					new ObjectSaveData().withData(params.getData())
						.withMeta(params.getMetadata())
						.withName(params.getId())
						.withType(params.getType())));
		if (params.getAuth() != null) {
			authPart = wsmeth.getAuth().validateToken(params.getAuth());
		}
		final Tuple11<Long, String, String, String, Long, String, Long, String,
				String, Long, Map<String, String>> meta = saveObjects(
						sop, authPart, jsonRpcContext).get(0);
		returnVal = new Tuple12<String, String, String, Long, String, String,
				String, String, String, String, Map<String,String>, Long>()
				.withE1(meta.getE2()) //object name
				.withE2(meta.getE3()) //type
				.withE3(meta.getE4()) //time
				.withE4(meta.getE5()) //ver
				.withE5("") //command, deprecated
				.withE6(meta.getE6()) //last mod
				.withE7(meta.getE6()) //owner, deprecated
				.withE8(meta.getE8()) //workspace name
				.withE9("") //ref, deprecated
				.withE10(meta.getE9()) //chsum
				.withE11(meta.getE11()) //meta
				.withE12(meta.getE1()); // object id
        //END save_object
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: save_objects</p>
     * <pre>
     * Save objects to the workspace. Saving over a deleted object undeletes
     * it.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.SaveObjectsParams SaveObjectsParams}
     * @return   parameter "info" of list of original type "object_info" (Information about an object, including user provided metadata. obj_id objid - the numerical id of the object. obj_name name - the name of the object. type_string type - the type of the object. timestamp save_date - the save date of the object. obj_ver ver - the version of the object. username saved_by - the user that saved or copied the object. ws_id wsid - the workspace containing the object. ws_name workspace - the workspace containing the object. string chsum - the md5 checksum of the object. int size - the size of the object in bytes. usermeta meta - arbitrary user-supplied metadata about the object.) &rarr; tuple of size 11: parameter "objid" of original type "obj_id" (The unique, permanent numerical ID of an object.), parameter "name" of original type "obj_name" (A string used as a name for an object. Any string consisting of alphanumeric characters and the characters |._- that is not an integer is acceptable.), parameter "type" of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1), parameter "save_date" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is either the character Z (representing the UTC timezone) or the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time) 2013-04-03T08:56:32Z (UTC time)), parameter "version" of Long, parameter "saved_by" of original type "username" (Login name of a KBase user account.), parameter "wsid" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_", ".", or "-" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "chsum" of String, parameter "size" of Long, parameter "meta" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String
     */
    @JsonServerMethod(rpc = "Workspace.save_objects", async=true)
    public List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>> saveObjects(SaveObjectsParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>> returnVal = null;
        //BEGIN save_objects
		returnVal = wsmeth.saveObjects(params, wsmeth.getUser(authPart), authPart);
        //END save_objects
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_object</p>
     * <pre>
     * Retrieves the specified object from the specified workspace.
     * Both the object data and metadata are returned.
     * Provided for backwards compatibility.
     * @deprecated Workspace.get_objects
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.GetObjectParams GetObjectParams} (original type "get_object_params")
     * @return   parameter "output" of type {@link us.kbase.workspace.GetObjectOutput GetObjectOutput} (original type "get_object_output")
     */
    @JsonServerMethod(rpc = "Workspace.get_object", authOptional=true, async=true)
    public GetObjectOutput getObject(GetObjectParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        GetObjectOutput returnVal = null;
        //BEGIN get_object
		final ObjectIdentifier oi = processObjectIdentifier(
				params.getWorkspace(), null, params.getId(), null,
				params.getInstance());
		final WorkspaceObjectData ret = ws.getObjects(
				wsmeth.getUser(params.getAuth(), authPart),
				Arrays.asList(oi)).get(0);
		resourcesToDelete.set(Arrays.asList(ret));
		returnVal = new GetObjectOutput()
			.withData(ret.getSerializedData().getUObject())
			.withMetadata(objInfoToMetaTuple(ret.getObjectInfo(), true));
        //END get_object
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_object_provenance</p>
     * <pre>
     * DEPRECATED
     * Get object provenance from the workspace.
     * @deprecated Workspace.get_objects2
     * </pre>
     * @param   objectIds   instance of list of type {@link us.kbase.workspace.ObjectIdentity ObjectIdentity}
     * @return   parameter "data" of list of type {@link us.kbase.workspace.ObjectProvenanceInfo ObjectProvenanceInfo}
     */
    @JsonServerMethod(rpc = "Workspace.get_object_provenance", authOptional=true, async=true)
    public List<ObjectProvenanceInfo> getObjectProvenance(List<ObjectIdentity> objectIds, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        List<ObjectProvenanceInfo> returnVal = null;
        //BEGIN get_object_provenance
		final List<ObjectIdentifier> loi = processObjectIdentifiers(objectIds);
		returnVal = translateObjectProvInfo(
				ws.getObjects(wsmeth.getUser(authPart), loi, true),
						wsmeth.getUser(authPart), handleManagerUrl,
						handleMgrToken, true);
        //END get_object_provenance
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_objects</p>
     * <pre>
     * DEPRECATED
     * Get objects from the workspace.
     * @deprecated Workspace.get_objects2
     * </pre>
     * @param   objectIds   instance of list of type {@link us.kbase.workspace.ObjectIdentity ObjectIdentity}
     * @return   parameter "data" of list of type {@link us.kbase.workspace.ObjectData ObjectData}
     */
    @JsonServerMethod(rpc = "Workspace.get_objects", authOptional=true, async=true)
    public List<ObjectData> getObjects(List<ObjectIdentity> objectIds, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        List<ObjectData> returnVal = null;
        //BEGIN get_objects
		final List<ObjectIdentifier> loi = processObjectIdentifiers(objectIds);
		final List<WorkspaceObjectData> objects =
				ws.getObjects(wsmeth.getUser(authPart), loi);
		resourcesToDelete.set(objects);
		returnVal = translateObjectData(objects, wsmeth.getUser(authPart),
					handleManagerUrl, handleMgrToken, true);
        //END get_objects
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_objects2</p>
     * <pre>
     * Get objects from the workspace.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.GetObjects2Params GetObjects2Params}
     * @return   parameter "results" of type {@link us.kbase.workspace.GetObjects2Results GetObjects2Results}
     */
    @JsonServerMethod(rpc = "Workspace.get_objects2", authOptional=true, async=true)
    public GetObjects2Results getObjects2(GetObjects2Params params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        GetObjects2Results returnVal = null;
        //BEGIN get_objects2
		returnVal = wsmeth.getObjects(params, wsmeth.getUser(authPart), false, resourcesToDelete);
        //END get_objects2
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_object_subset</p>
     * <pre>
     * DEPRECATED
     * Get portions of objects from the workspace.
     * When selecting a subset of an array in an object, the returned
     * array is compressed to the size of the subset, but the ordering of
     * the array is maintained. For example, if the array stored at the
     * 'feature' key of a Genome object has 4000 entries, and the object paths
     * provided are:
     *         /feature/7
     *         /feature/3015
     *         /feature/700
     * The returned feature array will be of length three and the entries will
     * consist, in order, of the 7th, 700th, and 3015th entries of the
     * original array.
     * @deprecated Workspace.get_objects2
     * </pre>
     * @param   subObjectIds   instance of list of type {@link us.kbase.workspace.SubObjectIdentity SubObjectIdentity}
     * @return   parameter "data" of list of type {@link us.kbase.workspace.ObjectData ObjectData}
     */
    @JsonServerMethod(rpc = "Workspace.get_object_subset", authOptional=true, async=true)
    public List<ObjectData> getObjectSubset(List<SubObjectIdentity> subObjectIds, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        List<ObjectData> returnVal = null;
        //BEGIN get_object_subset
		final List<ObjectIdentifier> loi = processSubObjectIdentifiers(
				subObjectIds);
		final List<WorkspaceObjectData> objects =
				ws.getObjects(wsmeth.getUser(authPart), loi);
		resourcesToDelete.set(objects);
		returnVal = translateObjectData(objects, wsmeth.getUser(authPart),
				handleManagerUrl, handleMgrToken, true);
        //END get_object_subset
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_object_history</p>
     * <pre>
     * Get an object's history. The version argument of the ObjectIdentity is
     * ignored.
     * </pre>
     * @param   object   instance of type {@link us.kbase.workspace.ObjectIdentity ObjectIdentity}
     * @return   parameter "history" of list of original type "object_info" (Information about an object, including user provided metadata. obj_id objid - the numerical id of the object. obj_name name - the name of the object. type_string type - the type of the object. timestamp save_date - the save date of the object. obj_ver ver - the version of the object. username saved_by - the user that saved or copied the object. ws_id wsid - the workspace containing the object. ws_name workspace - the workspace containing the object. string chsum - the md5 checksum of the object. int size - the size of the object in bytes. usermeta meta - arbitrary user-supplied metadata about the object.) &rarr; tuple of size 11: parameter "objid" of original type "obj_id" (The unique, permanent numerical ID of an object.), parameter "name" of original type "obj_name" (A string used as a name for an object. Any string consisting of alphanumeric characters and the characters |._- that is not an integer is acceptable.), parameter "type" of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1), parameter "save_date" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is either the character Z (representing the UTC timezone) or the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time) 2013-04-03T08:56:32Z (UTC time)), parameter "version" of Long, parameter "saved_by" of original type "username" (Login name of a KBase user account.), parameter "wsid" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_", ".", or "-" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "chsum" of String, parameter "size" of Long, parameter "meta" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String
     */
    @JsonServerMethod(rpc = "Workspace.get_object_history", authOptional=true, async=true)
    public List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>> getObjectHistory(ObjectIdentity object, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>> returnVal = null;
        //BEGIN get_object_history
		returnVal = wsmeth.getObjectHistory(object, wsmeth.getUser(authPart), false);
        //END get_object_history
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: list_referencing_objects</p>
     * <pre>
     * List objects that reference one or more specified objects. References
     * in the deleted state are not returned.
     * </pre>
     * @param   objectIds   instance of list of type {@link us.kbase.workspace.ObjectIdentity ObjectIdentity}
     * @return   parameter "referrers" of list of list of original type "object_info" (Information about an object, including user provided metadata. obj_id objid - the numerical id of the object. obj_name name - the name of the object. type_string type - the type of the object. timestamp save_date - the save date of the object. obj_ver ver - the version of the object. username saved_by - the user that saved or copied the object. ws_id wsid - the workspace containing the object. ws_name workspace - the workspace containing the object. string chsum - the md5 checksum of the object. int size - the size of the object in bytes. usermeta meta - arbitrary user-supplied metadata about the object.) &rarr; tuple of size 11: parameter "objid" of original type "obj_id" (The unique, permanent numerical ID of an object.), parameter "name" of original type "obj_name" (A string used as a name for an object. Any string consisting of alphanumeric characters and the characters |._- that is not an integer is acceptable.), parameter "type" of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1), parameter "save_date" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is either the character Z (representing the UTC timezone) or the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time) 2013-04-03T08:56:32Z (UTC time)), parameter "version" of Long, parameter "saved_by" of original type "username" (Login name of a KBase user account.), parameter "wsid" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_", ".", or "-" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "chsum" of String, parameter "size" of Long, parameter "meta" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String
     */
    @JsonServerMethod(rpc = "Workspace.list_referencing_objects", authOptional=true, async=true)
    public List<List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>>> listReferencingObjects(List<ObjectIdentity> objectIds, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        List<List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>>> returnVal = null;
        //BEGIN list_referencing_objects
		final List<ObjectIdentifier> loi = processObjectIdentifiers(objectIds);
		returnVal = translateObjectInfoList(ws.getReferencingObjects(
				wsmeth.getUser(authPart), loi), false);
        //END list_referencing_objects
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: list_referencing_object_counts</p>
     * <pre>
     * DEPRECATED
     * List the number of times objects have been referenced.
     * This count includes both provenance and object-to-object references
     * and, unlike list_referencing_objects, includes objects that are
     * inaccessible to the user.
     * @deprecated
     * </pre>
     * @param   objectIds   instance of list of type {@link us.kbase.workspace.ObjectIdentity ObjectIdentity}
     * @return   parameter "counts" of list of Long
     */
    @JsonServerMethod(rpc = "Workspace.list_referencing_object_counts", authOptional=true, async=true)
    public List<Long> listReferencingObjectCounts(List<ObjectIdentity> objectIds, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        List<Long> returnVal = null;
        //BEGIN list_referencing_object_counts
		final List<ObjectIdentifier> loi = processObjectIdentifiers(objectIds);
		returnVal = new LinkedList<Long>();
		for (int i: ws.getReferencingObjectCounts(
				wsmeth.getUser(authPart), loi)) {
			returnVal.add((long) i);
		}
        //END list_referencing_object_counts
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_referenced_objects</p>
     * <pre>
     * DEPRECATED
     *         Get objects by references from other objects.
     *         NOTE: In the vast majority of cases, this method is not necessary and
     *         get_objects should be used instead. 
     *         
     *         get_referenced_objects guarantees that a user that has access to an
     *         object can always see a) objects that are referenced inside the object
     *         and b) objects that are referenced in the object's provenance. This
     *         ensures that the user has visibility into the entire provenance of the
     *         object and the object's object dependencies (e.g. references).
     *         
     *         The user must have at least read access to the first object in each
     *         reference chain, but need not have access to any further objects in
     *         the chain, and those objects may be deleted.
     *         
     *         @deprecated Workspace.get_objects2
     * </pre>
     * @param   refChains   instance of list of original type "ref_chain" (A chain of objects with references to one another. An object reference chain consists of a list of objects where the nth object possesses a reference, either in the object itself or in the object provenance, to the n+1th object.) &rarr; list of type {@link us.kbase.workspace.ObjectIdentity ObjectIdentity}
     * @return   parameter "data" of list of type {@link us.kbase.workspace.ObjectData ObjectData}
     */
    @JsonServerMethod(rpc = "Workspace.get_referenced_objects", authOptional=true, async=true)
    public List<ObjectData> getReferencedObjects(List<List<ObjectIdentity>> refChains, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        List<ObjectData> returnVal = null;
        //BEGIN get_referenced_objects
		if (refChains == null) {
			throw new IllegalArgumentException("refChains may not be null");
		}
		final List<ObjectIdentifier> chains =
				new LinkedList<ObjectIdentifier>();
		int count = 1;
		for (List<ObjectIdentity> loy: refChains) {
			final List<ObjectIdentifier> lor;
			try {
				lor = processObjectIdentifiers(loy);
			} catch (Exception e) {
				throw new IllegalArgumentException(String.format(
						"Error on object chain #%s: %s",
						count, e.getLocalizedMessage()), e);
			}
			if (lor.size() < 2) {
				throw new IllegalArgumentException(String.format(
						"Error on object chain #%s: The minimum size of a reference chain is 2 ObjectIdentities",
						count));
			}
			chains.add(new ObjectIDWithRefPath(
					lor.get(0), lor.subList(1, lor.size())));
			count++;
		}
		final List<WorkspaceObjectData> objects = ws.getObjects(
				wsmeth.getUser(authPart), chains);
		resourcesToDelete.set(objects);
		returnVal = translateObjectData(objects, wsmeth.getUser(authPart),
					handleManagerUrl, handleMgrToken, true);
        //END get_referenced_objects
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: list_workspaces</p>
     * <pre>
     * Lists the metadata of all workspaces a user has access to. Provided for
     * backwards compatibility - to be replaced by the functionality of
     * list_workspace_info
     * @deprecated Workspace.list_workspace_info
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.ListWorkspacesParams ListWorkspacesParams} (original type "list_workspaces_params")
     * @return   parameter "workspaces" of list of original type "workspace_metadata" (Meta data associated with a workspace. Provided for backwards compatibility. To be replaced by workspace_info. ws_name id - name of the workspace username owner - name of the user who owns (who created) this workspace timestamp moddate - date when the workspace was last modified int objects - the approximate number of objects currently stored in the workspace. permission user_permission - permissions for the currently logged in user for the workspace permission global_permission - default permissions for the workspace for all KBase users ws_id num_id - numerical ID of the workspace @deprecated Workspace.workspace_info) &rarr; tuple of size 7: parameter "id" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_", ".", or "-" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "owner" of original type "username" (Login name of a KBase user account.), parameter "moddate" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is either the character Z (representing the UTC timezone) or the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time) 2013-04-03T08:56:32Z (UTC time)), parameter "objects" of Long, parameter "user_permission" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "global_permission" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "num_id" of original type "ws_id" (The unique, permanent numerical ID of a workspace.)
     */
    @JsonServerMethod(rpc = "Workspace.list_workspaces", authOptional=true, async=true)
    public List<Tuple7<String, String, String, Long, String, String, Long>> listWorkspaces(ListWorkspacesParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        List<Tuple7<String, String, String, Long, String, String, Long>> returnVal = null;
        //BEGIN list_workspaces
		returnVal =  wsInfoToMetaTuple(ws.listWorkspaces(
				wsmeth.getUser(params.getAuth(), authPart), null, null, null,
				null, null, longToBoolean(params.getExcludeGlobal()), false,
				false));
        //END list_workspaces
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: list_workspace_info</p>
     * <pre>
     * List workspaces viewable by the user.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.ListWorkspaceInfoParams ListWorkspaceInfoParams}
     * @return   parameter "wsinfo" of list of original type "workspace_info" (Information about a workspace. ws_id id - the numerical ID of the workspace. ws_name workspace - name of the workspace. username owner - name of the user who owns (e.g. created) this workspace. timestamp moddate - date when the workspace was last modified. int max_objid - the maximum object ID appearing in this workspace. Since cloning a workspace preserves object IDs, this number may be greater than the number of objects in a newly cloned workspace. permission user_permission - permissions for the authenticated user of this workspace. permission globalread - whether this workspace is globally readable. lock_status lockstat - the status of the workspace lock. usermeta metadata - arbitrary user-supplied metadata about the workspace.) &rarr; tuple of size 9: parameter "id" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_", ".", or "-" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "owner" of original type "username" (Login name of a KBase user account.), parameter "moddate" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is either the character Z (representing the UTC timezone) or the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time) 2013-04-03T08:56:32Z (UTC time)), parameter "max_objid" of Long, parameter "user_permission" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "globalread" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "lockstat" of original type "lock_status" (The lock status of a workspace. One of 'unlocked', 'locked', or 'published'.), parameter "metadata" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String
     */
    @JsonServerMethod(rpc = "Workspace.list_workspace_info", authOptional=true, async=true)
    public List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String,String>>> listWorkspaceInfo(ListWorkspaceInfoParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String,String>>> returnVal = null;
        //BEGIN list_workspace_info
		returnVal = wsmeth.listWorkspaceInfo(params, wsmeth.getUser(authPart));
        //END list_workspace_info
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: list_workspace_ids</p>
     * <pre>
     * List workspace IDs to which the user has access.
     * This function returns a subset of the information in the
     * list_workspace_info method and should be substantially faster.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.ListWorkspaceIDsParams ListWorkspaceIDsParams}
     * @return   parameter "results" of type {@link us.kbase.workspace.ListWorkspaceIDsResults ListWorkspaceIDsResults}
     */
    @JsonServerMethod(rpc = "Workspace.list_workspace_ids", authOptional=true, async=true)
    public ListWorkspaceIDsResults listWorkspaceIds(ListWorkspaceIDsParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        ListWorkspaceIDsResults returnVal = null;
        //BEGIN list_workspace_ids
		returnVal = wsmeth.listWorkspaceIDs(params, wsmeth.getUser(authPart));
        //END list_workspace_ids
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: list_workspace_objects</p>
     * <pre>
     * Lists the metadata of all objects in the specified workspace with the
     * specified type (or with any type). Provided for backwards compatibility.
     * @deprecated Workspace.list_objects
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.ListWorkspaceObjectsParams ListWorkspaceObjectsParams} (original type "list_workspace_objects_params")
     * @return   parameter "objects" of list of original type "object_metadata" (Meta data associated with an object stored in a workspace. Provided for backwards compatibility. obj_name id - name of the object. type_string type - type of the object. timestamp moddate - date when the object was saved obj_ver instance - the version of the object string command - Deprecated. Always returns the empty string. username lastmodifier - name of the user who last saved the object, including copying the object username owner - Deprecated. Same as lastmodifier. ws_name workspace - name of the workspace in which the object is stored string ref - Deprecated. Always returns the empty string. string chsum - the md5 checksum of the object. usermeta metadata - arbitrary user-supplied metadata about the object. obj_id objid - the numerical id of the object. @deprecated object_info) &rarr; tuple of size 12: parameter "id" of original type "obj_name" (A string used as a name for an object. Any string consisting of alphanumeric characters and the characters |._- that is not an integer is acceptable.), parameter "type" of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1), parameter "moddate" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is either the character Z (representing the UTC timezone) or the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time) 2013-04-03T08:56:32Z (UTC time)), parameter "instance" of Long, parameter "command" of String, parameter "lastmodifier" of original type "username" (Login name of a KBase user account.), parameter "owner" of original type "username" (Login name of a KBase user account.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_", ".", or "-" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "ref" of String, parameter "chsum" of String, parameter "metadata" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String, parameter "objid" of original type "obj_id" (The unique, permanent numerical ID of an object.)
     */
    @JsonServerMethod(rpc = "Workspace.list_workspace_objects", authOptional=true, async=true)
    public List<Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String,String>, Long>> listWorkspaceObjects(ListWorkspaceObjectsParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        List<Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String,String>, Long>> returnVal = null;
        //BEGIN list_workspace_objects
		final WorkspaceIdentifier wsi = processWorkspaceIdentifier(
				params.getWorkspace(), null);
		final TypeDefId type = params.getType() == null ? null :
				TypeDefId.fromTypeString(params.getType());
		
		final WorkspaceUser user = wsmeth.getUser(params.getAuth(), authPart);
		final ListObjectsParameters lop;
		if (type == null) {
			lop = new ListObjectsParameters(user, Arrays.asList(wsi));
		} else {
			lop = new ListObjectsParameters(user, Arrays.asList(wsi), type);
		}
		lop.withShowDeleted(longToBoolean(params.getShowDeletedObject()))
			.withIncludeMetaData(true);
		returnVal = objInfoToMetaTuple(ws.listObjects(lop), false);
        //END list_workspace_objects
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: list_objects</p>
     * <pre>
     * List objects in one or more workspaces.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.ListObjectsParams ListObjectsParams}
     * @return   parameter "objinfo" of list of original type "object_info" (Information about an object, including user provided metadata. obj_id objid - the numerical id of the object. obj_name name - the name of the object. type_string type - the type of the object. timestamp save_date - the save date of the object. obj_ver ver - the version of the object. username saved_by - the user that saved or copied the object. ws_id wsid - the workspace containing the object. ws_name workspace - the workspace containing the object. string chsum - the md5 checksum of the object. int size - the size of the object in bytes. usermeta meta - arbitrary user-supplied metadata about the object.) &rarr; tuple of size 11: parameter "objid" of original type "obj_id" (The unique, permanent numerical ID of an object.), parameter "name" of original type "obj_name" (A string used as a name for an object. Any string consisting of alphanumeric characters and the characters |._- that is not an integer is acceptable.), parameter "type" of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1), parameter "save_date" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is either the character Z (representing the UTC timezone) or the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time) 2013-04-03T08:56:32Z (UTC time)), parameter "version" of Long, parameter "saved_by" of original type "username" (Login name of a KBase user account.), parameter "wsid" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_", ".", or "-" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "chsum" of String, parameter "size" of Long, parameter "meta" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String
     */
    @JsonServerMethod(rpc = "Workspace.list_objects", authOptional=true, async=true)
    public List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>> listObjects(ListObjectsParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>> returnVal = null;
        //BEGIN list_objects
		returnVal = wsmeth.listObjects(params, wsmeth.getUser(authPart), false);
        //END list_objects
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_objectmeta</p>
     * <pre>
     * Retrieves the metadata for a specified object from the specified
     * workspace. Provides access to metadata for all versions of the object
     * via the instance parameter. Provided for backwards compatibility.
     * @deprecated Workspace.get_object_info3
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.GetObjectmetaParams GetObjectmetaParams} (original type "get_objectmeta_params")
     * @return   parameter "metadata" of original type "object_metadata" (Meta data associated with an object stored in a workspace. Provided for backwards compatibility. obj_name id - name of the object. type_string type - type of the object. timestamp moddate - date when the object was saved obj_ver instance - the version of the object string command - Deprecated. Always returns the empty string. username lastmodifier - name of the user who last saved the object, including copying the object username owner - Deprecated. Same as lastmodifier. ws_name workspace - name of the workspace in which the object is stored string ref - Deprecated. Always returns the empty string. string chsum - the md5 checksum of the object. usermeta metadata - arbitrary user-supplied metadata about the object. obj_id objid - the numerical id of the object. @deprecated object_info) &rarr; tuple of size 12: parameter "id" of original type "obj_name" (A string used as a name for an object. Any string consisting of alphanumeric characters and the characters |._- that is not an integer is acceptable.), parameter "type" of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1), parameter "moddate" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is either the character Z (representing the UTC timezone) or the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time) 2013-04-03T08:56:32Z (UTC time)), parameter "instance" of Long, parameter "command" of String, parameter "lastmodifier" of original type "username" (Login name of a KBase user account.), parameter "owner" of original type "username" (Login name of a KBase user account.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_", ".", or "-" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "ref" of String, parameter "chsum" of String, parameter "metadata" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String, parameter "objid" of original type "obj_id" (The unique, permanent numerical ID of an object.)
     */
    @JsonServerMethod(rpc = "Workspace.get_objectmeta", authOptional=true, async=true)
    public Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String,String>, Long> getObjectmeta(GetObjectmetaParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String,String>, Long> returnVal = null;
        //BEGIN get_objectmeta
		final ObjectIdentifier oi = processObjectIdentifier(
				params.getWorkspace(), null, params.getId(), null,
				params.getInstance());
		returnVal = objInfoToMetaTuple(ws.getObjectInformation(
				wsmeth.getUser(params.getAuth(), authPart),
				Arrays.asList(oi), true, false).get(0), true);
        //END get_objectmeta
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_object_info</p>
     * <pre>
     * Get information about objects from the workspace.
     * Set includeMetadata true to include the user specified metadata.
     * Otherwise the metadata in the object_info will be null.
     * This method will be replaced by the behavior of get_object_info_new
     * in the future.
     * @deprecated Workspace.get_object_info3
     * </pre>
     * @param   objectIds   instance of list of type {@link us.kbase.workspace.ObjectIdentity ObjectIdentity}
     * @param   includeMetadata   instance of original type "boolean" (A boolean. 0 = false, other = true.)
     * @return   parameter "info" of list of original type "object_info" (Information about an object, including user provided metadata. obj_id objid - the numerical id of the object. obj_name name - the name of the object. type_string type - the type of the object. timestamp save_date - the save date of the object. obj_ver ver - the version of the object. username saved_by - the user that saved or copied the object. ws_id wsid - the workspace containing the object. ws_name workspace - the workspace containing the object. string chsum - the md5 checksum of the object. int size - the size of the object in bytes. usermeta meta - arbitrary user-supplied metadata about the object.) &rarr; tuple of size 11: parameter "objid" of original type "obj_id" (The unique, permanent numerical ID of an object.), parameter "name" of original type "obj_name" (A string used as a name for an object. Any string consisting of alphanumeric characters and the characters |._- that is not an integer is acceptable.), parameter "type" of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1), parameter "save_date" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is either the character Z (representing the UTC timezone) or the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time) 2013-04-03T08:56:32Z (UTC time)), parameter "version" of Long, parameter "saved_by" of original type "username" (Login name of a KBase user account.), parameter "wsid" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_", ".", or "-" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "chsum" of String, parameter "size" of Long, parameter "meta" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String
     */
    @JsonServerMethod(rpc = "Workspace.get_object_info", authOptional=true, async=true)
    public List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>> getObjectInfo(List<ObjectIdentity> objectIds, Long includeMetadata, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>> returnVal = null;
        //BEGIN get_object_info
		final List<ObjectIdentifier> loi = processObjectIdentifiers(objectIds);
		returnVal = objInfoToTuple(
				ws.getObjectInformation(wsmeth.getUser(authPart), loi,
						longToBoolean(includeMetadata), false), true);
        //END get_object_info
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_object_info_new</p>
     * <pre>
     * Get information about objects from the workspace.
     * @deprecated Workspace.get_object_info3
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.GetObjectInfoNewParams GetObjectInfoNewParams}
     * @return   parameter "info" of list of original type "object_info" (Information about an object, including user provided metadata. obj_id objid - the numerical id of the object. obj_name name - the name of the object. type_string type - the type of the object. timestamp save_date - the save date of the object. obj_ver ver - the version of the object. username saved_by - the user that saved or copied the object. ws_id wsid - the workspace containing the object. ws_name workspace - the workspace containing the object. string chsum - the md5 checksum of the object. int size - the size of the object in bytes. usermeta meta - arbitrary user-supplied metadata about the object.) &rarr; tuple of size 11: parameter "objid" of original type "obj_id" (The unique, permanent numerical ID of an object.), parameter "name" of original type "obj_name" (A string used as a name for an object. Any string consisting of alphanumeric characters and the characters |._- that is not an integer is acceptable.), parameter "type" of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1), parameter "save_date" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is either the character Z (representing the UTC timezone) or the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time) 2013-04-03T08:56:32Z (UTC time)), parameter "version" of Long, parameter "saved_by" of original type "username" (Login name of a KBase user account.), parameter "wsid" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_", ".", or "-" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "chsum" of String, parameter "size" of Long, parameter "meta" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String
     */
    @JsonServerMethod(rpc = "Workspace.get_object_info_new", authOptional=true, async=true)
    public List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>> getObjectInfoNew(GetObjectInfoNewParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>> returnVal = null;
        //BEGIN get_object_info_new
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		final List<ObjectIdentifier> loi = processObjectSpecifications(
				params.getObjects());
		returnVal = objInfoToTuple(
				ws.getObjectInformation(wsmeth.getUser(authPart), loi,
						longToBoolean(params.getIncludeMetadata()),
						longToBoolean(params.getIgnoreErrors())), true);
        //END get_object_info_new
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_object_info3</p>
     * <pre>
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.GetObjectInfo3Params GetObjectInfo3Params}
     * @return   parameter "results" of type {@link us.kbase.workspace.GetObjectInfo3Results GetObjectInfo3Results}
     */
    @JsonServerMethod(rpc = "Workspace.get_object_info3", authOptional=true, async=true)
    public GetObjectInfo3Results getObjectInfo3(GetObjectInfo3Params params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        GetObjectInfo3Results returnVal = null;
        //BEGIN get_object_info3
		returnVal = wsmeth.getObjectInformation(params, wsmeth.getUser(authPart), false);
        //END get_object_info3
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: rename_workspace</p>
     * <pre>
     * Rename a workspace.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.RenameWorkspaceParams RenameWorkspaceParams}
     * @return   parameter "renamed" of original type "workspace_info" (Information about a workspace. ws_id id - the numerical ID of the workspace. ws_name workspace - name of the workspace. username owner - name of the user who owns (e.g. created) this workspace. timestamp moddate - date when the workspace was last modified. int max_objid - the maximum object ID appearing in this workspace. Since cloning a workspace preserves object IDs, this number may be greater than the number of objects in a newly cloned workspace. permission user_permission - permissions for the authenticated user of this workspace. permission globalread - whether this workspace is globally readable. lock_status lockstat - the status of the workspace lock. usermeta metadata - arbitrary user-supplied metadata about the workspace.) &rarr; tuple of size 9: parameter "id" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_", ".", or "-" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "owner" of original type "username" (Login name of a KBase user account.), parameter "moddate" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is either the character Z (representing the UTC timezone) or the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time) 2013-04-03T08:56:32Z (UTC time)), parameter "max_objid" of Long, parameter "user_permission" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "globalread" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "lockstat" of original type "lock_status" (The lock status of a workspace. One of 'unlocked', 'locked', or 'published'.), parameter "metadata" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String
     */
    @JsonServerMethod(rpc = "Workspace.rename_workspace", async=true)
    public Tuple9<Long, String, String, String, Long, String, String, String, Map<String,String>> renameWorkspace(RenameWorkspaceParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        Tuple9<Long, String, String, String, Long, String, String, String, Map<String,String>> returnVal = null;
        //BEGIN rename_workspace
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		final WorkspaceIdentifier wsi =
				processWorkspaceIdentifier(params.getWsi());
		returnVal = wsInfoToTuple(ws.renameWorkspace(wsmeth.getUser(authPart),
				wsi, params.getNewName()));
        //END rename_workspace
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: rename_object</p>
     * <pre>
     * Rename an object. User meta data is always returned as null.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.RenameObjectParams RenameObjectParams}
     * @return   parameter "renamed" of original type "object_info" (Information about an object, including user provided metadata. obj_id objid - the numerical id of the object. obj_name name - the name of the object. type_string type - the type of the object. timestamp save_date - the save date of the object. obj_ver ver - the version of the object. username saved_by - the user that saved or copied the object. ws_id wsid - the workspace containing the object. ws_name workspace - the workspace containing the object. string chsum - the md5 checksum of the object. int size - the size of the object in bytes. usermeta meta - arbitrary user-supplied metadata about the object.) &rarr; tuple of size 11: parameter "objid" of original type "obj_id" (The unique, permanent numerical ID of an object.), parameter "name" of original type "obj_name" (A string used as a name for an object. Any string consisting of alphanumeric characters and the characters |._- that is not an integer is acceptable.), parameter "type" of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1), parameter "save_date" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is either the character Z (representing the UTC timezone) or the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time) 2013-04-03T08:56:32Z (UTC time)), parameter "version" of Long, parameter "saved_by" of original type "username" (Login name of a KBase user account.), parameter "wsid" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_", ".", or "-" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "chsum" of String, parameter "size" of Long, parameter "meta" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String
     */
    @JsonServerMethod(rpc = "Workspace.rename_object", async=true)
    public Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>> renameObject(RenameObjectParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>> returnVal = null;
        //BEGIN rename_object
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		final ObjectIdentifier oi = processObjectIdentifier(params.getObj());
		returnVal = objInfoToTuple(ws.renameObject(wsmeth.getUser(authPart),
				oi, params.getNewName()), true);
        //END rename_object
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: copy_object</p>
     * <pre>
     * Copy an object. Returns the object_info for the newest version.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.CopyObjectParams CopyObjectParams}
     * @return   parameter "copied" of original type "object_info" (Information about an object, including user provided metadata. obj_id objid - the numerical id of the object. obj_name name - the name of the object. type_string type - the type of the object. timestamp save_date - the save date of the object. obj_ver ver - the version of the object. username saved_by - the user that saved or copied the object. ws_id wsid - the workspace containing the object. ws_name workspace - the workspace containing the object. string chsum - the md5 checksum of the object. int size - the size of the object in bytes. usermeta meta - arbitrary user-supplied metadata about the object.) &rarr; tuple of size 11: parameter "objid" of original type "obj_id" (The unique, permanent numerical ID of an object.), parameter "name" of original type "obj_name" (A string used as a name for an object. Any string consisting of alphanumeric characters and the characters |._- that is not an integer is acceptable.), parameter "type" of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1), parameter "save_date" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is either the character Z (representing the UTC timezone) or the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time) 2013-04-03T08:56:32Z (UTC time)), parameter "version" of Long, parameter "saved_by" of original type "username" (Login name of a KBase user account.), parameter "wsid" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_", ".", or "-" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "chsum" of String, parameter "size" of Long, parameter "meta" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String
     */
    @JsonServerMethod(rpc = "Workspace.copy_object", async=true)
    public Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>> copyObject(CopyObjectParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>> returnVal = null;
        //BEGIN copy_object
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		final ObjectIdentifier from = processObjectIdentifier(params.getFrom());
		final ObjectIdentifier to = processObjectIdentifier(params.getTo());
		returnVal = objInfoToTuple(ws.copyObject(
				wsmeth.getUser(authPart), from, to), true);
        //END copy_object
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: revert_object</p>
     * <pre>
     * Revert an object.
     *         The object specified in the ObjectIdentity is reverted to the version
     *         specified in the ObjectIdentity.
     * </pre>
     * @param   object   instance of type {@link us.kbase.workspace.ObjectIdentity ObjectIdentity}
     * @return   parameter "reverted" of original type "object_info" (Information about an object, including user provided metadata. obj_id objid - the numerical id of the object. obj_name name - the name of the object. type_string type - the type of the object. timestamp save_date - the save date of the object. obj_ver ver - the version of the object. username saved_by - the user that saved or copied the object. ws_id wsid - the workspace containing the object. ws_name workspace - the workspace containing the object. string chsum - the md5 checksum of the object. int size - the size of the object in bytes. usermeta meta - arbitrary user-supplied metadata about the object.) &rarr; tuple of size 11: parameter "objid" of original type "obj_id" (The unique, permanent numerical ID of an object.), parameter "name" of original type "obj_name" (A string used as a name for an object. Any string consisting of alphanumeric characters and the characters |._- that is not an integer is acceptable.), parameter "type" of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1), parameter "save_date" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is either the character Z (representing the UTC timezone) or the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time) 2013-04-03T08:56:32Z (UTC time)), parameter "version" of Long, parameter "saved_by" of original type "username" (Login name of a KBase user account.), parameter "wsid" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_", ".", or "-" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "chsum" of String, parameter "size" of Long, parameter "meta" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String
     */
    @JsonServerMethod(rpc = "Workspace.revert_object", async=true)
    public Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>> revertObject(ObjectIdentity object, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>> returnVal = null;
        //BEGIN revert_object
		final ObjectIdentifier oi = processObjectIdentifier(object);
		returnVal = objInfoToTuple(ws.revertObject(
				wsmeth.getUser(authPart), oi), true);
        //END revert_object
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_names_by_prefix</p>
     * <pre>
     * Get object names matching a prefix. At most 1000 names are returned.
     * No particular ordering is guaranteed, nor is which names will be
     * returned if more than 1000 are found.
     * This function is intended for use as an autocomplete helper function.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.GetNamesByPrefixParams GetNamesByPrefixParams}
     * @return   parameter "res" of type {@link us.kbase.workspace.GetNamesByPrefixResults GetNamesByPrefixResults}
     */
    @JsonServerMethod(rpc = "Workspace.get_names_by_prefix", authOptional=true, async=true)
    public GetNamesByPrefixResults getNamesByPrefix(GetNamesByPrefixParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        GetNamesByPrefixResults returnVal = null;
        //BEGIN get_names_by_prefix
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		final List<WorkspaceIdentifier> wsil =
				new LinkedList<WorkspaceIdentifier>();
		for (final WorkspaceIdentity wsi: params.getWorkspaces()) {
			wsil.add(processWorkspaceIdentifier(wsi));
		}
		returnVal = new GetNamesByPrefixResults().withNames(
				ws.getNamesByPrefix(
						wsmeth.getUser(authPart),
						wsil,
						params.getPrefix(),
						longToBoolean(params.getIncludeHidden()),
						1000
				)
		);
        //END get_names_by_prefix
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: hide_objects</p>
     * <pre>
     * Hide objects. All versions of an object are hidden, regardless of
     * the version specified in the ObjectIdentity. Hidden objects do not
     * appear in the list_objects method.
     * </pre>
     * @param   objectIds   instance of list of type {@link us.kbase.workspace.ObjectIdentity ObjectIdentity}
     */
    @JsonServerMethod(rpc = "Workspace.hide_objects", async=true)
    public void hideObjects(List<ObjectIdentity> objectIds, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        //BEGIN hide_objects
		final List<ObjectIdentifier> loi = processObjectIdentifiers(objectIds);
		ws.setObjectsHidden(wsmeth.getUser(authPart), loi, true);
        //END hide_objects
    }

    /**
     * <p>Original spec-file function name: unhide_objects</p>
     * <pre>
     * Unhide objects. All versions of an object are unhidden, regardless
     * of the version specified in the ObjectIdentity.
     * </pre>
     * @param   objectIds   instance of list of type {@link us.kbase.workspace.ObjectIdentity ObjectIdentity}
     */
    @JsonServerMethod(rpc = "Workspace.unhide_objects", async=true)
    public void unhideObjects(List<ObjectIdentity> objectIds, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        //BEGIN unhide_objects
		final List<ObjectIdentifier> loi = processObjectIdentifiers(objectIds);
		ws.setObjectsHidden(wsmeth.getUser(authPart), loi, false);
        //END unhide_objects
    }

    /**
     * <p>Original spec-file function name: delete_objects</p>
     * <pre>
     * Delete objects. All versions of an object are deleted, regardless of
     * the version specified in the ObjectIdentity.
     * </pre>
     * @param   objectIds   instance of list of type {@link us.kbase.workspace.ObjectIdentity ObjectIdentity}
     */
    @JsonServerMethod(rpc = "Workspace.delete_objects", async=true)
    public void deleteObjects(List<ObjectIdentity> objectIds, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        //BEGIN delete_objects
		final List<ObjectIdentifier> loi = processObjectIdentifiers(objectIds);
		ws.setObjectsDeleted(wsmeth.getUser(authPart), loi, true);
        //END delete_objects
    }

    /**
     * <p>Original spec-file function name: undelete_objects</p>
     * <pre>
     * Undelete objects. All versions of an object are undeleted, regardless
     * of the version specified in the ObjectIdentity. If an object is not
     * deleted, no error is thrown.
     * </pre>
     * @param   objectIds   instance of list of type {@link us.kbase.workspace.ObjectIdentity ObjectIdentity}
     */
    @JsonServerMethod(rpc = "Workspace.undelete_objects", async=true)
    public void undeleteObjects(List<ObjectIdentity> objectIds, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        //BEGIN undelete_objects
		final List<ObjectIdentifier> loi = processObjectIdentifiers(objectIds);
		ws.setObjectsDeleted(wsmeth.getUser(authPart), loi, false);
        //END undelete_objects
    }

    /**
     * <p>Original spec-file function name: delete_workspace</p>
     * <pre>
     * Delete a workspace. All objects contained in the workspace are deleted.
     * </pre>
     * @param   wsi   instance of type {@link us.kbase.workspace.WorkspaceIdentity WorkspaceIdentity}
     */
    @JsonServerMethod(rpc = "Workspace.delete_workspace", async=true)
    public void deleteWorkspace(WorkspaceIdentity wsi, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        //BEGIN delete_workspace
		final WorkspaceIdentifier wksp = processWorkspaceIdentifier(wsi);
		ws.setWorkspaceDeleted(wsmeth.getUser(authPart), wksp, true);
        //END delete_workspace
    }

    /**
     * <p>Original spec-file function name: request_module_ownership</p>
     * <pre>
     * Request ownership of a module name. A Workspace administrator
     * must approve the request.
     * </pre>
     * @param   mod   instance of original type "modulename" (A module name defined in a KIDL typespec.)
     */
    @JsonServerMethod(rpc = "Workspace.request_module_ownership", async=true)
    public void requestModuleOwnership(String mod, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        //BEGIN request_module_ownership
		final WorkspaceUser u = wsmeth.getUser(authPart);
		types.requestModuleRegistration(u, mod);
		//bail on this, there's no mail daemon running on magellean AFAIK
//		wsadmin.notifyOnModuleRegRequest(authPart, u, mod);
        //END request_module_ownership
    }

    /**
     * <p>Original spec-file function name: register_typespec</p>
     * <pre>
     * Register a new typespec or recompile a previously registered typespec
     * with new options.
     * See the documentation of RegisterTypespecParams for more details.
     * Also see the release_types function.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.RegisterTypespecParams RegisterTypespecParams}
     * @return   instance of mapping from original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1) to original type "jsonschema" (The JSON Schema (v4) representation of a type definition.)
     */
    @JsonServerMethod(rpc = "Workspace.register_typespec", async=true)
    public Map<String,String> registerTypespec(RegisterTypespecParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        Map<String,String> returnVal = null;
        //BEGIN register_typespec
		//TODO improve parse errors, don't need include path, currentlyCompiled
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		if (!(params.getMod() == null) ^ (params.getSpec() == null)) {
			throw new IllegalArgumentException(
					"Must provide either a spec or module name");
		}
		final List<String> add = params.getNewTypes() != null ?
				params.getNewTypes() : new ArrayList<String>();
		final List<String> rem = params.getRemoveTypes() != null ?
				params.getRemoveTypes() : new ArrayList<String>();
		final Map<String, Long> deps = params.getDependencies() != null ?
				params.getDependencies() : new HashMap<String, Long>();
		final Map<TypeDefName, TypeChange> res;
		if (params.getMod() != null) {
			 res = types.compileTypeSpec(
					wsmeth.getUser(authPart), params.getMod(),
					add, rem, deps, params.getDryrun() == null ? true :
						params.getDryrun() != 0);
		} else {
			res = types.compileNewTypeSpec(
					wsmeth.getUser(authPart), params.getSpec(),
					add, rem, deps, params.getDryrun() == null ? true :
						params.getDryrun() != 0, params.getPrevVer());
		}
		returnVal = new HashMap<String, String>();
		for (final TypeChange tc: res.values()) {
			if (!tc.isUnregistered()) {
				returnVal.put(tc.getTypeVersion().getTypeString(),
						tc.getJsonSchema());
			}
		}
        //END register_typespec
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: register_typespec_copy</p>
     * <pre>
     * Register a copy of new typespec or refresh an existing typespec which is
     * loaded from another workspace for synchronization. Method returns new
     * version of module in current workspace.
     * Also see the release_types function.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.RegisterTypespecCopyParams RegisterTypespecCopyParams}
     * @return   parameter "new_local_version" of original type "spec_version" (The version of a typespec file.)
     */
    @JsonServerMethod(rpc = "Workspace.register_typespec_copy", async=true)
    public Long registerTypespecCopy(RegisterTypespecCopyParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        Long returnVal = null;
        //BEGIN register_typespec_copy
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		if (params.getExternalWorkspaceUrl() == null) {
			throw new IllegalArgumentException(
					"Must provide a URL for an external workspace service");
		}
		if (params.getMod() == null) {
			throw new IllegalArgumentException(
					"Must provide a module name");
		}
		final WorkspaceClient client = new WorkspaceClient(
				new URL(params.getExternalWorkspaceUrl()), authPart);
		if (!params.getExternalWorkspaceUrl().startsWith("https:")) {
			client.setIsInsecureHttpConnectionAllowed(true);
		}
		final GetModuleInfoParams gmiparams = new GetModuleInfoParams()
			.withMod(params.getMod()).withVer(params.getVersion());
		final us.kbase.workspace.ModuleInfo extInfo = client.getModuleInfo(gmiparams);
		final Map<String, String> includesToMd5 = new HashMap<String, String>();
		for (final Map.Entry<String, Long> entry : extInfo.getIncludedSpecVersion().entrySet()) {
			final String includedModule = entry.getKey();
			final long extIncludedVer = entry.getValue();
			final GetModuleInfoParams includeParams = new GetModuleInfoParams()
				.withMod(includedModule).withVer(extIncludedVer);
			final us.kbase.workspace.ModuleInfo extIncludedInfo =
					client.getModuleInfo(includeParams);
			includesToMd5.put(includedModule, extIncludedInfo.getChsum());
		}
		final String userId = authPart.getUserName();
		final String specDocument = extInfo.getSpec();
		final Set<String> extTypeSet = new LinkedHashSet<String>();
		for (final String typeDef : extInfo.getTypes().keySet()) {
			extTypeSet.add(TypeDefId.fromTypeString(typeDef).getType().getName());
		}
		returnVal = types.compileTypeSpecCopy(params.getMod(), specDocument,
				extTypeSet, userId, includesToMd5, extInfo.getIncludedSpecVersion());
        //END register_typespec_copy
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: release_module</p>
     * <pre>
     * Release a module for general use of its types.
     * Releases the most recent version of a module. Releasing a module does
     * two things to the module's types:
     * 1) If a type's major version is 0, it is changed to 1. A major
     *         version of 0 implies that the type is in development and may have
     *         backwards incompatible changes from minor version to minor version.
     *         Once a type is released, backwards incompatible changes always
     *         cause a major version increment.
     * 2) This version of the type becomes the default version, and if a 
     *         specific version is not supplied in a function call, this version
     *         will be used. This means that newer, unreleased versions of the
     *         type may be skipped.
     * </pre>
     * @param   mod   instance of original type "modulename" (A module name defined in a KIDL typespec.)
     * @return   parameter "types" of list of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1)
     */
    @JsonServerMethod(rpc = "Workspace.release_module", async=true)
    public List<String> releaseModule(String mod, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        List<String> returnVal = null;
        //BEGIN release_module
		returnVal = new LinkedList<String>();
		final List<AbsoluteTypeDefId> ret =
				types.releaseTypes(wsmeth.getUser(authPart), mod);
		for (final AbsoluteTypeDefId t: ret) {
			returnVal.add(t.getTypeString());
		}
        //END release_module
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: list_modules</p>
     * <pre>
     * List typespec modules.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.ListModulesParams ListModulesParams}
     * @return   parameter "modules" of list of original type "modulename" (A module name defined in a KIDL typespec.)
     */
    @JsonServerMethod(rpc = "Workspace.list_modules", async=true)
    public List<String> listModules(ListModulesParams params, RpcContext jsonRpcContext) throws Exception {
        List<String> returnVal = null;
        //BEGIN list_modules
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		WorkspaceUser user = null;
		if (params.getOwner() != null) {
			user = new WorkspaceUser(params.getOwner());
		}
		returnVal = types.listModules(user);
        //END list_modules
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: list_module_versions</p>
     * <pre>
     * List typespec module versions.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.ListModuleVersionsParams ListModuleVersionsParams}
     * @return   parameter "vers" of type {@link us.kbase.workspace.ModuleVersions ModuleVersions}
     */
    @JsonServerMethod(rpc = "Workspace.list_module_versions", authOptional=true, async=true)
    public ModuleVersions listModuleVersions(ListModuleVersionsParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        ModuleVersions returnVal = null;
        //BEGIN list_module_versions
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		if (!(params.getMod() == null ^ params.getType() == null)) {
			throw new IllegalArgumentException(
					"Must provide either a module name or a type");
		}
		final List<Long> vers;
		final String module;
		if (params.getMod() != null) {
			vers = types.getModuleVersions(
					params.getMod(), wsmeth.getUser(authPart));
			module = params.getMod();
		} else {
			final TypeDefId type = TypeDefId.fromTypeString(params.getType());
			vers = types.getModuleVersions(type, wsmeth.getUser(authPart));
			module = type.getType().getModule();
		}
		returnVal = new ModuleVersions().withMod(module).withVers(vers);
        //END list_module_versions
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_module_info</p>
     * <pre>
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.GetModuleInfoParams GetModuleInfoParams}
     * @return   parameter "info" of type {@link us.kbase.workspace.ModuleInfo ModuleInfo}
     */
    @JsonServerMethod(rpc = "Workspace.get_module_info", authOptional=true, async=true)
    public ModuleInfo getModuleInfo(GetModuleInfoParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        ModuleInfo returnVal = null;
        //BEGIN get_module_info
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		if (params.getMod() == null) {
			throw new IllegalArgumentException(
					"Must provide a module name");
		}
		final ModuleDefId module;
		if (params.getVer() != null) {
			module = new ModuleDefId(params.getMod(), params.getVer());
		} else {
			module = new ModuleDefId(params.getMod());
		}
		WorkspaceUser user = wsmeth.getUser(authPart);
		final us.kbase.workspace.database.ModuleInfo mi =
				types.getModuleInfo(user, module);
		final Map<String, String> types = new HashMap<String, String>();
		for (final AbsoluteTypeDefId t: mi.getTypes().keySet()) {
			types.put(t.getTypeString(), mi.getTypes().get(t));
		}
		returnVal = new ModuleInfo()
				.withDescription(mi.getDescription())
				.withOwners(mi.getOwners())
				.withSpec(mi.getTypespec())
				.withVer(mi.getVersion())
				.withTypes(types)
				.withIncludedSpecVersion(mi.getIncludedSpecVersions())
				.withChsum(mi.getMd5hash())
				.withFunctions(mi.getFunctions())
				.withIsReleased(mi.isReleased() ? 1L : 0L);
        //END get_module_info
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_jsonschema</p>
     * <pre>
     * Get JSON schema for a type.
     * </pre>
     * @param   type   instance of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1)
     * @return   parameter "schema" of original type "jsonschema" (The JSON Schema (v4) representation of a type definition.)
     */
    @JsonServerMethod(rpc = "Workspace.get_jsonschema", authOptional=true, async=true)
    public String getJsonschema(String type, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        String returnVal = null;
        //BEGIN get_jsonschema
		returnVal = types.getJsonSchema(TypeDefId.fromTypeString(type),
				wsmeth.getUser(authPart));
        //END get_jsonschema
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: translate_from_MD5_types</p>
     * <pre>
     * Translation from types qualified with MD5 to their semantic versions
     * </pre>
     * @param   md5Types   instance of list of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1)
     * @return   parameter "sem_types" of mapping from original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1) to list of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1)
     */
    @JsonServerMethod(rpc = "Workspace.translate_from_MD5_types", async=true)
    public Map<String,List<String>> translateFromMD5Types(List<String> md5Types, RpcContext jsonRpcContext) throws Exception {
        Map<String,List<String>> returnVal = null;
        //BEGIN translate_from_MD5_types
        returnVal = types.translateFromMd5Types(md5Types);
        //END translate_from_MD5_types
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: translate_to_MD5_types</p>
     * <pre>
     * Translation from types qualified with semantic versions to their MD5'ed versions
     * </pre>
     * @param   semTypes   instance of list of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1)
     * @return   parameter "md5_types" of mapping from original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1) to original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1)
     */
    @JsonServerMethod(rpc = "Workspace.translate_to_MD5_types", authOptional=true, async=true)
    public Map<String,String> translateToMD5Types(List<String> semTypes, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        Map<String,String> returnVal = null;
        //BEGIN translate_to_MD5_types
        returnVal = types.translateToMd5Types(semTypes, wsmeth.getUser(authPart));
        //END translate_to_MD5_types
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_type_info</p>
     * <pre>
     * </pre>
     * @param   type   instance of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1)
     * @return   parameter "info" of type {@link us.kbase.workspace.TypeInfo TypeInfo}
     */
    @JsonServerMethod(rpc = "Workspace.get_type_info", authOptional=true, async=true)
    public TypeInfo getTypeInfo(String type, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        TypeInfo returnVal = null;
        //BEGIN get_type_info
        TypeDetailedInfo tdi = types.getTypeInfo(
        		type, true, wsmeth.getUser(authPart));
        returnVal = new TypeInfo().withTypeDef(tdi.getTypeDefId())
        		.withDescription(tdi.getDescription())
        		.withSpecDef(tdi.getSpecDef())
        		.withJsonSchema(tdi.getJsonSchema())
        		.withParsingStructure(tdi.getParsingStructure())
        		.withModuleVers(tdi.getModuleVersions())
        		.withReleasedModuleVers(tdi.getReleasedModuleVersions())
        		.withTypeVers(tdi.getTypeVersions())
        		.withReleasedTypeVers(tdi.getReleasedTypeVersions())
        		.withUsingFuncDefs(tdi.getUsingFuncDefIds())
        		.withUsingTypeDefs(tdi.getUsingTypeDefIds())
        		.withUsedTypeDefs(tdi.getUsedTypeDefIds());
        //END get_type_info
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_all_type_info</p>
     * <pre>
     * </pre>
     * @param   mod   instance of original type "modulename" (A module name defined in a KIDL typespec.)
     * @return   instance of list of type {@link us.kbase.workspace.TypeInfo TypeInfo}
     */
    @JsonServerMethod(rpc = "Workspace.get_all_type_info", authOptional=true, async=true)
    public List<TypeInfo> getAllTypeInfo(String mod, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        List<TypeInfo> returnVal = null;
        //BEGIN get_all_type_info
        returnVal = new ArrayList<TypeInfo>();
        ModuleInfo mi = getModuleInfo(new GetModuleInfoParams().withMod(mod),
                authPart, jsonRpcContext);
        for (String typeDef : mi.getTypes().keySet())
        	returnVal.add(getTypeInfo(typeDef, authPart, jsonRpcContext));
        //END get_all_type_info
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_func_info</p>
     * <pre>
     * </pre>
     * @param   func   instance of original type "func_string" (A function string for referencing a funcdef. Specifies the function and its version in a single string in the format [modulename].[funcname]-[major].[minor]: modulename - a string. The name of the module containing the function. funcname - a string. The name of the function as assigned by the funcdef statement. major - an integer. The major version of the function. A change in the major version implies the function has changed in a non-backwards compatible way. minor - an integer. The minor version of the function. A change in the minor version implies that the function has changed in a way that is backwards compatible with previous function definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyFunc-3.1)
     * @return   parameter "info" of type {@link us.kbase.workspace.FuncInfo FuncInfo}
     */
    @JsonServerMethod(rpc = "Workspace.get_func_info", authOptional=true, async=true)
    public FuncInfo getFuncInfo(String func, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        FuncInfo returnVal = null;
        //BEGIN get_func_info
        FuncDetailedInfo fdi = types.getFuncInfo(
        		func, true, wsmeth.getUser(authPart));
        returnVal = new FuncInfo().withFuncDef(fdi.getFuncDefId())
        		.withDescription(fdi.getDescription())
        		.withSpecDef(fdi.getSpecDef())
        		.withParsingStructure(fdi.getParsingStructure())
        		.withModuleVers(fdi.getModuleVersions())
        		.withReleasedModuleVers(fdi.getReleasedModuleVersions())
        		.withFuncVers(fdi.getFuncVersions())
        		.withReleasedFuncVers(fdi.getReleasedFuncVersions())
        		.withUsedTypeDefs(fdi.getUsedTypeDefIds());
        //END get_func_info
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: get_all_func_info</p>
     * <pre>
     * </pre>
     * @param   mod   instance of original type "modulename" (A module name defined in a KIDL typespec.)
     * @return   parameter "info" of list of type {@link us.kbase.workspace.FuncInfo FuncInfo}
     */
    @JsonServerMethod(rpc = "Workspace.get_all_func_info", authOptional=true, async=true)
    public List<FuncInfo> getAllFuncInfo(String mod, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        List<FuncInfo> returnVal = null;
        //BEGIN get_all_func_info
        returnVal = new ArrayList<FuncInfo>();
        ModuleInfo mi = getModuleInfo(new GetModuleInfoParams().withMod(mod),
                authPart, jsonRpcContext);
        for (String funcDef : mi.getFunctions())
        	returnVal.add(getFuncInfo(funcDef, authPart, jsonRpcContext));
        //END get_all_func_info
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: grant_module_ownership</p>
     * <pre>
     * Grant ownership of a module. You must have grant ability on the
     * module.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.GrantModuleOwnershipParams GrantModuleOwnershipParams}
     */
    @JsonServerMethod(rpc = "Workspace.grant_module_ownership", async=true)
    public void grantModuleOwnership(GrantModuleOwnershipParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        //BEGIN grant_module_ownership
		wsmeth.grantModuleOwnership(params, wsmeth.getUser(authPart), false);
        //END grant_module_ownership
    }

    /**
     * <p>Original spec-file function name: remove_module_ownership</p>
     * <pre>
     * Remove ownership from a current owner. You must have the grant ability
     * on the module.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.RemoveModuleOwnershipParams RemoveModuleOwnershipParams}
     */
    @JsonServerMethod(rpc = "Workspace.remove_module_ownership", async=true)
    public void removeModuleOwnership(RemoveModuleOwnershipParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        //BEGIN remove_module_ownership
		wsmeth.removeModuleOwnership(params, wsmeth.getUser(authPart), false);
        //END remove_module_ownership
    }

    /**
     * <p>Original spec-file function name: list_all_types</p>
     * <pre>
     * List all released types with released version from all modules. Return
     * mapping from module name to mapping from type name to released type
     * version.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.ListAllTypesParams ListAllTypesParams}
     * @return   instance of mapping from original type "modulename" (A module name defined in a KIDL typespec.) to mapping from original type "typename" (A type definition name in a KIDL typespec.) to original type "typever" (A version of a type. Specifies the version of the type  in a single string in the format [major].[minor]: major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions.)
     */
    @JsonServerMethod(rpc = "Workspace.list_all_types", authOptional=true, async=true)
    public Map<String,Map<String,String>> listAllTypes(ListAllTypesParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        Map<String,Map<String,String>> returnVal = null;
        //BEGIN list_all_types
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		returnVal = types.listAllTypes(params.getWithEmptyModules() != null &&
				params.getWithEmptyModules() != 0L);
        //END list_all_types
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: administer</p>
     * <pre>
     * The administration interface.
     * </pre>
     * @param   command   instance of unspecified object
     * @return   parameter "response" of unspecified object
     */
    @JsonServerMethod(rpc = "Workspace.administer", async=true)
    public UObject administer(UObject command, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        UObject returnVal = null;
        //BEGIN administer
		returnVal = new UObject(wsadmin.runCommand(authPart, command, resourcesToDelete));
        //END administer
        return returnVal;
    }
    @JsonServerMethod(rpc = "Workspace.status")
    public Map<String, Object> status() {
        Map<String, Object> returnVal = null;
        //BEGIN_STATUS
		//note failures are tested manually for now, if you make changes test
		//things still work
		//TODO TEST when the client supports this method
		//TODO TEST add tests exercising failures
		returnVal = new LinkedHashMap<String, Object>();
		final List<DependencyStatus> deps = ws.status();
		if (wsmeth.getHandleServiceURL() != null) {
			deps.add(wsmeth.checkHandleService());
		}
		if (handleManagerUrl != null) {
			deps.add(checkHandleManager());
		}
		boolean ok = true;
		final List<Map<String, String>> dstate = new LinkedList<>();
		for (final DependencyStatus ds: deps) {
			if (!ds.isOk()) {
				ok = false;
			}
			final Map<String, String> d = new HashMap<String, String>();
			d.put("state", ds.isOk() ? "OK" : "Fail");
			d.put("name", ds.getName());
			d.put("message", ds.getStatus());
			d.put("version", ds.getVersion());
			dstate.add(d);
		}
		returnVal.put("state", ok ? "OK" : "Fail");
		returnVal.put("message", ok ? "OK" : "Dependency failure");
		returnVal.put("dependencies", dstate);
		returnVal.put("version", VER);
		returnVal.put("git_url", GIT);
		returnVal.put("freemem", Runtime.getRuntime().freeMemory());
		returnVal.put("totalmem", Runtime.getRuntime().totalMemory());
		returnVal.put("maxmem", Runtime.getRuntime().maxMemory());
		@SuppressWarnings("unused")
		final String v = version;
		@SuppressWarnings("unused")
		final String h = gitCommitHash;
		@SuppressWarnings("unused")
		final String u = gitUrl;
        //END_STATUS
        return returnVal;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            new WorkspaceServer().startupServer(Integer.parseInt(args[0]));
        } else if (args.length == 3) {
            JsonServerSyslog.setStaticUseSyslog(false);
            JsonServerSyslog.setStaticMlogFile(args[1] + ".log");
            new WorkspaceServer().processRpcCall(new File(args[0]), new File(args[1]), args[2]);
        } else {
            System.out.println("Usage: <program> <server_port>");
            System.out.println("   or: <program> <context_json_file> <output_json_file> <token>");
            return;
        }
    }
}

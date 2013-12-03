package us.kbase.workspace;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import us.kbase.auth.AuthToken;
import us.kbase.common.service.JsonClientCaller;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.Tuple11;
import us.kbase.common.service.Tuple12;
import us.kbase.common.service.Tuple7;
import us.kbase.common.service.Tuple8;
import us.kbase.common.service.UObject;
import us.kbase.common.service.UnauthorizedException;

/**
 * <p>Original spec-file module name: Workspace</p>
 * <pre>
 * The workspace service at its core is a storage and retrieval system for 
 * typed objects. Objects are organized by the user into one or more workspaces.
 * Features:
 * Versioning of objects
 * Data provenenance
 * Object to object references
 * Workspace sharing
 * ***Add stuff here***
 * Notes about deletion and GC
 * BINARY DATA:
 * All binary data must be hex encoded prior to storage in a workspace. 
 * Attempting to send binary data via a workspace client will cause errors.
 * </pre>
 */
public class WorkspaceClient {
    private JsonClientCaller caller;
    private static URL DEFAULT_URL = null;
    static {
        try {
            DEFAULT_URL = new URL("http://kbase.us/services/workspace/");
        } catch (MalformedURLException mue) {
            throw new RuntimeException("Compile error in client - bad url compiled");
        }
    }

    public WorkspaceClient() {
       caller = new JsonClientCaller(DEFAULT_URL);
    }

    public WorkspaceClient(URL url) {
        caller = new JsonClientCaller(url);
    }

    public WorkspaceClient(URL url, AuthToken token) throws UnauthorizedException, IOException {
        caller = new JsonClientCaller(url, token);
    }

    public WorkspaceClient(URL url, String user, String password) throws UnauthorizedException, IOException {
        caller = new JsonClientCaller(url, user, password);
    }

    public WorkspaceClient(AuthToken token) throws UnauthorizedException, IOException {
        caller = new JsonClientCaller(DEFAULT_URL, token);
    }

    public WorkspaceClient(String user, String password) throws UnauthorizedException, IOException {
        caller = new JsonClientCaller(DEFAULT_URL, user, password);
    }

	public void setConnectionReadTimeOut(Integer milliseconds) {
		this.caller.setConnectionReadTimeOut(milliseconds);
	}

    public boolean isAuthAllowedForHttp() {
        return caller.isAuthAllowedForHttp();
    }

    public void setAuthAllowedForHttp(boolean isAuthAllowedForHttp) {
        caller.setAuthAllowedForHttp(isAuthAllowedForHttp);
    }

    /**
     * <p>Original spec-file function name: create_workspace</p>
     * <pre>
     * Creates a new workspace.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.CreateWorkspaceParams CreateWorkspaceParams}
     * @return   parameter "info" of original type "workspace_info" (Information about a workspace. ws_id id - the numerical ID of the workspace. ws_name workspace - name of the workspace. username owner - name of the user who owns (e.g. created) this workspace. timestamp moddate - date when the workspace was last modified. int objects - the approximate number of objects currently stored in the workspace. permission user_permission - permissions for the authenticated user of this workspace. permission globalread - whether this workspace is globally readable.) &rarr; tuple of size 8: parameter "id" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "owner" of original type "username" (Login name of a KBase user account.), parameter "moddate" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "object" of Long, parameter "user_permission" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "globalread" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "lockstat" of original type "lock_status" (The lock status of a workspace. One of 'unlocked', 'locked', or 'published'.)
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public Tuple8<Long, String, String, String, Long, String, String, String> createWorkspace(CreateWorkspaceParams params) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(params);
        TypeReference<List<Tuple8<Long, String, String, String, Long, String, String, String>>> retType = new TypeReference<List<Tuple8<Long, String, String, String, Long, String, String, String>>>() {};
        List<Tuple8<Long, String, String, String, Long, String, String, String>> res = caller.jsonrpcCall("Workspace.create_workspace", args, retType, true, true);
        return res.get(0);
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
     * @return   parameter "info" of original type "workspace_info" (Information about a workspace. ws_id id - the numerical ID of the workspace. ws_name workspace - name of the workspace. username owner - name of the user who owns (e.g. created) this workspace. timestamp moddate - date when the workspace was last modified. int objects - the approximate number of objects currently stored in the workspace. permission user_permission - permissions for the authenticated user of this workspace. permission globalread - whether this workspace is globally readable.) &rarr; tuple of size 8: parameter "id" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "owner" of original type "username" (Login name of a KBase user account.), parameter "moddate" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "object" of Long, parameter "user_permission" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "globalread" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "lockstat" of original type "lock_status" (The lock status of a workspace. One of 'unlocked', 'locked', or 'published'.)
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public Tuple8<Long, String, String, String, Long, String, String, String> lockWorkspace(WorkspaceIdentity wsi) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(wsi);
        TypeReference<List<Tuple8<Long, String, String, String, Long, String, String, String>>> retType = new TypeReference<List<Tuple8<Long, String, String, String, Long, String, String, String>>>() {};
        List<Tuple8<Long, String, String, String, Long, String, String, String>> res = caller.jsonrpcCall("Workspace.lock_workspace", args, retType, true, true);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: get_workspacemeta</p>
     * <pre>
     * Retrieves the metadata associated with the specified workspace.
     * Provided for backwards compatibility. 
     * @deprecated Workspace.get_workspace_info
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.GetWorkspacemetaParams GetWorkspacemetaParams} (original type "get_workspacemeta_params")
     * @return   parameter "metadata" of original type "workspace_metadata" (Meta data associated with a workspace. Provided for backwards compatibility. To be replaced by workspace_info. ws_name id - name of the workspace username owner - name of the user who owns (who created) this object timestamp moddate - date when the workspace was last modified int objects - the approximate number of objects currently stored in the workspace. permission user_permission - permissions for the currently logged in user for the workspace permission global_permission - default permissions for the workspace for all KBase users ws_id num_id - numerical ID of the workspace @deprecated Workspace.workspace_info) &rarr; tuple of size 7: parameter "id" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "owner" of original type "username" (Login name of a KBase user account.), parameter "moddate" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "objects" of Long, parameter "user_permission" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "global_permission" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "num_id" of original type "ws_id" (The unique, permanent numerical ID of a workspace.)
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public Tuple7<String, String, String, Long, String, String, Long> getWorkspacemeta(GetWorkspacemetaParams params) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(params);
        TypeReference<List<Tuple7<String, String, String, Long, String, String, Long>>> retType = new TypeReference<List<Tuple7<String, String, String, Long, String, String, Long>>>() {};
        List<Tuple7<String, String, String, Long, String, String, Long>> res = caller.jsonrpcCall("Workspace.get_workspacemeta", args, retType, true, false);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: get_workspace_info</p>
     * <pre>
     * Get information associated with a workspace.
     * </pre>
     * @param   wsi   instance of type {@link us.kbase.workspace.WorkspaceIdentity WorkspaceIdentity}
     * @return   parameter "info" of original type "workspace_info" (Information about a workspace. ws_id id - the numerical ID of the workspace. ws_name workspace - name of the workspace. username owner - name of the user who owns (e.g. created) this workspace. timestamp moddate - date when the workspace was last modified. int objects - the approximate number of objects currently stored in the workspace. permission user_permission - permissions for the authenticated user of this workspace. permission globalread - whether this workspace is globally readable.) &rarr; tuple of size 8: parameter "id" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "owner" of original type "username" (Login name of a KBase user account.), parameter "moddate" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "object" of Long, parameter "user_permission" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "globalread" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "lockstat" of original type "lock_status" (The lock status of a workspace. One of 'unlocked', 'locked', or 'published'.)
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public Tuple8<Long, String, String, String, Long, String, String, String> getWorkspaceInfo(WorkspaceIdentity wsi) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(wsi);
        TypeReference<List<Tuple8<Long, String, String, String, Long, String, String, String>>> retType = new TypeReference<List<Tuple8<Long, String, String, String, Long, String, String, String>>>() {};
        List<Tuple8<Long, String, String, String, Long, String, String, String>> res = caller.jsonrpcCall("Workspace.get_workspace_info", args, retType, true, false);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: get_workspace_description</p>
     * <pre>
     * Get a workspace's description.
     * </pre>
     * @param   wsi   instance of type {@link us.kbase.workspace.WorkspaceIdentity WorkspaceIdentity}
     * @return   parameter "description" of String
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public String getWorkspaceDescription(WorkspaceIdentity wsi) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(wsi);
        TypeReference<List<String>> retType = new TypeReference<List<String>>() {};
        List<String> res = caller.jsonrpcCall("Workspace.get_workspace_description", args, retType, true, false);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: set_permissions</p>
     * <pre>
     * Set permissions for a workspace.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.SetPermissionsParams SetPermissionsParams}
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public void setPermissions(SetPermissionsParams params) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(params);
        TypeReference<Object> retType = new TypeReference<Object>() {};
        caller.jsonrpcCall("Workspace.set_permissions", args, retType, false, true);
    }

    /**
     * <p>Original spec-file function name: set_global_permission</p>
     * <pre>
     * Set the global permission for a workspace.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.SetGlobalPermissionsParams SetGlobalPermissionsParams}
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public void setGlobalPermission(SetGlobalPermissionsParams params) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(params);
        TypeReference<Object> retType = new TypeReference<Object>() {};
        caller.jsonrpcCall("Workspace.set_global_permission", args, retType, false, true);
    }

    /**
     * <p>Original spec-file function name: set_workspace_description</p>
     * <pre>
     * Set the description for a workspace.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.SetWorkspaceDescriptionParams SetWorkspaceDescriptionParams}
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public void setWorkspaceDescription(SetWorkspaceDescriptionParams params) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(params);
        TypeReference<Object> retType = new TypeReference<Object>() {};
        caller.jsonrpcCall("Workspace.set_workspace_description", args, retType, false, true);
    }

    /**
     * <p>Original spec-file function name: get_permissions</p>
     * <pre>
     * Get permissions for a workspace.
     * </pre>
     * @param   wsi   instance of type {@link us.kbase.workspace.WorkspaceIdentity WorkspaceIdentity}
     * @return   parameter "perms" of mapping from original type "username" (Login name of a KBase user account.) to original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.)
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public Map<String,String> getPermissions(WorkspaceIdentity wsi) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(wsi);
        TypeReference<List<Map<String,String>>> retType = new TypeReference<List<Map<String,String>>>() {};
        List<Map<String,String>> res = caller.jsonrpcCall("Workspace.get_permissions", args, retType, true, true);
        return res.get(0);
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
     * @return   parameter "metadata" of original type "object_metadata" (Meta data associated with an object stored in a workspace. Provided for backwards compatibility. obj_name id - name of the object. type_string type - type of the object. timestamp moddate - date when the object was saved obj_ver instance - the version of the object string command - Deprecated. Always returns the empty string. username lastmodifier - name of the user who last saved the object, including copying the object username owner - Deprecated. Same as lastmodifier. ws_name workspace - name of the workspace in which the object is stored string ref - Deprecated. Always returns the empty string. string chsum - the md5 checksum of the object. usermeta metadata - arbitrary user-supplied metadata about the object. obj_id objid - the numerical id of the object.) &rarr; tuple of size 12: parameter "id" of original type "obj_name" (A string used as a name for an object. Any string consisting of alphanumeric characters and the characters |._- that is not an integer is acceptable.), parameter "type" of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1), parameter "moddate" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "instance" of Long, parameter "command" of String, parameter "lastmodifier" of original type "username" (Login name of a KBase user account.), parameter "owner" of original type "username" (Login name of a KBase user account.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "ref" of String, parameter "chsum" of String, parameter "metadata" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String, parameter "objid" of original type "obj_id" (The unique, permanent numerical ID of an object.)
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String,String>, Long> saveObject(SaveObjectParams params) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(params);
        TypeReference<List<Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String,String>, Long>>> retType = new TypeReference<List<Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String,String>, Long>>>() {};
        List<Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String,String>, Long>> res = caller.jsonrpcCall("Workspace.save_object", args, retType, true, false);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: save_objects</p>
     * <pre>
     * Save objects to the workspace. Saving over a deleted object undeletes
     * it.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.SaveObjectsParams SaveObjectsParams}
     * @return   parameter "info" of list of original type "object_info" (Information about an object, including user provided metadata. obj_id objid - the numerical id of the object. obj_name name - the name of the object. type_string type - the type of the object. timestamp save_date - the save date of the object. obj_ver ver - the version of the object. username saved_by - the user that saved or copied the object. ws_id wsid - the workspace containing the object. ws_name workspace - the workspace containing the object. string chsum - the md5 checksum of the object. int size - the size of the object in bytes. usermeta meta - arbitrary user-supplied metadata about the object.) &rarr; tuple of size 11: parameter "objid" of original type "obj_id" (The unique, permanent numerical ID of an object.), parameter "name" of original type "obj_name" (A string used as a name for an object. Any string consisting of alphanumeric characters and the characters |._- that is not an integer is acceptable.), parameter "type" of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1), parameter "save_date" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "version" of Long, parameter "saved_by" of original type "username" (Login name of a KBase user account.), parameter "wsid" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "chsum" of String, parameter "size" of Long, parameter "meta" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>> saveObjects(SaveObjectsParams params) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(params);
        TypeReference<List<List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>>>> retType = new TypeReference<List<List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>>>>() {};
        List<List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>>> res = caller.jsonrpcCall("Workspace.save_objects", args, retType, true, true);
        return res.get(0);
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
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public GetObjectOutput getObject(GetObjectParams params) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(params);
        TypeReference<List<GetObjectOutput>> retType = new TypeReference<List<GetObjectOutput>>() {};
        List<GetObjectOutput> res = caller.jsonrpcCall("Workspace.get_object", args, retType, true, false);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: get_objects</p>
     * <pre>
     * Get objects from the workspace.
     * </pre>
     * @param   objectIds   instance of list of type {@link us.kbase.workspace.ObjectIdentity ObjectIdentity}
     * @return   parameter "data" of list of type {@link us.kbase.workspace.ObjectData ObjectData}
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public List<ObjectData> getObjects(List<ObjectIdentity> objectIds) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(objectIds);
        TypeReference<List<List<ObjectData>>> retType = new TypeReference<List<List<ObjectData>>>() {};
        List<List<ObjectData>> res = caller.jsonrpcCall("Workspace.get_objects", args, retType, true, false);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: get_object_history</p>
     * <pre>
     * Get an object's history. The version argument of the ObjectIdentity is
     * ignored.
     * </pre>
     * @param   object   instance of type {@link us.kbase.workspace.ObjectIdentity ObjectIdentity}
     * @return   parameter "history" of list of original type "object_info" (Information about an object, including user provided metadata. obj_id objid - the numerical id of the object. obj_name name - the name of the object. type_string type - the type of the object. timestamp save_date - the save date of the object. obj_ver ver - the version of the object. username saved_by - the user that saved or copied the object. ws_id wsid - the workspace containing the object. ws_name workspace - the workspace containing the object. string chsum - the md5 checksum of the object. int size - the size of the object in bytes. usermeta meta - arbitrary user-supplied metadata about the object.) &rarr; tuple of size 11: parameter "objid" of original type "obj_id" (The unique, permanent numerical ID of an object.), parameter "name" of original type "obj_name" (A string used as a name for an object. Any string consisting of alphanumeric characters and the characters |._- that is not an integer is acceptable.), parameter "type" of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1), parameter "save_date" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "version" of Long, parameter "saved_by" of original type "username" (Login name of a KBase user account.), parameter "wsid" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "chsum" of String, parameter "size" of Long, parameter "meta" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>> getObjectHistory(ObjectIdentity object) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(object);
        TypeReference<List<List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>>>> retType = new TypeReference<List<List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>>>>() {};
        List<List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>>> res = caller.jsonrpcCall("Workspace.get_object_history", args, retType, true, false);
        return res.get(0);
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
     * @return   parameter "workspaces" of list of original type "workspace_metadata" (Meta data associated with a workspace. Provided for backwards compatibility. To be replaced by workspace_info. ws_name id - name of the workspace username owner - name of the user who owns (who created) this object timestamp moddate - date when the workspace was last modified int objects - the approximate number of objects currently stored in the workspace. permission user_permission - permissions for the currently logged in user for the workspace permission global_permission - default permissions for the workspace for all KBase users ws_id num_id - numerical ID of the workspace @deprecated Workspace.workspace_info) &rarr; tuple of size 7: parameter "id" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "owner" of original type "username" (Login name of a KBase user account.), parameter "moddate" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "objects" of Long, parameter "user_permission" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "global_permission" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "num_id" of original type "ws_id" (The unique, permanent numerical ID of a workspace.)
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public List<Tuple7<String, String, String, Long, String, String, Long>> listWorkspaces(ListWorkspacesParams params) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(params);
        TypeReference<List<List<Tuple7<String, String, String, Long, String, String, Long>>>> retType = new TypeReference<List<List<Tuple7<String, String, String, Long, String, String, Long>>>>() {};
        List<List<Tuple7<String, String, String, Long, String, String, Long>>> res = caller.jsonrpcCall("Workspace.list_workspaces", args, retType, true, false);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: list_workspace_info</p>
     * <pre>
     * Early version of list_workspaces.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.ListWorkspaceInfoParams ListWorkspaceInfoParams}
     * @return   parameter "wsinfo" of list of original type "workspace_info" (Information about a workspace. ws_id id - the numerical ID of the workspace. ws_name workspace - name of the workspace. username owner - name of the user who owns (e.g. created) this workspace. timestamp moddate - date when the workspace was last modified. int objects - the approximate number of objects currently stored in the workspace. permission user_permission - permissions for the authenticated user of this workspace. permission globalread - whether this workspace is globally readable.) &rarr; tuple of size 8: parameter "id" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "owner" of original type "username" (Login name of a KBase user account.), parameter "moddate" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "object" of Long, parameter "user_permission" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "globalread" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "lockstat" of original type "lock_status" (The lock status of a workspace. One of 'unlocked', 'locked', or 'published'.)
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public List<Tuple8<Long, String, String, String, Long, String, String, String>> listWorkspaceInfo(ListWorkspaceInfoParams params) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(params);
        TypeReference<List<List<Tuple8<Long, String, String, String, Long, String, String, String>>>> retType = new TypeReference<List<List<Tuple8<Long, String, String, String, Long, String, String, String>>>>() {};
        List<List<Tuple8<Long, String, String, String, Long, String, String, String>>> res = caller.jsonrpcCall("Workspace.list_workspace_info", args, retType, true, false);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: list_workspace_objects</p>
     * <pre>
     * Lists the metadata of all objects in the specified workspace with the
     * specified type (or with any type). Provided for backwards compatibility.
     * @deprecated Workspace.list_objects
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.ListWorkspaceObjectsParams ListWorkspaceObjectsParams} (original type "list_workspace_objects_params")
     * @return   parameter "objects" of list of original type "object_metadata" (Meta data associated with an object stored in a workspace. Provided for backwards compatibility. obj_name id - name of the object. type_string type - type of the object. timestamp moddate - date when the object was saved obj_ver instance - the version of the object string command - Deprecated. Always returns the empty string. username lastmodifier - name of the user who last saved the object, including copying the object username owner - Deprecated. Same as lastmodifier. ws_name workspace - name of the workspace in which the object is stored string ref - Deprecated. Always returns the empty string. string chsum - the md5 checksum of the object. usermeta metadata - arbitrary user-supplied metadata about the object. obj_id objid - the numerical id of the object.) &rarr; tuple of size 12: parameter "id" of original type "obj_name" (A string used as a name for an object. Any string consisting of alphanumeric characters and the characters |._- that is not an integer is acceptable.), parameter "type" of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1), parameter "moddate" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "instance" of Long, parameter "command" of String, parameter "lastmodifier" of original type "username" (Login name of a KBase user account.), parameter "owner" of original type "username" (Login name of a KBase user account.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "ref" of String, parameter "chsum" of String, parameter "metadata" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String, parameter "objid" of original type "obj_id" (The unique, permanent numerical ID of an object.)
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public List<Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String,String>, Long>> listWorkspaceObjects(ListWorkspaceObjectsParams params) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(params);
        TypeReference<List<List<Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String,String>, Long>>>> retType = new TypeReference<List<List<Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String,String>, Long>>>>() {};
        List<List<Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String,String>, Long>>> res = caller.jsonrpcCall("Workspace.list_workspace_objects", args, retType, true, false);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: list_objects</p>
     * <pre>
     * Early version of list_objects.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.ListObjectsParams ListObjectsParams}
     * @return   parameter "objinfo" of list of original type "object_info" (Information about an object, including user provided metadata. obj_id objid - the numerical id of the object. obj_name name - the name of the object. type_string type - the type of the object. timestamp save_date - the save date of the object. obj_ver ver - the version of the object. username saved_by - the user that saved or copied the object. ws_id wsid - the workspace containing the object. ws_name workspace - the workspace containing the object. string chsum - the md5 checksum of the object. int size - the size of the object in bytes. usermeta meta - arbitrary user-supplied metadata about the object.) &rarr; tuple of size 11: parameter "objid" of original type "obj_id" (The unique, permanent numerical ID of an object.), parameter "name" of original type "obj_name" (A string used as a name for an object. Any string consisting of alphanumeric characters and the characters |._- that is not an integer is acceptable.), parameter "type" of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1), parameter "save_date" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "version" of Long, parameter "saved_by" of original type "username" (Login name of a KBase user account.), parameter "wsid" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "chsum" of String, parameter "size" of Long, parameter "meta" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>> listObjects(ListObjectsParams params) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(params);
        TypeReference<List<List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>>>> retType = new TypeReference<List<List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>>>>() {};
        List<List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>>> res = caller.jsonrpcCall("Workspace.list_objects", args, retType, true, false);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: get_objectmeta</p>
     * <pre>
     * Retrieves the metadata for a specified object from the specified
     * workspace. Provides access to metadata for all versions of the object
     * via the instance parameter. Provided for backwards compatibility.
     * @deprecated Workspace.get_object_info
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.GetObjectmetaParams GetObjectmetaParams} (original type "get_objectmeta_params")
     * @return   parameter "metadata" of original type "object_metadata" (Meta data associated with an object stored in a workspace. Provided for backwards compatibility. obj_name id - name of the object. type_string type - type of the object. timestamp moddate - date when the object was saved obj_ver instance - the version of the object string command - Deprecated. Always returns the empty string. username lastmodifier - name of the user who last saved the object, including copying the object username owner - Deprecated. Same as lastmodifier. ws_name workspace - name of the workspace in which the object is stored string ref - Deprecated. Always returns the empty string. string chsum - the md5 checksum of the object. usermeta metadata - arbitrary user-supplied metadata about the object. obj_id objid - the numerical id of the object.) &rarr; tuple of size 12: parameter "id" of original type "obj_name" (A string used as a name for an object. Any string consisting of alphanumeric characters and the characters |._- that is not an integer is acceptable.), parameter "type" of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1), parameter "moddate" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "instance" of Long, parameter "command" of String, parameter "lastmodifier" of original type "username" (Login name of a KBase user account.), parameter "owner" of original type "username" (Login name of a KBase user account.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "ref" of String, parameter "chsum" of String, parameter "metadata" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String, parameter "objid" of original type "obj_id" (The unique, permanent numerical ID of an object.)
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String,String>, Long> getObjectmeta(GetObjectmetaParams params) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(params);
        TypeReference<List<Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String,String>, Long>>> retType = new TypeReference<List<Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String,String>, Long>>>() {};
        List<Tuple12<String, String, String, Long, String, String, String, String, String, String, Map<String,String>, Long>> res = caller.jsonrpcCall("Workspace.get_objectmeta", args, retType, true, false);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: get_object_info</p>
     * <pre>
     * Get information about an object from the workspace.
     * Set includeMetadata true to include the user specified metadata.
     * Otherwise the metadata in the object_info will be null.
     * </pre>
     * @param   objectIds   instance of list of type {@link us.kbase.workspace.ObjectIdentity ObjectIdentity}
     * @param   includeMetadata   instance of original type "boolean" (A boolean. 0 = false, other = true.)
     * @return   parameter "info" of list of original type "object_info" (Information about an object, including user provided metadata. obj_id objid - the numerical id of the object. obj_name name - the name of the object. type_string type - the type of the object. timestamp save_date - the save date of the object. obj_ver ver - the version of the object. username saved_by - the user that saved or copied the object. ws_id wsid - the workspace containing the object. ws_name workspace - the workspace containing the object. string chsum - the md5 checksum of the object. int size - the size of the object in bytes. usermeta meta - arbitrary user-supplied metadata about the object.) &rarr; tuple of size 11: parameter "objid" of original type "obj_id" (The unique, permanent numerical ID of an object.), parameter "name" of original type "obj_name" (A string used as a name for an object. Any string consisting of alphanumeric characters and the characters |._- that is not an integer is acceptable.), parameter "type" of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1), parameter "save_date" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "version" of Long, parameter "saved_by" of original type "username" (Login name of a KBase user account.), parameter "wsid" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "chsum" of String, parameter "size" of Long, parameter "meta" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>> getObjectInfo(List<ObjectIdentity> objectIds, Long includeMetadata) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(objectIds);
        args.add(includeMetadata);
        TypeReference<List<List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>>>> retType = new TypeReference<List<List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>>>>() {};
        List<List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>>> res = caller.jsonrpcCall("Workspace.get_object_info", args, retType, true, false);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: rename_workspace</p>
     * <pre>
     * Rename a workspace.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.RenameWorkspaceParams RenameWorkspaceParams}
     * @return   parameter "renamed" of original type "workspace_info" (Information about a workspace. ws_id id - the numerical ID of the workspace. ws_name workspace - name of the workspace. username owner - name of the user who owns (e.g. created) this workspace. timestamp moddate - date when the workspace was last modified. int objects - the approximate number of objects currently stored in the workspace. permission user_permission - permissions for the authenticated user of this workspace. permission globalread - whether this workspace is globally readable.) &rarr; tuple of size 8: parameter "id" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "owner" of original type "username" (Login name of a KBase user account.), parameter "moddate" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "object" of Long, parameter "user_permission" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "globalread" of original type "permission" (Represents the permissions a user or users have to a workspace: 'a' - administrator. All operations allowed. 'w' - read/write. 'r' - read. 'n' - no permissions.), parameter "lockstat" of original type "lock_status" (The lock status of a workspace. One of 'unlocked', 'locked', or 'published'.)
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public Tuple8<Long, String, String, String, Long, String, String, String> renameWorkspace(RenameWorkspaceParams params) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(params);
        TypeReference<List<Tuple8<Long, String, String, String, Long, String, String, String>>> retType = new TypeReference<List<Tuple8<Long, String, String, String, Long, String, String, String>>>() {};
        List<Tuple8<Long, String, String, String, Long, String, String, String>> res = caller.jsonrpcCall("Workspace.rename_workspace", args, retType, true, true);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: rename_object</p>
     * <pre>
     * Rename an object. User meta data is always returned as null.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.RenameObjectParams RenameObjectParams}
     * @return   parameter "renamed" of original type "object_info" (Information about an object, including user provided metadata. obj_id objid - the numerical id of the object. obj_name name - the name of the object. type_string type - the type of the object. timestamp save_date - the save date of the object. obj_ver ver - the version of the object. username saved_by - the user that saved or copied the object. ws_id wsid - the workspace containing the object. ws_name workspace - the workspace containing the object. string chsum - the md5 checksum of the object. int size - the size of the object in bytes. usermeta meta - arbitrary user-supplied metadata about the object.) &rarr; tuple of size 11: parameter "objid" of original type "obj_id" (The unique, permanent numerical ID of an object.), parameter "name" of original type "obj_name" (A string used as a name for an object. Any string consisting of alphanumeric characters and the characters |._- that is not an integer is acceptable.), parameter "type" of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1), parameter "save_date" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "version" of Long, parameter "saved_by" of original type "username" (Login name of a KBase user account.), parameter "wsid" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "chsum" of String, parameter "size" of Long, parameter "meta" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>> renameObject(RenameObjectParams params) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(params);
        TypeReference<List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>>> retType = new TypeReference<List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>>>() {};
        List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>> res = caller.jsonrpcCall("Workspace.rename_object", args, retType, true, true);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: copy_object</p>
     * <pre>
     * Copy an object. Returns the object_info for the newest version.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.CopyObjectParams CopyObjectParams}
     * @return   parameter "copied" of original type "object_info" (Information about an object, including user provided metadata. obj_id objid - the numerical id of the object. obj_name name - the name of the object. type_string type - the type of the object. timestamp save_date - the save date of the object. obj_ver ver - the version of the object. username saved_by - the user that saved or copied the object. ws_id wsid - the workspace containing the object. ws_name workspace - the workspace containing the object. string chsum - the md5 checksum of the object. int size - the size of the object in bytes. usermeta meta - arbitrary user-supplied metadata about the object.) &rarr; tuple of size 11: parameter "objid" of original type "obj_id" (The unique, permanent numerical ID of an object.), parameter "name" of original type "obj_name" (A string used as a name for an object. Any string consisting of alphanumeric characters and the characters |._- that is not an integer is acceptable.), parameter "type" of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1), parameter "save_date" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "version" of Long, parameter "saved_by" of original type "username" (Login name of a KBase user account.), parameter "wsid" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "chsum" of String, parameter "size" of Long, parameter "meta" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>> copyObject(CopyObjectParams params) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(params);
        TypeReference<List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>>> retType = new TypeReference<List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>>>() {};
        List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>> res = caller.jsonrpcCall("Workspace.copy_object", args, retType, true, true);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: revert_object</p>
     * <pre>
     * Revert an object.
     *         The object specified in the ObjectIdentity is reverted to the version
     *         specified in the ObjectIdentity.
     * </pre>
     * @param   object   instance of type {@link us.kbase.workspace.ObjectIdentity ObjectIdentity}
     * @return   parameter "reverted" of original type "object_info" (Information about an object, including user provided metadata. obj_id objid - the numerical id of the object. obj_name name - the name of the object. type_string type - the type of the object. timestamp save_date - the save date of the object. obj_ver ver - the version of the object. username saved_by - the user that saved or copied the object. ws_id wsid - the workspace containing the object. ws_name workspace - the workspace containing the object. string chsum - the md5 checksum of the object. int size - the size of the object in bytes. usermeta meta - arbitrary user-supplied metadata about the object.) &rarr; tuple of size 11: parameter "objid" of original type "obj_id" (The unique, permanent numerical ID of an object.), parameter "name" of original type "obj_name" (A string used as a name for an object. Any string consisting of alphanumeric characters and the characters |._- that is not an integer is acceptable.), parameter "type" of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1), parameter "save_date" of original type "timestamp" (A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference in time to UTC in the format +/-HHMM, eg: 2012-12-17T23:24:06-0500 (EST time) 2013-04-03T08:56:32+0000 (UTC time)), parameter "version" of Long, parameter "saved_by" of original type "username" (Login name of a KBase user account.), parameter "wsid" of original type "ws_id" (The unique, permanent numerical ID of a workspace.), parameter "workspace" of original type "ws_name" (A string used as a name for a workspace. Any string consisting of alphanumeric characters and "_" that is not an integer is acceptable. The name may optionally be prefixed with the workspace owner's user name and a colon, e.g. kbasetest:my_workspace.), parameter "chsum" of String, parameter "size" of Long, parameter "meta" of original type "usermeta" (User provided metadata about an object. Arbitrary key-value pairs provided by the user.) &rarr; mapping from String to String
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>> revertObject(ObjectIdentity object) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(object);
        TypeReference<List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>>> retType = new TypeReference<List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>>>() {};
        List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String,String>>> res = caller.jsonrpcCall("Workspace.revert_object", args, retType, true, true);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: hide_objects</p>
     * <pre>
     * Hide objects. All versions of an object are hidden, regardless of
     * the version specified in the ObjectIdentity. Hidden objects do not
     * appear in the list_objects method.
     * </pre>
     * @param   objectIds   instance of list of type {@link us.kbase.workspace.ObjectIdentity ObjectIdentity}
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public void hideObjects(List<ObjectIdentity> objectIds) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(objectIds);
        TypeReference<Object> retType = new TypeReference<Object>() {};
        caller.jsonrpcCall("Workspace.hide_objects", args, retType, false, true);
    }

    /**
     * <p>Original spec-file function name: unhide_objects</p>
     * <pre>
     * Unhide objects. All versions of an object are unhidden, regardless
     * of the version specified in the ObjectIdentity.
     * </pre>
     * @param   objectIds   instance of list of type {@link us.kbase.workspace.ObjectIdentity ObjectIdentity}
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public void unhideObjects(List<ObjectIdentity> objectIds) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(objectIds);
        TypeReference<Object> retType = new TypeReference<Object>() {};
        caller.jsonrpcCall("Workspace.unhide_objects", args, retType, false, true);
    }

    /**
     * <p>Original spec-file function name: delete_objects</p>
     * <pre>
     * Delete objects. All versions of an object are deleted, regardless of
     * the version specified in the ObjectIdentity.
     * </pre>
     * @param   objectIds   instance of list of type {@link us.kbase.workspace.ObjectIdentity ObjectIdentity}
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public void deleteObjects(List<ObjectIdentity> objectIds) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(objectIds);
        TypeReference<Object> retType = new TypeReference<Object>() {};
        caller.jsonrpcCall("Workspace.delete_objects", args, retType, false, true);
    }

    /**
     * <p>Original spec-file function name: undelete_objects</p>
     * <pre>
     * Undelete objects. All versions of an object are undeleted, regardless
     * of the version specified in the ObjectIdentity. If an object is not
     * deleted, no error is thrown.
     * </pre>
     * @param   objectIds   instance of list of type {@link us.kbase.workspace.ObjectIdentity ObjectIdentity}
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public void undeleteObjects(List<ObjectIdentity> objectIds) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(objectIds);
        TypeReference<Object> retType = new TypeReference<Object>() {};
        caller.jsonrpcCall("Workspace.undelete_objects", args, retType, false, true);
    }

    /**
     * <p>Original spec-file function name: delete_workspace</p>
     * <pre>
     * Delete a workspace. All objects contained in the workspace are deleted.
     * </pre>
     * @param   wsi   instance of type {@link us.kbase.workspace.WorkspaceIdentity WorkspaceIdentity}
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public void deleteWorkspace(WorkspaceIdentity wsi) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(wsi);
        TypeReference<Object> retType = new TypeReference<Object>() {};
        caller.jsonrpcCall("Workspace.delete_workspace", args, retType, false, true);
    }

    /**
     * <p>Original spec-file function name: undelete_workspace</p>
     * <pre>
     * Undelete a workspace. All objects contained in the workspace are
     * undeleted, regardless of their state at the time the workspace was
     * deleted.
     * </pre>
     * @param   wsi   instance of type {@link us.kbase.workspace.WorkspaceIdentity WorkspaceIdentity}
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public void undeleteWorkspace(WorkspaceIdentity wsi) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(wsi);
        TypeReference<Object> retType = new TypeReference<Object>() {};
        caller.jsonrpcCall("Workspace.undelete_workspace", args, retType, false, true);
    }

    /**
     * <p>Original spec-file function name: request_module_ownership</p>
     * <pre>
     * Request ownership of a module name.
     * </pre>
     * @param   mod   instance of original type "modulename" (The module name of a KIDL typespec.)
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public void requestModuleOwnership(String mod) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(mod);
        TypeReference<Object> retType = new TypeReference<Object>() {};
        caller.jsonrpcCall("Workspace.request_module_ownership", args, retType, false, true);
    }

    /**
     * <p>Original spec-file function name: compile_typespec</p>
     * <pre>
     * Compile a new typespec or recompile an existing typespec. 
     * Also see the release_types function.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.CompileTypespecParams CompileTypespecParams}
     * @return   instance of mapping from original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1) to original type "jsonschema" (The JSON Schema for a type.)
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public Map<String,String> compileTypespec(CompileTypespecParams params) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(params);
        TypeReference<List<Map<String,String>>> retType = new TypeReference<List<Map<String,String>>>() {};
        List<Map<String,String>> res = caller.jsonrpcCall("Workspace.compile_typespec", args, retType, true, true);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: compile_typespec_copy</p>
     * <pre>
     * Compile a copy of new typespec or recompile an existing typespec which is loaded 
     * from another workspace for synchronization. Method returns new version of module 
     * in current workspace. Also see the release_types function.
     * </pre>
     * @param   externalWorkspaceUrl   instance of String
     * @param   mod   instance of original type "modulename" (The module name of a KIDL typespec.)
     * @param   versionInExternalWorkspace   instance of original type "spec_version" (The version of a typespec.)
     * @return   parameter "new_local_version" of original type "spec_version" (The version of a typespec.)
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public Long compileTypespecCopy(String externalWorkspaceUrl, String mod, Long versionInExternalWorkspace) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(externalWorkspaceUrl);
        args.add(mod);
        args.add(versionInExternalWorkspace);
        TypeReference<List<Long>> retType = new TypeReference<List<Long>>() {};
        List<Long> res = caller.jsonrpcCall("Workspace.compile_typespec_copy", args, retType, true, true);
        return res.get(0);
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
     * @param   mod   instance of original type "modulename" (The module name of a KIDL typespec.)
     * @return   parameter "types" of list of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1)
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public List<String> releaseModule(String mod) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(mod);
        TypeReference<List<List<String>>> retType = new TypeReference<List<List<String>>>() {};
        List<List<String>> res = caller.jsonrpcCall("Workspace.release_module", args, retType, true, true);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: list_modules</p>
     * <pre>
     * List typespec modules.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.ListModulesParams ListModulesParams}
     * @return   parameter "modules" of list of original type "modulename" (The module name of a KIDL typespec.)
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public List<String> listModules(ListModulesParams params) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(params);
        TypeReference<List<List<String>>> retType = new TypeReference<List<List<String>>>() {};
        List<List<String>> res = caller.jsonrpcCall("Workspace.list_modules", args, retType, true, false);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: list_module_versions</p>
     * <pre>
     * List typespec module versions.
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.ListModuleVersionsParams ListModuleVersionsParams}
     * @return   parameter "vers" of type {@link us.kbase.workspace.ModuleVersions ModuleVersions}
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public ModuleVersions listModuleVersions(ListModuleVersionsParams params) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(params);
        TypeReference<List<ModuleVersions>> retType = new TypeReference<List<ModuleVersions>>() {};
        List<ModuleVersions> res = caller.jsonrpcCall("Workspace.list_module_versions", args, retType, true, false);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: get_module_info</p>
     * <pre>
     * </pre>
     * @param   params   instance of type {@link us.kbase.workspace.GetModuleInfoParams GetModuleInfoParams}
     * @return   parameter "info" of type {@link us.kbase.workspace.ModuleInfo ModuleInfo}
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public ModuleInfo getModuleInfo(GetModuleInfoParams params) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(params);
        TypeReference<List<ModuleInfo>> retType = new TypeReference<List<ModuleInfo>>() {};
        List<ModuleInfo> res = caller.jsonrpcCall("Workspace.get_module_info", args, retType, true, false);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: get_jsonschema</p>
     * <pre>
     * Get JSON schema for a type.
     * </pre>
     * @param   type   instance of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1)
     * @return   parameter "schema" of original type "jsonschema" (The JSON Schema for a type.)
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public String getJsonschema(String type) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(type);
        TypeReference<List<String>> retType = new TypeReference<List<String>>() {};
        List<String> res = caller.jsonrpcCall("Workspace.get_jsonschema", args, retType, true, false);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: translate_from_MD5_types</p>
     * <pre>
     * Translation from types qualified with MD5 to their semantic versions
     * </pre>
     * @param   arg1   instance of list of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1)
     * @return   instance of mapping from original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1) to list of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1)
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public Map<String,List<String>> translateFromMD5Types(List<String> arg1) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(arg1);
        TypeReference<List<Map<String,List<String>>>> retType = new TypeReference<List<Map<String,List<String>>>>() {};
        List<Map<String,List<String>>> res = caller.jsonrpcCall("Workspace.translate_from_MD5_types", args, retType, true, false);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: translate_to_MD5_types</p>
     * <pre>
     * Translation from types qualified with semantic versions to their MD5'ed versions
     * </pre>
     * @param   arg1   instance of list of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1)
     * @return   instance of mapping from original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1) to original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1)
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public Map<String,String> translateToMD5Types(List<String> arg1) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(arg1);
        TypeReference<List<Map<String,String>>> retType = new TypeReference<List<Map<String,String>>>() {};
        List<Map<String,String>> res = caller.jsonrpcCall("Workspace.translate_to_MD5_types", args, retType, true, false);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: get_type_info</p>
     * <pre>
     * </pre>
     * @param   type   instance of original type "type_string" (A type string. Specifies the type and its version in a single string in the format [module].[typename]-[major].[minor]: module - a string. The module name of the typespec containing the type. typename - a string. The name of the type as assigned by the typedef statement. major - an integer. The major version of the type. A change in the major version implies the type has changed in a non-backwards compatible way. minor - an integer. The minor version of the type. A change in the minor version implies that the type has changed in a way that is backwards compatible with previous type definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyType-3.1)
     * @return   instance of type {@link us.kbase.workspace.TypeInfo TypeInfo}
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public TypeInfo getTypeInfo(String type) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(type);
        TypeReference<List<TypeInfo>> retType = new TypeReference<List<TypeInfo>>() {};
        List<TypeInfo> res = caller.jsonrpcCall("Workspace.get_type_info", args, retType, true, false);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: get_func_info</p>
     * <pre>
     * </pre>
     * @param   func   instance of original type "func_string" (A function string. Specifies the function and its version in a single string in the format [module].[funcname]-[major].[minor]: module - a string. The module name of the typespec containing the function. funcname - a string. The name of the function as assigned by the funcdef statement. major - an integer. The major version of the function. A change in the major version implies the function has changed in a non-backwards compatible way. minor - an integer. The minor version of the function. A change in the minor version implies that the function has changed in a way that is backwards compatible with previous function definitions. In many cases, the major and minor versions are optional, and if not provided the most recent version will be used. Example: MyModule.MyFunc-3.1)
     * @return   instance of type {@link us.kbase.workspace.FuncInfo FuncInfo}
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public FuncInfo getFuncInfo(String func) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(func);
        TypeReference<List<FuncInfo>> retType = new TypeReference<List<FuncInfo>>() {};
        List<FuncInfo> res = caller.jsonrpcCall("Workspace.get_func_info", args, retType, true, false);
        return res.get(0);
    }

    /**
     * <p>Original spec-file function name: administer</p>
     * <pre>
     * The administration interface.
     * </pre>
     * @param   command   instance of unspecified object
     * @return   parameter "response" of unspecified object
     * @throws IOException if an IO exception occurs
     * @throws JsonClientException if a JSON RPC exception occurs
     */
    public UObject administer(UObject command) throws IOException, JsonClientException {
        List<Object> args = new ArrayList<Object>();
        args.add(command);
        TypeReference<List<UObject>> retType = new TypeReference<List<UObject>>() {};
        List<UObject> res = caller.jsonrpcCall("Workspace.administer", args, retType, true, true);
        return res.get(0);
    }
}

package us.kbase.workspace;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import org.codehaus.jackson.type.TypeReference;
import us.kbase.JsonClientCaller;
import us.kbase.Tuple5;
import us.kbase.auth.TokenFormatException;

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
 * TODO
 * BINARY DATA:
 * All binary data must be hex encoded prior to storage in a workspace. 
 * Attempting to send binary data via a workspace client will cause errors.
 * </pre>
 */
public class WorkspaceClient {
    private JsonClientCaller caller;

    public WorkspaceClient(String url) throws MalformedURLException {
        caller = new JsonClientCaller(url);
    }

    public WorkspaceClient(String url, String token) throws
        MalformedURLException, IOException, TokenFormatException {
        caller = new JsonClientCaller(url, token);
    }

    public WorkspaceClient(String url, String user, String password) throws MalformedURLException {
        caller = new JsonClientCaller(url, user, password);
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
     * @param   params   Original type "create_workspace_params" (see {@link us.kbase.workspace.CreateWorkspaceParams CreateWorkspaceParams} for details)
     * @return   Original type "workspace_metadata" (Meta data associated with a workspace. workspace_id workspace - ID of the workspace. username owner - name of the user who owns (e.g. created) this workspace. timestamp moddate - date when the workspace was last modified. permission user_permission - permissions for the authenticated user of this workspace permission globalread - whether this workspace is globally readable.)
     */
    public Tuple5<String, String, String, String, String> createWorkspace(CreateWorkspaceParams params) throws Exception {
        List<Object> args = new ArrayList<Object>();
        args.add(params);
        TypeReference<List<Tuple5<String, String, String, String, String>>> retType = new TypeReference<List<Tuple5<String, String, String, String, String>>>() {};
        List<Tuple5<String, String, String, String, String>> res = caller.jsonrpcCall("Workspace.create_workspace", args, retType, true, true);
        return res.get(0);
    }
}

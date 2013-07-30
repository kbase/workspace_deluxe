package us.kbase.workspace;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import org.codehaus.jackson.type.TypeReference;
import us.kbase.JsonClientCaller;
import us.kbase.Tuple5;

public class WorkspaceClient {
    private JsonClientCaller caller;

    public WorkspaceClient(String url) throws MalformedURLException {
        caller = new JsonClientCaller(url);
    }

    public WorkspaceClient(String url, String token) throws MalformedURLException, IOException {
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

    public Tuple5<String, String, String, String, String> createWorkspace(CreateWorkspaceParams params) throws Exception {
        List<Object> args = new ArrayList<Object>();
        args.add(params);
        TypeReference<List<Tuple5<String, String, String, String, String>>> retType = new TypeReference<List<Tuple5<String, String, String, String, String>>>() {};
        List<Tuple5<String, String, String, String, String>> res = caller.jsonrpcCall("Workspace.create_workspace", args, retType, true, true);
        return res.get(0);
    }
}

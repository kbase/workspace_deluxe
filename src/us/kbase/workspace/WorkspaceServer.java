package us.kbase.workspace;

import us.kbase.JsonServerMethod;
import us.kbase.JsonServerServlet;
import us.kbase.Tuple5;
import us.kbase.auth.AuthUser;

//BEGIN_HEADER
//END_HEADER

public class WorkspaceServer extends JsonServerServlet {
    private static final long serialVersionUID = 1L;

    //BEGIN_CLASS_HEADER
    //END_CLASS_HEADER

    public WorkspaceServer() throws Exception {
        //BEGIN_CONSTRUCTOR
        //END_CONSTRUCTOR
    }

    @JsonServerMethod(rpc = "Workspace.create_workspace")
    public Tuple5<String, String, String, String, String> createWorkspace(CreateWorkspaceParams params, AuthUser authPart) throws Exception {
        Tuple5<String, String, String, String, String> ret = null;
        //BEGIN create_workspace
        //END create_workspace
        return ret;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: <program> <server_port>");
            return;
        }
        new WorkspaceServer().startupServer(Integer.parseInt(args[0]));
    }
}

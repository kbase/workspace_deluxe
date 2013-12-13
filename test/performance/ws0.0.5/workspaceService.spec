/*
=head1 workspaceService

=head2 SYNOPSIS

Workspaces are used in KBase to provide an online location for all data, models, and
analysis results. Workspaces are a powerful tool for managing private data, tracking 
workflow provenance, storing and sharing large datasets, and tracking work history. They
have a number of useful characteristics which you will learn about over the course of the
workspace tutorials:

1.) Multiple users can read and write from the same workspace at the same time, 
facilitating collaboration

2.) When an object is overwritten in a workspace, the previous version is preserved and
easily accessible at any time, enabling the use of workspaces to track object versions

3.) Workspaces have default permissions and user-specific permissions, providing total 
control over the sharing and access of workspace contents

=head2 EXAMPLE OF API USE IN PERL

To use the API, first you need to instantiate a workspace client object:

my $client = Bio::KBase::workspaceService::Client->new(user_id => "user", 
		password => "password");
   
Next, you can run API commands on the client object:
   
my $ws = $client->create_workspace({
	workspace => "foo",
	default_permission => "n"
});
my $objs = $client->list_workspace_objects({
	workspace => "foo"
});
print map { $_->[0] } @$objs;

=head2 AUTHENTICATION

There are several ways to provide authentication for using the workspace
service.
Firstly, one can provide a username and password as in the example above.
Secondly, one can obtain an authorization token via the C<AuthToken.pm> module
(see the documentation for that module) and provide it to the Client->new()
method with the keyword argument C<token>.
Finally, one can provide the token directly to a method via the C<auth>
parameter. If a token is provided directly to a method, this token takes
precedence over any previously provided authorization.
If no authorization is provided only unauthenticated read operations are
allowed.

=head2 WORKSPACE

A workspace is a named collection of objects owned by a specific
user, that may be viewable or editable by other users. Functions that operate
on workspaces take a C<workspace_id>, which is an alphanumeric string that
uniquely identifies a workspace among all workspaces.

*/
module workspaceService {
	/* *********************************************************************************************** */
	/* WORKSPACE DATA TYPES */
	/* *********************************************************************************************** */
	
	/* indicates true or false values, false <= 0, true >=1 */
	typedef int bool;

	/* ID of a job object */
	typedef string job_id;
		
	/* A string used as an ID for a workspace. Any string consisting of alphanumeric characters and "_" is acceptable  */
	typedef string workspace_id;
	
	/* A string indicating the "type" of an object stored in a workspace. Acceptable types are returned by the "get_types()" command  */
	typedef string object_type;
	
	/* ID of an object stored in the workspace. Any string consisting of alphanumeric characters and "-" is acceptable */
	typedef string object_id;
	
	/* Single letter indicating permissions on access to workspace. Options are: 'a' for administative access, 'w' for read/write access, 'r' for read access, and 'n' for no access. For default permissions (e.g. permissions for any user) only 'n' and 'r' are allowed.*/
	typedef string permission;
	
	/* Login name of KBase useraccount to which permissions for workspaces are mapped */
	typedef string username;
	
	/* Exact time for workspace operations. e.g. 2012-12-17T23:24:06 */
	typedef string timestamp;
	
	/* A 36 character string referring to a particular instance of an object in a workspace that lasts forever. Objects should always be retreivable using this ID */
	typedef string workspace_ref;
	
	/* Generic definition for object data stored in the workspace
	   Data objects stored in the workspace could be either a string or a reference to a complex perl data structure. So we can't really formulate a strict type definition for this data.
	   
	   version - for complex data structures, the datastructure should include a version number to enable tracking of changes that may occur to the structure of the data over time
	
	*/
	typedef structure { 
	   int version;
	} ObjectData;
	
	/* Meta data associated with an object stored in a workspace.
	
		object_id id - ID of the object assigned by the user or retreived from the IDserver (e.g. kb|g.0)
		object_type type - type of the object (e.g. Genome)
		timestamp moddate - date when the object was modified by the user (e.g. 2012-12-17T23:24:06)
		int instance - instance of the object, which is equal to the number of times the user has overwritten the object
		string command - name of the command last used to modify or create the object
		username lastmodifier - name of the user who last modified the object
		username owner - name of the user who owns (who created) this object
		workspace_id workspace - ID of the workspace in which the object is currently stored
		workspace_ref ref - a 36 character ID that provides permanent undeniable access to this specific instance of this object
		string chsum - checksum of the associated data object
		mapping<string,string> metadata - custom metadata entered for data object during save operation 
	
	*/
	typedef tuple<object_id id,object_type type,timestamp moddate,int instance,string command,username lastmodifier,username owner,workspace_id workspace,workspace_ref ref,string chsum,mapping<string,string> metadata> object_metadata;
	
	/* Meta data associated with a workspace.
	
		workspace_id id - ID of the object assigned by the user or retreived from the IDserver (e.g. kb|g.0)
		username owner - name of the user who owns (who created) this object
		timestamp moddate - date when the workspace was last modified
		int objects - number of objects currently stored in the workspace
		permission user_permission - permissions for the currently logged user for the workspace
		permission global_permission - default permissions for the workspace for all KBase users
			
	*/
	typedef tuple<workspace_id id,username owner,timestamp moddate,int objects,permission user_permission,permission global_permission> workspace_metadata;
	
	/* Data structures for a job object
		
		job_id id - ID of the job object
		string type - type of the job
		string auth - authentication token of job owner
		string status - current status of job
		mapping<string,string> jobdata;
		string queuetime - time when job was queued
		string starttime - time when job started running
		string completetime - time when the job was completed
		string owner - owner of the job
		string queuecommand - command used to queue job
			
	*/
    typedef structure {
		job_id id;
		string type;
		string auth;
		string status;
		mapping<string,string> jobdata;
		string queuetime;
		string starttime;
		string completetime;
		string owner;
		string queuecommand;
    } JobObject;
	
	/* Settings for user accounts stored in the workspace
	
		workspace_id workspace - the workspace currently selected by the user
			
	*/
	typedef structure { 
	   workspace_id workspace;
	} user_settings;
	
	/* *********************************************************************************************** */
	/* WORKSPACE FUNCTIONS */
	/* *********************************************************************************************** */
	
	/* Input parameters for the "load_media_from_bio" function.
	
		workspace_id mediaWS - ID of workspace where media will be loaded (an optional argument with default "KBaseMedia")
		object_id bioid - ID of biochemistry from which media will be loaded (an optional argument with default "default")
		workspace_id bioWS - ID of workspace with biochemistry from which media will be loaded (an optional argument with default "kbase")
		bool clearExisting - A boolean indicating if existing media in the specified workspace should be cleared (an optional argument with default "0")
		bool overwrite - A boolean indicating if a matching existing media should be overwritten (an optional argument with default "0")
		
	*/
	typedef structure { 
		workspace_id mediaWS;
		object_id bioid;
		workspace_id bioWS;
		bool clearExisting;
		bool overwrite;
		string auth;
		bool asHash;
	} load_media_from_bio_params;
	
	/*
		Creates "Media" objects in the workspace for all media contained in the specified biochemistry
	*/
	funcdef load_media_from_bio(load_media_from_bio_params params) returns (list<object_metadata> mediaMetas) authentication optional;
	
	/* Input parameters for the "import_bio" function.
	
		object_id bioid - ID of biochemistry to be imported (an optional argument with default "default")
		workspace_id bioWS - ID of workspace to which biochemistry will be imported (an optional argument with default "kbase")
		string url - URL from which biochemistry should be retrieved
		bool compressed - boolean indicating if biochemistry is compressed
		bool overwrite - A boolean indicating if a matching existing biochemistry should be overwritten (an optional argument with default "0")
		
	*/
	typedef structure {
		object_id bioid;
		workspace_id bioWS;
		string url;
		bool compressed;
		bool clearExisting;
		bool overwrite;
		string auth;
		bool asHash;
	} import_bio_params;
	
	/*
		Imports a biochemistry from a URL
	*/
	funcdef import_bio(import_bio_params params) returns (object_metadata metadata) authentication optional;
	
	/* Input parameters for the "import_map" function.
	
		object_id mapid - ID of mapping to be imported (an optional argument with default "default")
		workspace_id mapWS - ID of workspace to which mapping will be imported (an optional argument with default "kbase")
		string url - URL from which mapping should be retrieved
		bool compressed - boolean indicating if mapping is compressed
		bool overwrite - A boolean indicating if a matching existing mapping should be overwritten (an optional argument with default "0")
		
	*/
	typedef structure {
		object_id bioid;
		workspace_id bioWS;
		object_id mapid;
		workspace_id mapWS;
		string url;
		bool compressed;
		bool overwrite;
		string auth;
		bool asHash;
	} import_map_params;
	
	/*
		Imports a mapping from a URL
	*/
	funcdef import_map(import_map_params params) returns (object_metadata metadata) authentication optional;
	
	/* Input parameters for the "save_objects function.
	
		object_type type - type of the object to be saved (an essential argument)
		workspace_id workspace - ID of the workspace where the object is to be saved (an essential argument)
		object_id id - ID behind which the object will be saved in the workspace (an essential argument)
		ObjectData data - string or reference to complex datastructure to be saved in the workspace (an essential argument)
		string command - the name of the KBase command that is calling the "save_object" function (an optional argument with default "unknown")
		mapping<string,string> metadata - a hash of metadata to be associated with the object (an optional argument with default "{}")
		string auth - the authentication token of the KBase account to associate this save command
		bool retrieveFromURL - a flag indicating that the "data" argument contains a URL from which the actual data should be downloaded (an optional argument with default "0")
		bool json - a flag indicating if the input data is encoded as a JSON string (an optional argument with default "0")
		bool compressed - a flag indicating if the input data in zipped (an optional argument with default "0")
		bool asHash - a boolean indicating if metadata should be returned as a hash
		
	*/
	typedef structure { 
		object_id id;
		object_type type;
		UnspecifiedObject data;
		workspace_id workspace;
		string command;
		mapping<string,string> metadata;
		string auth;
		bool json;
		bool compressed;
		bool retrieveFromURL;
		bool asHash;
	} save_object_params;
	
	/*
		Saves the input object data and metadata into the selected workspace, returning the object_metadata of the saved object
	*/
	funcdef save_object(save_object_params params) returns (object_metadata metadata) authentication optional;
	
	/* Input parameters for the "delete_object" function.
	
		object_type type - type of the object to be deleted (an essential argument)
		workspace_id workspace - ID of the workspace where the object is to be deleted (an essential argument)
		object_id id - ID of the object to be deleted (an essential argument)
		string auth - the authentication token of the KBase account to associate this deletion command
		bool asHash - a boolean indicating if metadata should be returned as a hash
		
	*/
	typedef structure { 
		object_id id;
		object_type type;
		workspace_id workspace;
		string auth;
		bool asHash;
	} delete_object_params;
	
	/*
		Deletes the specified object from the specified workspace, returning the object_metadata of the deleted object.
		Object is only temporarily deleted and can be recovered by using the revert command.
	*/
	funcdef delete_object(delete_object_params params) returns (object_metadata metadata) authentication optional;
	
	/* Input parameters for the "delete_object_permanently" function.
	
		object_type type - type of the object to be permanently deleted (an essential argument)
		workspace_id workspace - ID of the workspace where the object is to be permanently deleted (an essential argument)
		object_id id - ID of the object to be permanently deleted (an essential argument)
		string auth - the authentication token of the KBase account to associate with this permanent deletion command
		bool asHash - a boolean indicating if metadata should be returned as a hash
		
	*/
	typedef structure { 
		object_id id;
		object_type type;
		workspace_id workspace;
		string auth;
		bool asHash;
	} delete_object_permanently_params;
	
	/*
		Permanently deletes the specified object from the specified workspace.
		This permanently deletes the object and object history, and the data cannot be recovered.
		Objects cannot be permanently deleted unless they've been deleted first.
	*/
	funcdef delete_object_permanently(delete_object_permanently_params params) returns (object_metadata metadata) authentication optional;
	
	/* Input parameters for the "get_object" function.
	
		object_type type - type of the object to be retrieved (an essential argument)
		workspace_id workspace - ID of the workspace containing the object to be retrieved (an essential argument)
		object_id id - ID of the object to be retrieved (an essential argument)
		int instance - Version of the object to be retrieved, enabling retrieval of any previous version of an object (an optional argument; the current version is retrieved if no version is provides)
		string auth - the authentication token of the KBase account to associate with this object retrieval command (an optional argument)
		bool asHash - a boolean indicating if metadata should be returned as a hash
		bool asJSON - indicates that data should be returned in JSON format (an optional argument; default is '0')
			
	*/
	typedef structure { 
		object_id id;
		object_type type;
		workspace_id workspace;
		int instance;
		string auth;
		bool asHash;
		bool asJSON;
	} get_object_params;
	
	/* Output generated by the "get_object" function.
	
		UnspecifiedObject data - data for object retrieved in json format (an essential argument)
		object_metadata metadata - metadata for object retrieved (an essential argument)	
	
	*/
	typedef structure { 
		UnspecifiedObject data;
		object_metadata metadata;
	} get_object_output;
	
	/*
		Retrieves the specified object from the specified workspace.
		Both the object data and metadata are returned.
		This commands provides access to all versions of the object via the instance parameter.
	*/
	funcdef get_object(get_object_params params) returns (get_object_output output) authentication optional;	
	
	/* Input parameters for the "get_object" function.
	
		list<object_id> ids - ID of the object to be retrieved (an essential argument)
		list<object_type> types - type of the object to be retrieved (an essential argument)
		list<workspace_id> workspaces - ID of the workspace containing the object to be retrieved (an essential argument)
		list<int> instances  - Version of the object to be retrieved, enabling retrieval of any previous version of an object (an optional argument; the current version is retrieved if no version is provides)
		string auth - the authentication token of the KBase account to associate with this object retrieval command (an optional argument; user is "public" if auth is not provided)
		bool asHash - a boolean indicating if metadata should be returned as a hash
		bool asJSON - indicates that data should be returned in JSON format (an optional argument; default is '0')
			
	*/
	typedef structure { 
		list<object_id> ids;
		list<object_type> types;
		list<workspace_id> workspaces;
		list<int> instances;
		string auth;
		bool asHash;
		bool asJSON;
	} get_objects_params;
	
	/*
		Retrieves the specified objects from the specified workspaces.
		Both the object data and metadata are returned.
		This commands provides access to all versions of the objects via the instances parameter.
	*/
	funcdef get_objects(get_objects_params params) returns (list<get_object_output> output) authentication optional;
	
	/* Input parameters for the "get_object_by_ref" function.
	
		workspace_ref reference - reference to a specific instance of a specific object in a workspace (an essential argument)
		string auth - the authentication token of the KBase account to associate with this object retrieval command (an optional argument)
		bool asHash - a boolean indicating if metadata should be returned as a hash
		bool asJSON - indicates that data should be returned in JSON format (an optional argument; default is '0')
			
	*/
	typedef structure { 
		workspace_ref reference;
		string auth;
		bool asHash;
		bool asJSON;
	} get_object_by_ref_params;
	/*
		Retrieves the specified object from the specified workspace.
		Both the object data and metadata are returned.
		This commands provides access to all versions of the object via the instance parameter.
	*/
	funcdef get_object_by_ref(get_object_by_ref_params params) returns (get_object_output output) authentication optional;

	/* Input parameters for the "save_object_by_ref" function.
	
		object_id id - ID to which the model should be saved (an essential argument)
		object_type type - type of the object for which metadata is to be retrieved (an essential argument)
		ObjectData data - string or reference to complex datastructure to be saved in the workspace (an essential argument)
		string command - the name of the KBase command that is calling the "save_object" function (an optional argument with default "unknown")
		mapping<string,string> metadata - a hash of metadata to be associated with the object (an optional argument with default "{}")
		workspace_ref reference - reference the object should be saved in
		bool json - a flag indicating if the input data is encoded as a JSON string (an optional argument with default "0")
		bool compressed - a flag indicating if the input data in zipped (an optional argument with default "0")
		bool retrieveFromURL - a flag indicating that the "data" argument contains a URL from which the actual data should be downloaded (an optional argument with default "0")
		bool replace - a flag indicating any existing object located at the specified reference should be overwritten (an optional argument with default "0")
		string auth - the authentication token of the KBase account to associate this save command
		bool asHash - a boolean indicating if metadata should be returned as a hash
			
	*/
	typedef structure { 
		object_id id;
		object_type type;
		ObjectData data;
		string command;
		mapping<string,string> metadata;
		workspace_ref reference;
		bool json;
		bool compressed;
		bool retrieveFromURL;
		bool replace;
		string auth;
		bool asHash;
	} save_object_by_ref_params;
	
	/*
		Retrieves the specified object from the specified workspace.
		Both the object data and metadata are returned.
		This commands provides access to all versions of the object via the instance parameter.
	*/
	funcdef save_object_by_ref(save_object_by_ref_params params) returns (object_metadata metadata) authentication optional;

	/* Input parameters for the "get_objectmeta" function.
	
		object_type type - type of the object for which metadata is to be retrieved (an essential argument)
		workspace_id workspace - ID of the workspace containing the object for which metadata is to be retrieved (an essential argument)
		object_id id - ID of the object for which metadata is to be retrieved (an essential argument)
		int instance - Version of the object for which metadata is to be retrieved, enabling retrieval of any previous version of an object (an optional argument; the current metadata is retrieved if no version is provides)
		string auth - the authentication token of the KBase account to associate with this object metadata retrieval command (an optional argument)
		bool asHash - a boolean indicating if metadata should be returned as a hash
			
	*/
	typedef structure { 
		object_id id;
		object_type type;
		workspace_id workspace;
		int instance;
		string auth;
		bool asHash;
	} get_objectmeta_params;
	
	/*
		Retrieves the metadata for a specified object from the specified workspace.
		This commands provides access to metadata for all versions of the object via the instance parameter.
	*/
	funcdef get_objectmeta(get_objectmeta_params params) returns (object_metadata metadata) authentication optional; 
	
	/* Input parameters for the "get_objectmeta_by_ref" function.
	
		workspace_ref reference - reference to a specific instance of a specific object in a workspace (an essential argument)
		string auth - the authentication token of the KBase account to associate with this object retrieval command (an optional argument)
		bool asHash - a boolean indicating if metadata should be returned as a hash
			
	*/
	typedef structure { 
		workspace_ref reference;
		string auth;
		bool asHash;
	} get_objectmeta_by_ref_params;
	/*
		Retrieves the specified object from the specified workspace.
		Both the object data and metadata are returned.
		This commands provides access to all versions of the object via the instance parameter.
	*/
	funcdef get_objectmeta_by_ref(get_objectmeta_by_ref_params params) returns (object_metadata metadata) authentication optional;
	
	/* Input parameters for the "revert_object" function.
	
		object_type type - type of the object to be reverted (an essential argument)
		workspace_id workspace - ID of the workspace containing the object to be reverted (an essential argument)
		object_id id - ID of the object to be reverted (an essential argument)
		int instance - Previous version of the object to which the object should be reset (an essential argument)
		string auth - the authentication token of the KBase account to associate with this object reversion command
		bool asHash - a boolean indicating if metadata should be returned as a hash
			
	*/
	typedef structure { 
		object_id id;
		object_type type;
		workspace_id workspace;
		int instance;
		string auth;
		bool asHash;
	} revert_object_params;
	
	/*
		Reverts a specified object in a specifed workspace to a previous version of the object.
		Returns the metadata of the newly reverted object.
		This command still makes a new instance of the object, copying data related to the target instance to the new instance.
		This ensures that the object instance always increases and no portion of the object history is ever lost.
	*/
	funcdef revert_object(revert_object_params params) returns (object_metadata metadata) authentication optional;
	
	/* Input parameters for the "copy_object" function.
	
		object_type type - type of the object to be copied (an essential argument)
		workspace_id source_workspace - ID of the workspace containing the object to be copied (an essential argument)
		object_id source_id - ID of the object to be copied (an essential argument)
		int instance - Version of the object to be copied, enabling retrieval of any previous version of an object (an optional argument; the current object is copied if no version is provides)
		workspace_id new_workspace - ID of the workspace the object to be copied to (an essential argument)
		object_id new_id - ID the object is to be copied to (an essential argument)
		string new_workspace_url - URL of workspace server where object should be copied (an optional argument - object will be saved in the same server if not provided)
		string auth - the authentication token of the KBase account to associate with this object copy command (an optional argument)
		bool asHash - a boolean indicating if metadata should be returned as a hash
		
	*/  
	typedef structure { 
		string new_workspace_url;
		object_id new_id;
		workspace_id new_workspace;
		object_id source_id;
		int instance;
		object_type type;
		workspace_id source_workspace;
		string auth;
		bool asHash;
	} copy_object_params;
	
	/*
		Copies a specified object in a specifed workspace to a new ID and/or workspace.
		Returns the metadata of the newly copied object.
		It is possible to use the version parameter to copy any version of a workspace object.
	*/
	funcdef copy_object(copy_object_params params) returns (object_metadata metadata) authentication optional;
	
	/* Input parameters for the "move_object" function.
	
		object_type type - type of the object to be moved (an essential argument)
		workspace_id source_workspace - ID of the workspace containing the object to be moved (an essential argument)
		object_id source_id - ID of the object to be moved (an essential argument)
 		workspace_id new_workspace - ID of the workspace the object to be moved to (an essential argument)
		object_id new_id - ID the object is to be moved to (an essential argument)
		string new_workspace_url - URL of workspace server where object should be copied (an optional argument - object will be saved in the same server if not provided)
		string auth - the authentication token of the KBase account to associate with this object move command
		bool asHash - a boolean indicating if metadata should be returned as a hash
		
	*/
	typedef structure { 
		string new_workspace_url;
		object_id new_id;
		workspace_id new_workspace;
		object_id source_id;
		object_type type;
		workspace_id source_workspace;
		string auth;
		bool asHash;
	} move_object_params;
	
	/*
		Moves a specified object in a specifed workspace to a new ID and/or workspace.
		Returns the metadata of the newly moved object.
	*/
	funcdef move_object(move_object_params params) returns (object_metadata metadata) authentication optional;
	
	/* Input parameters for the "has_object" function.
	
		object_type type - type of the object to be checked for existance (an essential argument)
		workspace_id workspace - ID of the workspace containing the object to be checked for existance (an essential argument)
		object_id id - ID of the object to be checked for existance (an essential argument)
		int instance - Version of the object to be checked for existance (an optional argument; the current object is checked if no version is provided)
		string auth - the authentication token of the KBase account to associate with this object check command (an optional argument)
			
	*/
	typedef structure { 
		object_id id;
		int instance;
		object_type type;
		workspace_id workspace;
		string auth;
	} has_object_params;
	
	/*
		Checks if a specified object in a specifed workspace exists.
		Returns "1" if the object exists, "0" if not
	*/
	funcdef has_object(has_object_params params) returns (bool object_present) authentication optional;
	
	/* Input parameters for the "object_history" function.
	
		object_type type - type of the object to have history printed (an essential argument)
		workspace_id workspace - ID of the workspace containing the object to have history printed (an essential argument)
		object_id id - ID of the object to have history printed (an essential argument)
		string auth - the authentication token of the KBase account to associate with this object history command (an optional argument)
		bool asHash - a boolean indicating if metadata should be returned as a hash
			
	*/
	typedef structure { 
		object_id id;
		object_type type;
		workspace_id workspace;
		string auth;
		bool asHash;
	} object_history_params;
	
	/*
		Returns the metadata associated with every version of a specified object in a specified workspace.
	*/
	funcdef object_history(object_history_params params) returns (list<object_metadata> metadatas) authentication optional;
	
	/* Input parameters for the "create_workspace" function.
	
		workspace_id workspace - ID of the workspace to be created (an essential argument)
		permission default_permission - Default permissions of the workspace to be created. Accepted values are 'a' => admin, 'w' => write, 'r' => read, 'n' => none (optional argument with default "n")
		string auth - the authentication token of the KBase account that will own the created workspace
		bool asHash - a boolean indicating if metadata should be returned as a hash
		
	*/
	typedef structure { 
		workspace_id workspace;
		permission default_permission;
		string auth;
		bool asHash;
	} create_workspace_params;
	
	/*
		Creates a new workspace with the specified name and default permissions.
	*/
	funcdef create_workspace(create_workspace_params params) returns (workspace_metadata metadata) authentication optional;
	
	/* Input parameters for the "get_workspacemeta" function.
	
		workspace_id workspace - ID of the workspace for which metadata should be returned (an essential argument)
		string auth - the authentication token of the KBase account accessing workspace metadata (an optional argument)
		bool asHash - a boolean indicating if metadata should be returned as a hash
		
	*/
	typedef structure { 
		workspace_id workspace;
		string auth;
		bool asHash;
	} get_workspacemeta_params;
	
	/*
		Retreives the metadata associated with the specified workspace.
	*/
	funcdef get_workspacemeta(get_workspacemeta_params params) returns (workspace_metadata metadata) authentication optional;
	
	/* Input parameters for the "get_workspacepermissions" function.
	
		workspace_id workspace - ID of the workspace for which custom user permissions should be returned (an essential argument)
		string auth - the authentication token of the KBase account accessing workspace permissions (an optional argument)
			
	*/
	typedef structure { 
		workspace_id workspace;
		string auth;
	} get_workspacepermissions_params;
	
	/*
		Retreives a list of all users with custom permissions to the workspace if an admin, returns 
		the user's own permissions otherwise.
	*/
	funcdef get_workspacepermissions(get_workspacepermissions_params params) returns (mapping<username,permission> user_permissions) authentication optional;
	
	/* Input parameters for the "delete_workspace" function.
	
		workspace_id workspace - ID of the workspace to be deleted (an essential argument)
		string auth - the authentication token of the KBase account deleting the workspace; must be the workspace owner
		bool asHash - a boolean indicating if metadata should be returned as a hash
		
	*/
	typedef structure { 
		workspace_id workspace;
		string auth;
		bool asHash;
	} delete_workspace_params;
	
	/*
		Deletes a specified workspace with all objects.
	*/
	funcdef delete_workspace(delete_workspace_params params) returns (workspace_metadata metadata) authentication optional;
	
	/* Input parameters for the "clone_workspace" function.
	
		workspace_id current_workspace - ID of the workspace to be cloned (an essential argument)
		workspace_id new_workspace - ID of the workspace to which the cloned workspace will be copied (an essential argument)
		string new_workspace_url - URL of workspace server where workspace should be cloned (an optional argument - workspace will be cloned in the same server if not provided)
		permission default_permission - Default permissions of the workspace created by the cloning process. Accepted values are 'a' => admin, 'w' => write, 'r' => read, 'n' => none (an essential argument)
		string auth - the authentication token of the KBase account that will own the cloned workspace (an optional argument)
		bool asHash - a boolean indicating if metadata should be returned as a hash
		
	*/
	typedef structure { 
		workspace_id new_workspace;
		string new_workspace_url;
		workspace_id current_workspace;
		permission default_permission;
		string auth;
		bool asHash;
	} clone_workspace_params;
	
	/*
		Copies a specified workspace with all objects.
	*/
	funcdef clone_workspace(clone_workspace_params params) returns (workspace_metadata metadata) authentication optional;
	
	/* Input parameters for the "list_workspaces" function.
	
		string auth - the authentication token of the KBase account accessing the list of workspaces (an optional argument)
		bool asHash - a boolean indicating if metadata should be returned as a hash
		bool excludeGlobal - if credentials are supplied and excludeGlobal is true exclude world readable workspaces
		
	*/
	typedef structure { 
		string auth;
		bool asHash;
		bool excludeGlobal;
	} list_workspaces_params;
	
	/*
		Lists the metadata of all workspaces a user has access to.
	*/
	funcdef list_workspaces(list_workspaces_params params) returns (list<workspace_metadata> workspaces) authentication optional;
	
	/* Input parameters for the "list_workspace_objects" function.
	
		workspace_id workspace - ID of the workspace for which objects should be listed (an essential argument)
		string type - type of the objects to be listed (an optional argument; all object types will be listed if left unspecified)
		bool showDeletedObject - a flag that, if set to '1', causes any deleted objects to be included in the output (an optional argument; default is '0')
		string auth - the authentication token of the KBase account listing workspace objects; must have at least 'read' privileges (an optional argument)
		bool asHash - a boolean indicating if metadata should be returned as a hash
		
	*/
	typedef structure { 
	   workspace_id workspace;
	   string type;
	   bool showDeletedObject;
	   string auth;
	   bool asHash;
	} list_workspace_objects_params;
	
	/*
		Lists the metadata of all objects in the specified workspace with the specified type (or with any type).
	*/
	funcdef list_workspace_objects(list_workspace_objects_params params) returns (list<object_metadata> objects) authentication optional;

	/* Input parameters for the "set_global_workspace_permissions" function.
	
		workspace_id workspace - ID of the workspace for which permissions will be set (an essential argument)
		permission new_permission - New default permissions to which the workspace should be set. Accepted values are 'a' => admin, 'w' => write, 'r' => read, 'n' => none (an essential argument)
		string auth - the authentication token of the KBase account changing workspace default permissions; must have 'admin' privelages to workspace
		bool asHash - a boolean indicating if metadata should be returned as a hash

	*/
	typedef structure { 
	   permission new_permission;
	   workspace_id workspace;
	   string auth;
	   bool asHash;
	} set_global_workspace_permissions_params;
	
	/*
		Sets the default permissions for accessing a specified workspace for all users.
		Must have admin privelages to change workspace global permissions.
	*/
	funcdef set_global_workspace_permissions(set_global_workspace_permissions_params params) returns (workspace_metadata metadata) authentication optional;
	
	/* Input parameters for the "set_workspace_permissions" function.
	
		workspace_id workspace - ID of the workspace for which permissions will be set (an essential argument)
		list<username> users - list of users for which workspace privileges are to be reset (an essential argument)
		permission new_permission - New permissions to which all users in the user list will be set for the workspace. Accepted values are 'a' => admin, 'w' => write, 'r' => read, 'n' => none (an essential argument)
		string auth - the authentication token of the KBase account changing workspace permissions; must have 'admin' privelages to workspace
			
	*/
	typedef structure { 
	   list<username> users;
	   permission new_permission;
	   workspace_id workspace;
	   string auth;
	} set_workspace_permissions_params;
	
	/*
		Sets the permissions for a list of users for accessing a specified workspace.
		Must have admin privelages to change workspace permissions. Note that only the workspace owner can change the owner's permissions;
		any other user's attempt to do will silently fail.
	*/
	funcdef set_workspace_permissions(set_workspace_permissions_params params) returns (bool success) authentication optional;
	
	/* Input parameters for the "get_user_settings" function.
	
		string auth - the authentication token of the KBase account changing workspace permissions; must have 'admin' privelages to workspace
			
	*/
	typedef structure { 
	   string auth;
	} get_user_settings_params;
	
	/*
		Retrieves settings for user account, including currently selected workspace
	*/
	funcdef get_user_settings(get_user_settings_params params) returns (user_settings output) authentication optional;

	/* Input parameters for the "set_user_settings" function.
	
		string setting - the setting to be set (an essential argument)
		string value - new value to be set (an essential argument)
		string auth - the authentication token of the KBase account changing workspace permissions; must have 'admin' privelages to workspace
			
	*/
	typedef structure {
		string setting;
		string value;
		string auth;
	} set_user_settings_params;
	
	/*
		Retrieves settings for user account, including currently selected workspace
	*/
	funcdef set_user_settings(set_user_settings_params params) returns (user_settings output) authentication optional;

	/* Input parameters for the "queue_job" function.
	
		string auth - the authentication token of the KBase account queuing the job; must have access to the job being queued (an optional argument)
		string state - the initial state to assign to the job being queued (an optional argument; default is "queued")
		string type - the type of the job being queued
		mapping<string,string> jobdata - hash of data associated with job
			
	*/
	typedef structure {
		string auth;
		string state;
		string type;
		string queuecommand;
		mapping<string,string> jobdata;
	} queue_job_params;
	
	/*
		Queues a new job in the workspace.
	*/
	funcdef queue_job(queue_job_params params) returns (JobObject job) authentication optional;

	/* Input parameters for the "set_job_status" function.
	
		string jobid - ID of the job to be have status changed (an essential argument)
		string status - Status to which job should be changed; accepted values are 'queued', 'running', and 'done' (an essential argument)
		string auth - the authentication token of the KBase account requesting job status; only status for owned jobs can be retrieved (an optional argument)
		string currentStatus - Indicates the current statues of the selected job (an optional argument; default is "undef")
		mapping<string,string> jobdata - hash of data associated with job
		
	*/
	typedef structure {
		string jobid;
		string status;
		string auth;
		string currentStatus;
		mapping<string,string> jobdata;
	} set_job_status_params;
	
	/*
		Changes the current status of a currently queued jobs 
		Used to manage jobs by ensuring multiple server don't claim the same job.
	*/
	funcdef set_job_status(set_job_status_params params) returns (JobObject job) authentication optional;
	
	/* Input parameters for the "get_jobs" function.
		
		list<string> jobids - list of specific jobs to be retrieved (an optional argument; default is an empty list)
		string status - Status of all jobs to be retrieved; accepted values are 'queued', 'running', and 'done' (an essential argument)
		string auth - the authentication token of the KBase account accessing job list; only owned jobs will be returned (an optional argument)
			
	*/
	typedef structure {
		list<string> jobids;
		string type;
		string status;
		string auth;
	} get_jobs_params;
	funcdef get_jobs(get_jobs_params params) returns (list<JobObject> jobs) authentication optional;
	
	/*
		Returns a list of all permanent and optional types currently accepted by the workspace service.
		An object cannot be saved in any workspace if it's type is not on this list. 
	*/
	funcdef get_types() returns (list<string> types);
	
	/* Input parameters for the "add_type" function.
	
		string type - Name of type being added (an essential argument)
		string auth - the authentication token of the KBase account adding a type
			
	*/
	typedef structure {
		string type;
		string auth;
	} add_type_params;
	
	/*
		Adds a new custom type to the workspace service, so that objects of this type may be retreived.
		Cannot add a type that already exists.
	*/
	funcdef add_type(add_type_params params) returns (bool success) authentication optional;
	
	/* Input parameters for the "remove_type" function.
	
		string type - name of custom type to be removed from workspace service (an essential argument)
		string auth - the authentication token of the KBase account removing a custom type
			
	*/
	typedef structure {
		string type;
		string auth;
	} remove_type_params;
	
	/*
		Removes a custom type from the workspace service.
		Permanent types cannot be removed.
	*/
	funcdef remove_type(remove_type_params params) returns (bool success) authentication optional;

	/* Input parameters for the "patch" function.
		
		string patch_id - ID of the patch that should be run on the workspace
		string auth - the authentication token of the KBase account removing a custom type
			
	*/
	typedef structure {
		string patch_id;
		string auth;
	} patch_params;
	
	/*
		This function patches the database after an update. Called remotely, but only callable by the admin user.
	*/
	funcdef patch(patch_params params) returns (bool success) authentication optional;
	
	/* *********************************************************************************************** */
	/* COMMAND LINE API/WEB API CORRESPONDENCE */
	/* *********************************************************************************************** */
	/*
		USER ENVIRONMENT MANAGEMENT FUNCTIONS
		kbws-url => no corresponding function; changes the URL of the workspace service that all commands operate against
		kbws-login => no corresponding function; logs in a user in the persistent environment
		kbws-logout => no corresponding function; logs out a users in the persistent environment
		kbws-whoami => no corresponding function; returns the ID of the currently logged in user
		kbws-workspace => no corresponding function; changes current workspace selected by user
		
		WORKSPACE MANAGEMENT FUNCTIONS
		kbws-createws => create_workspace
		kbws-list => list_workspaces
		kbws-setglobalperm => set_global_workspace_permissions
		kbws-setuserperm => set_workspace_permissions
		kbws-deletews => delete_workspace
		kbws-listobj => list_workspace_objects
		kbws-clone => clone_workspace
		kbws-meta => get_workspacemeta
		kbws-perm => get_workspacepermissions
		
		OBJECT MANAGEMENT FUNCTIONS
		kbws-get => get_object
		kbws-getmeta => get_objectmeta
		kbws-move => move_object
		kbws-copy => copy_object
		kbws-delete => delete_object
		kbws-exists => has_object
		kbws-history => get_object_history
		kbws-load => save_object
		kbws-revert => revert_object
		
		TYPE MANAGEMENT FUNCTIONS
		kbws-addtype => add_type
		kbws-types => get_types
		kbws-removetype => remove_type
	
		JOB MANAGEMENT FUNCTIONS
		kbws-jobs => get_jobs
	*/
};

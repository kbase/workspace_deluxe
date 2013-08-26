/*

The workspace service at its core is a storage and retrieval system for 
typed objects. Objects are organized by the user into one or more workspaces.

Features:

Versioning of objects
Data provenenance
Object to object references
Workspace sharing
***Add stuff here***


BINARY DATA:
All binary data must be hex encoded prior to storage in a workspace. 
Attempting to send binary data via a workspace client will cause errors.

*/
module Workspace {
	
	/* A boolean. 0 = false, other = true. */
	typedef int boolean;
	
	/* The numerical ID of a workspace */
	typedef int ws_id;
	
	/* A string used as a name for a workspace.
		Any string consisting of alphanumeric characters and "_" is acceptable.
	*/
	typedef string ws_name;
	
	/* Represents the permissions a user or users have to a workspace:
		'a' - administrator. All operations allowed.
		'w' - read/write.
		'r' - read.
		'n' - no permissions.
	*/
	typedef string permission;
	
	/* Login name of a KBase user account. */
	typedef string username;
	
	/* A time, e.g. 2012-12-17T23:24:06 */
	typedef string timestamp;
	
	/* A workspace identifier.
		Select a workspace by one, and only one, of the numerical id or name, where the
		name can also be a KBase ID including the numerical id, e.g. kb|ws.35.
		ws_id - the numerical ID of the workspace.
		ws_name workspace - name of the workspace or the workspace ID in KBase format, e.g. kb|ws.78.
	*/
	typedef structure {
		ws_name workspace;
		ws_id id;
	} WorkspaceIdentity;
	
	/* Meta data associated with a workspace.
	
		ws_id id - the numerical ID of the workspace.
		ws_name workspace - name of the workspace.
		username owner - name of the user who owns (e.g. created) this workspace.
		timestamp moddate - date when the workspace was last modified
		permission user_permission - permissions for the authenticated user of this workspace
		permission globalread - whether this workspace is globally readable.
			
	*/
	typedef tuple<ws_id id, ws_name workspace, username owner, timestamp moddate,
		permission user_permission, permission globalread> workspace_metadata;

	/* Input parameters for the "create_workspace" function.
		Required:
		ws_name workspace - name of the workspace to be created
		Optional:
		permission globalread - 'r' to set workspace globally readable, default 'n'.
		string description - A free-text description of the workspace, 1000 characters max. Longer strings will be mercilessly and brutally truncated.
	*/
	typedef structure { 
		ws_name workspace;
		permission globalread;
		string description;
	} CreateWorkspaceParams;
	
	/*
		Creates a new workspace.
	*/
	funcdef create_workspace(CreateWorkspaceParams params) returns
		(workspace_metadata metadata) authentication required;
		
	/*
		Get a workspace's metadata.
	*/
	funcdef get_workspace_metadata(WorkspaceIdentity wsi)
		returns (workspace_metadata meta) authentication optional;
	
	/* 
	
		Get a workspace's description.
	*/
	funcdef get_workspace_description(WorkspaceIdentity wsi)
		returns (string description) authentication optional;
	
	/* Input parameters for the "set_permissions" function.
		One, and only one, of the following is required:
		ws_id id - the numerical ID of the workspace.
		ws_name workspace - name of the workspace or the workspace ID in KBase format, e.g. kb|ws.78.
		Required arguments:
		permission new_permission - the permission to assign to the users
		list<username> users - the users whose permissions will be altered
	*/
	typedef structure {
		ws_name workspace;
		ws_id id;
		permission new_permission;
		list<username> users;
	} SetPermissionsParams;
	
	/* 
		Set permissions for a workspace.
	*/
	funcdef set_permissions(SetPermissionsParams params) returns () authentication required;
	
	/* 
		Get permissions for a workspace.
	*/
	funcdef get_permissions(WorkspaceIdentity wsi) returns
		(mapping<username, permission> perms) authentication required;
		
};

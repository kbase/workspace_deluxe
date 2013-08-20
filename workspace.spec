/*

The workspace service at its core is a storage and retrieval system for 
typed objects. Objects are organized by the user into one or more workspaces.

Features:

Versioning of objects
Data provenenance
Object to object references
Workspace sharing
TODO


BINARY DATA:
All binary data must be hex encoded prior to storage in a workspace. 
Attempting to send binary data via a workspace client will cause errors.

*/
module Workspace {
	
	/* A boolean. 0 = false, other = true. */
	typedef int boolean;
	
	/* A string used as an ID for a workspace.
		Any string consisting of alphanumeric characters and "_" is acceptable.
	*/
	typedef string workspace_id;
	
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
	
	/* Meta data associated with a workspace.
	
		workspace_id workspace - ID of the workspace.
		username owner - name of the user who owns (e.g. created) this workspace.
		timestamp moddate - date when the workspace was last modified.
		permission user_permission - permissions for the authenticated user of this workspace
		permission globalread - whether this workspace is globally readable.
			
	*/
	typedef tuple<workspace_id workspace, username owner, timestamp moddate, permission user_permission, permission globalread> workspace_metadata;

	/* Input parameters for the "create_workspace" function.
		Required:
		workspace_id workspace - ID of the workspace to be created
		Optional:
		permission globalread - 'r' to set workspace globally readable, default 'n'.
		string description - A free-text description of the workspace, 1000 characters max. Longer strings will be mercilessly and brutally truncated.
	*/
	typedef structure { 
		workspace_id workspace;
		permission globalread;
		string description;
	} create_workspace_params;
	
	/*
		Creates a new workspace.
	*/
	funcdef create_workspace(create_workspace_params params) returns (workspace_metadata metadata) authentication required;
	
};

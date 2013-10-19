/*

The workspace service at its core is a storage and retrieval system for 
typed objects. Objects are organized by the user into one or more workspaces.

Features:

Versioning of objects
Data provenenance
Object to object references
Workspace sharing
***Add stuff here***

Notes about deletion and GC

BINARY DATA:
All binary data must be hex encoded prior to storage in a workspace. 
Attempting to send binary data via a workspace client will cause errors.

*/
module Workspace {
	
	/* A boolean. 0 = false, other = true. */
	typedef int boolean;
	
	/* The unique, permanent numerical ID of a workspace. */
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
	
	/* 
		A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is the difference
		in time to UTC in the format +/-HHMM, eg:
			2012-12-17T23:24:06-0500 (EST time)
			2013-04-03T08:56:32+0000 (UTC time)
	*/
	typedef string timestamp;
	
	/* A type id.
		References a type via the format [module].[typename] where the module
		is the module name of the typespec containing the type and the typename
		is the name assigned by a typedef statement.
	*/
	typedef string type_id;
	
	/* A type version.
		Specifies the type version by the format [major].[minor] where 'major'
		is the major (e.g. backward incompatible) version of the type as an
		integer and 'minor' is the minor (e.g. backwards compatible) version
		of the type as an integer.
	*/
	typedef string type_ver;
	
	/* A type string.
		Specifies the type and its version in a single string in the format
		[module].[typename]-[major].[minor]. See type_id and type_ver.
	*/
	typedef string type_string;
	
	/* User provided metadata about an object.
		Arbitrary key-value pairs provided by the user.
	*/
	typedef mapping<string, string> usermeta;
	
	/* A workspace identifier.

		Select a workspace by one, and only one, of the numerical id or name,
			where the name can also be a KBase ID including the numerical id,
			e.g. kb|ws.35.
		ws_id id - the numerical ID of the workspace.
		ws_name workspace - name of the workspace or the workspace ID in KBase
			format, e.g. kb|ws.78.
		
	*/
	typedef structure {
		ws_name workspace;
		ws_id id;
	} WorkspaceIdentity;
	
	/* Meta data associated with a workspace.
	
		ws_id id - the numerical ID of the workspace.
		ws_name workspace - name of the workspace.
		username owner - name of the user who owns (e.g. created) this workspace.
		timestamp moddate - date when the workspace was last modified.
		permission user_permission - permissions for the authenticated user of
			this workspace.
		permission globalread - whether this workspace is globally readable.
			
	*/
	typedef tuple<ws_id id, ws_name workspace, username owner, timestamp moddate,
		permission user_permission, permission globalread> workspace_metadata;
		
	/* The unique, permanent numerical ID of an object. */
	typedef int obj_id;
	
	/* A string used as a name for an object.
		Any string consisting of alphanumeric characters and the characters
			|._- is acceptable.
	*/
	typedef string obj_name;
	
	/* An object version.
		The version of the object, starting at 1.
	*/
	typedef int obj_ver;
	
	/* A string that uniquely identifies an object in the workspace service.
	
		There are several ways to uniquely identify an object in one string:
		"[ws_id].[obj_id].[obj_ver]" - for example, "23.567.2" would identify
			the second version of an object with id 567 in a workspace with id
			23.
		"[ws_name]/[obj_name]/[obj_ver]" - for example,
			"MyFirstWorkspace/MyFirstObject/3" would identify the third version
			of an object called MyFirstObject in the workspace called
			MyFirstWorkspace.
		"kb|ws.[ws_id].obj.[obj_id].ver.[obj_ver]" - for example, 
			"kb|ws.23.obj.567.ver.2" would identify the same object as in the
			first example.
		In all cases, if the version number is omitted, the latest version of
		the object is assumed.
	*/
	typedef string obj_ref;
	
	/* An object identifier.
		
		Select an object by either:
			One, and only one, of the numerical id or name of the workspace,
			where the name can also be a KBase ID including the numerical id,
			e.g. kb|ws.35.
				ws_id wsid - the numerical ID of the workspace.
				ws_name workspace - name of the workspace or the workspace ID
					in KBase format, e.g. kb|ws.78.
			AND 
			One, and only one, of the numerical id or name of the object.
				obj_id objid- the numerical ID of the object.
				obj_name name - name of the object.
			OPTIONALLY
				obj_ver ver - the version of the object.
		OR an object reference string:
			obj_ref ref - an object reference string.
	*/
	typedef structure {
		ws_name workspace;
		ws_id wsid;
		obj_name name;
		obj_id objid;
		obj_ver ver;
		obj_ref ref;
	} ObjectIdentity;
	
	/* Metadata associated with an object.
	
		obj_id objid - the numerical id of the object.
		obj_name name - the name of the object.
		type_string type - the type of the object.
		timestamp create_date - the creation date of the object.
		obj_ver ver - the version of the object.
		username created_by - the user that created the object.
		ws_id wsid - the workspace containing the object.
		string chsum - the md5 checksum of the object.
		int size - the size of the object in bytes.

	*/
	typedef tuple<obj_id objid, obj_name name, type_string type,
		timestamp create_date, int version, username created_by,
		ws_id wsid, string chsum, int size> object_metadata;
		
	/* Metadata associated with an object, including user provided metadata.
	
		obj_id objid - the numerical id of the object.
		obj_name name - the name of the object.
		type_string type - the type of the object.
		timestamp create_date - the creation date of the object.
		obj_ver ver - the version of the object.
		username created_by - the user that created the object.
		ws_id wsid - the workspace containing the object.
		string chsum - the md5 checksum of the object.
		int size - the size of the object in bytes.
		usermeta metadata - arbitrary user-supplied metadata about
			the object.

	*/
	typedef tuple<obj_id objid, obj_name name, type_string type,
		timestamp create_date, int version, username created_by,
		ws_id wsid, string chsum, int size, usermeta metadata>
		object_metadata_full;
	
	/* A provenance action.
	
		A provenance action is an action taken while transforming one data
		object to another. There may be several provenance actions taken in
		series. An action is typically running a script, running an api
		command, etc. All of the following are optional, but more information
		provided equates to better data provenance.
		
		timestamp time - the time the action was started.
		string service - the name of the service that performed this action.
		int service_ver - the version of the service that performed this action.
		string method - the method of the service that performed this action.
		list<UnspecifiedObject> method_params - the parameters of the method
			that performed this action. If the object is a workspace object,
			put the object id in the input_ws_object list and refer to it here
			by the %N syntax described below.
		string script - the name of the script that performed this action.
		int script_ver - the version of the script that performed this action.
		string script_command_line - the command line provided to the script
			that performed this action. If workspace objects were provided in
			the command line, put the object id in the input_ws_object list
			and refer to it here by the %N syntax described below.
		list<ObjectIdentifier> input_ws_objects - the workspace objects that
			were used as input to this action. Refer to these objects
			elsewhere in the action via the syntax %N, where N is the index
			of the object in this list.
		list<string> intermediate_incoming - if the previous action produced 
			output that 1) was not stored in a referrable way, and 2) is
			used as input for this action, provide it with an arbitrary and
			unique ID here, in the order of the input arguments to this action.
			These IDs can be used in the method_params argument.
		list<string> intermediate_outgoing - if this action produced output
			that 1) was not stored in a referrable way, and 2) is
			used as input for the next action, provide it with an arbitrary and
			unique ID here, in the order of the output values from this action.
			These IDs can be used in the intermediate_incoming argument in the
			next action.
		string description - a free text description of this action, limited to
			1000 characters. Longer descriptions will be silently truncated.
	*/
	typedef structure {
		timestamp time;
		string service;
		int service_ver;
		string method;
		list<UnspecifiedObject> method_params;
		string script;
		int script_ver;
		string script_command_line;
		string description;
		list<ObjectIdentity> input_ws_objects;
		list<string> intermediate_incoming;
		list<string> intermediate_outgoing;
		string description;
	} ProvenanceAction;


	/* Input parameters for the "create_workspace" function.
	
		Required arguments:
		ws_name workspace - name of the workspace to be created.
		
		Optional arguments:
		permission globalread - 'r' to set workspace globally readable,
			default 'n'.
		string description - A free-text description of the workspace, 1000
			characters max. Longer strings will be mercilessly and brutally
			truncated.
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
	
	authentication optional;
	
	/*
		Get a workspace's metadata.
	*/
	funcdef get_workspace_metadata(WorkspaceIdentity wsi)
		returns (workspace_metadata meta);
	
	/* 
		Get a workspace's description.
	*/
	funcdef get_workspace_description(WorkspaceIdentity wsi)
		returns (string description);
	
	/* Input parameters for the "set_permissions" function.
	
		One, and only one, of the following is required:
		ws_id id - the numerical ID of the workspace.
		ws_name workspace - name of the workspace or the workspace ID in KBase
			format, e.g. kb|ws.78.
		
		Required arguments:
		permission new_permission - the permission to assign to the users.
		list<username> users - the users whose permissions will be altered.
	*/
	typedef structure {
		ws_name workspace;
		ws_id id;
		permission new_permission;
		list<username> users;
	} SetPermissionsParams;
	
	authentication required;
	
	/* 
		Set permissions for a workspace.
	*/
	funcdef set_permissions(SetPermissionsParams params) returns ();
	
	/* 
		Get permissions for a workspace.
	*/
	funcdef get_permissions(WorkspaceIdentity wsi) returns
		(mapping<username, permission> perms);
	
	/* An object and associated data required for saving.
	
		Required parameters:
		type_id type - the type of the object.
		UnspecifiedObject data - the object data.
		
		Optional parameters:
		One of an object name or id. If no name or id is provided the name
			will be set to the object id as a string, possibly with -\d+
			appended if that object id already exists as a name.
		obj_name name - the name of the object.
		obj_id objid - the id of the object to save over.
		usermeta metadata - arbitrary user-supplied metadata for the object,
			not to exceed 16kb.
		list<ProvenanceAction> provenance - provenance data for the object.
		type_ver tver - the version of the type. If the version or minor
			version is not provided the latest version will be assumed.
		boolean hidden - true if this object should not be listed when listing
			workspace objects.
	
	*/
	typedef structure {
		type_id type;
		UnspecifiedObject data;
		obj_name name;
		obj_id objid;
		usermeta metadata;
		list<ProvenanceAction> provenance;
		type_ver tver;
		boolean hidden;
	} ObjectSaveData;
	
	/* Input parameters for the "save_objects" function.
	
		One, and only one, of the following is required:
		ws_id id - the numerical ID of the workspace.
		ws_name workspace - name of the workspace or the workspace ID in KBase
			format, e.g. kb|ws.78.
		
		Required arguments:
		list<ObjectSaveData> objects - the objects to save.
		
	*/
	typedef structure {
		ws_name workspace;
		ws_id id;
		list<ObjectSaveData> objects;
	} SaveObjectsParams;
	
	/* 
		Save objects to the workspace. Saving over a deleted object undeletes
		it.
	*/
	funcdef save_objects(SaveObjectsParams params)
		returns (list<object_metadata> meta);
	
	/* The data and metadata for an object.
	
		UnspecifiedObject data - the object's data.
		object_metadata_full meta - metadata about the object.
		
	*/
	typedef structure {
		UnspecifiedObject data;
		object_metadata_full meta;
	} ObjectData;
	
	authentication optional;
	
	/* 
		Get objects from the workspace.
	*/
	funcdef get_objects(list<ObjectIdentity> object_ids)
		returns (list<ObjectData> data);
	
	/* 
		Get object metadata from the workspace.
	*/
	funcdef get_object_metadata(list<ObjectIdentity> object_ids)
		returns (list<object_metadata_full> meta);
	
	authentication required;
	
	/* 
		Delete objects. All versions of an object are deleted, regardless of
		the version specified in the ObjectIdentity.
	*/
	funcdef delete_objects(list<ObjectIdentity> object_ids) returns();
	
	/* 
		Undelete objects. All versions of an object are undeleted, regardless
		of the version specified in the ObjectIdentity. If an object is not
		deleted, no error is thrown.
	*/
	funcdef undelete_objects(list<ObjectIdentity> object_ids) returns();
	
	/*
		Delete a workspace. All objects contained in the workspace are deleted.
	*/
	funcdef delete_workspace(WorkspaceIdentity wsi) returns();
	
	/* 
		Undelete a workspace. All objects contained in the workspace are
		undeleted, regardless of their state at the time the workspace was
		deleted.
	*/
	funcdef undelete_workspace(WorkspaceIdentity wsi) returns();
	
	/* **************** Type registering functions ******************** */
	
	/* A KBase Interface Definition Language (KIDL) typespec. */
	typedef string typespec;
	 
	/* The module name of a KIDL typespec. */
	typedef string modulename;
	
	/* The name of a type in a KIDL typespec module. */
	typedef string typename;
	
	/* The version of a typespec. */
	typedef int spec_version;
	
	/* The JSON Schema for a type. */
	typedef string jsonschema;
	
	authentication required;
	
	/* Request ownership of a module name. */
	funcdef request_module_ownership(modulename mod) returns();
	
	/* Parameters for the compile_typespec function.
	
		Required parameters:
		One of:
		typespec spec - the new typespec to compile.
		modulename mod - the module to recompile.
		
		Optional parameters:
		boolean dryrun - Return, but do not save, the results of compiling the 
			spec. Default true. Set to false for making permanent changes.
		list<typename> new_types - types in the spec to make available in the
			workspace service. When compiling a spec for the first time, if
			this argument is empty no types will be made available. Previously
			available types remain so upon recompilation of a spec or
			compilation of a new spec.
		list<typename> remove_types - no longer make these types available in
			the workspace service for the new version of the spec. This does
			not remove versions of types previously compiled.
		mapping<modulename, spec_version> dependencies - By default, the
			latest released versions of spec dependencies will be included when
			compiling a spec. Specific versions can be specified here.
		spec_version prev_ver - the id of the previous version of the typespec.
			An error will be thrown if this is set and prev_ver is not the
			most recent version of the typespec. This prevents overwriting of
			changes made since retrieving a spec and compiling an edited spec.
			This argument is ignored if a modulename is passed.
	*/
	typedef structure {
		typespec spec;
		modulename mod;
		list<typename> new_types;
		list<typename> remove_types;
		mapping<modulename, spec_version> dependencies;
		boolean dryrun;
		spec_version prev_ver;
	} CompileTypespecParams;
	
	/* Compile a new typespec or recompile an existing typespec. 
		Also see the release_types function.
	*/
	funcdef compile_typespec(CompileTypespecParams params)
		returns(mapping<type_string, jsonschema>);
		
	/* Release a module for general use of its types.
		
		Releases the most recent version of a module. Releasing a module does
		two things to the module's types:
		1) If a type's major version is 0, it is changed to 1. A major
			version of 0 implies that the type is in development and may have
			backwards incompatible changes from minor version to minor version.
			Once a type is released, backwards incompatible changes always
			cause a major version increment.
		2) This version of the type becomes the default version, and if a 
			specific version is not supplied in a function call, this version
			will be used. This means that newer, unreleased versions of the
			type may be skipped.
	*/
	funcdef release_module(modulename mod) returns(list<type_string> types);
	
	authentication none;
	
	/* Parameters for the list_modules() function.
	
		Optional parameters:
		username owner - only list modules owned by this user.
	*/
	typedef structure {
		username owner;
	} ListModulesParams;
	
	/* List typespec modules. */
	funcdef list_modules(ListModulesParams params)
		returns(list<modulename> modules);
	
	/* Parameters for the list_module_versions function.
	
		Required arguments:
		One of:
		modulename mod - returns all versions of the module.
		type_string type - returns all versions of the module associated with
			the type.
	*/
	typedef structure {
		modulename mod;
		type_string type;
	} ListModuleVersionsParams;
	
	/* A set of versions from a module.
	
		modulename mod - the name of the module.
		list<spec_version> - a set or subset of versions associated with the
			module.
	*/
	typedef structure {
		modulename mod;
		list<spec_version> vers;
	} ModuleVersions;
	
	/* List typespec module versions. */
	funcdef list_module_versions(ListModuleVersionsParams params)
		 returns(ModuleVersions vers);
	
	/* Parameters for the get_module_info function.
	
		Required parameters:
		modulename mod - the name of the module to retrieve.
		
		Optional parameters:
		spec_version ver - the version of the module to retrieve. Defaults to
			the latest version.
	*/
	typedef structure {
		modulename mod;
		spec_version ver;
	} GetModuleInfoParams;
	
	/* Information about a module.
	
		list<username> owners - the owners of the module.
		spec_version ver - the version of the module.
		typespec spec - the typespec.
		string description - the description of the module from the typespec.
		mapping<type_string, jsonschema> types - the types associated with this
			module and their JSON schema.
	*/
	typedef structure {
		list<username> owners;
		spec_version ver;
		typespec spec;
		string description;
		mapping<type_string, jsonschema> types;
	} ModuleInfo;
	
	funcdef get_module_info(GetModuleInfoParams params)
		returns(ModuleInfo info);
		
	/* Get JSON schema for a type. */
	funcdef get_jsonschema(type_string type) returns (jsonschema schema);
		
	/* The administration interface. */
	funcdef administer(UnspecifiedObject command)
		returns(UnspecifiedObject response) authentication required;
};

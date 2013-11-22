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
		Any string consisting of alphanumeric characters and "_" that is not an
		integer is acceptable.
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
	
	/* A type string.
		Specifies the type and its version in a single string in the format
		[module].[typename]-[major].[minor]:
		
		module - a string. The module name of the typespec containing the type.
		typename - a string. The name of the type as assigned by the typedef
			statement.
		major - an integer. The major version of the type. A change in the
			major version implies the type has changed in a non-backwards
			compatible way.
		minor - an integer. The minor version of the type. A change in the
			minor version implies that the type has changed in a way that is
			backwards compatible with previous type definitions.
		
		In many cases, the major and minor versions are optional, and if not
		provided the most recent version will be used.
		
		Example: MyModule.MyType-3.1
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
	
	/* Meta data associated with a workspace. Provided for backwards
		compatibility. To be replaced by workspace_info.
	
		ws_name id - name of the workspace 
		username owner - name of the user who owns (who created) this object
		timestamp moddate - date when the workspace was last modified
		int objects - the approximate number of objects currently stored in
			the workspace.
		permission user_permission - permissions for the currently logged in
			user for the workspace
		permission global_permission - default permissions for the workspace
			for all KBase users
		ws_id num_id - numerical ID of the workspace
		
		@deprecated Workspace.workspace_info
	*/
	typedef tuple<ws_name id, username owner, timestamp moddate,
		int objects, permission user_permission, permission global_permission,
		ws_id num_id> workspace_metadata;
	
	/* Information about a workspace.
	
		ws_id id - the numerical ID of the workspace.
		ws_name workspace - name of the workspace.
		username owner - name of the user who owns (e.g. created) this workspace.
		timestamp moddate - date when the workspace was last modified.
		int objects - the approximate number of objects currently stored in
			the workspace.
		permission user_permission - permissions for the authenticated user of
			this workspace.
		permission globalread - whether this workspace is globally readable.
			
	*/
	typedef tuple<ws_id id, ws_name workspace, username owner, timestamp moddate,
		int object, permission user_permission, permission globalread>
		workspace_info;
		
	/* The unique, permanent numerical ID of an object. */
	typedef int obj_id;
	
	/* A string used as a name for an object.
		Any string consisting of alphanumeric characters and the characters
			|._- that is not an integer is acceptable.
	*/
	typedef string obj_name;
	
	/* An object version.
		The version of the object, starting at 1.
	*/
	typedef int obj_ver;
	
	/* A string that uniquely identifies an object in the workspace service.
	
		There are two ways to uniquely identify an object in one string:
		"[ws_name or id]/[obj_name or id]/[obj_ver]" - for example,
			"MyFirstWorkspace/MyFirstObject/3" would identify the third version
			of an object called MyFirstObject in the workspace called
			MyFirstWorkspace. 42/Panic/1 would identify the first version of
			the object name Panic in workspace with id 42. Towel/1/6 would
			identify the 6th version of the object with id 1 in the Towel
			workspace. 
		"kb|ws.[ws_id].obj.[obj_id].ver.[obj_ver]" - for example, 
			"kb|ws.23.obj.567.ver.2" would identify the second version of an
			object with id 567 in a workspace with id 23.
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
	
	/* Meta data associated with an object stored in a workspace. Provided for
		backwards compatibility.
	
		obj_name id - name of the object.
		type_string type - type of the object.
		timestamp moddate - date when the object was saved
		obj_ver instance - the version of the object
		string command - Deprecated. Always returns the empty string.
		username lastmodifier - name of the user who last saved the object
		username owner - Deprecated. Same as lastmodifier.
		ws_name workspace - name of the workspace in which the object is
			stored
		string ref - Deprecated. Always returns the empty string.
		string chsum - the md5 checksum of the object.
		usermeta metadata - arbitrary user-supplied metadata about
			the object.
		obj_id objid - the numerical id of the object.
	*/
	typedef tuple<obj_name id, type_string type, timestamp moddate,
		int instance, string command, username lastmodifier, username owner,
		ws_name workspace, string ref ,string chsum, usermeta metadata,
		obj_id objid> object_metadata;
	
	/* Information about an object.
	
		obj_id objid - the numerical id of the object.
		obj_name name - the name of the object.
		type_string type - the type of the object.
		timestamp save_date - the save date of the object.
		obj_ver ver - the version of the object.
		username created_by - the user that created the object.
		ws_id wsid - the workspace containing the object.
		ws_name workspace - the workspace containing the object.
		string chsum - the md5 checksum of the object.
		int size - the size of the object in bytes.

	*/
	typedef tuple<obj_id objid, obj_name name, type_string type,
		timestamp save_date, int version, username created_by,
		ws_id wsid, ws_name workspace, string chsum, int size> object_info;
		
	/* Information about an object, including user provided metadata.
	
		obj_id objid - the numerical id of the object.
		obj_name name - the name of the object.
		type_string type - the type of the object.
		timestamp save_date - the save date of the object.
		obj_ver ver - the version of the object.
		username created_by - the user that created the object.
		ws_id wsid - the workspace containing the object.
		ws_name workspace - the workspace containing the object.
		string chsum - the md5 checksum of the object.
		int size - the size of the object in bytes.
		usermeta meta - arbitrary user-supplied metadata about
			the object.

	*/
	typedef tuple<obj_id objid, obj_name name, type_string type,
		timestamp save_date, int version, username created_by,
		ws_id wsid, ws_name workspace, string chsum, int size, usermeta meta>
		object_info_full;
	
	/* A provenance action.
	
		A provenance action is an action taken while transforming one data
		object to another. There may be several provenance actions taken in
		series. An action is typically running a script, running an api
		command, etc. All of the following are optional, but more information
		provided equates to better data provenance.
		
		resolved_ws_objects should never be set by the user; it is set by the
		workspace service when returning data.
		
		The maximum size of the entire provenance object, including all actions,
		is 1MB.
		
		timestamp time - the time the action was started.
		string service - the name of the service that performed this action.
		string service_ver - the version of the service that performed this action.
		string method - the method of the service that performed this action.
		list<UnspecifiedObject> method_params - the parameters of the method
			that performed this action. If an object in the parameters is a
			workspace object, also put the object reference in the
			input_ws_object list.
		string script - the name of the script that performed this action.
		string script_ver - the version of the script that performed this action.
		string script_command_line - the command line provided to the script
			that performed this action. If workspace objects were provided in
			the command line, also put the object reference in the
			input_ws_object list.
		list<obj_ref> input_ws_objects - the workspace objects that
			were used as input to this action; typically these will also be
			present as parts of the method_params or the script_command_line
			arguments.
		list<obj_ref> resolved_ws_objects - the workspace objects ids from 
			input_ws_objects resolved to permanent workspace object references
			by the workspace service.
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
		string description - a free text description of this action.
	*/
	typedef structure {
		timestamp time;
		string service;
		string service_ver;
		string method;
		list<UnspecifiedObject> method_params;
		string script;
		string script_ver;
		string script_command_line;
		list<obj_ref> input_ws_objects;
		list<obj_ref> resolved_ws_objects;
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
		(workspace_info info) authentication required;
	
	authentication optional;
	
	/* Input parameters for the "get_workspacemeta" function. Provided for
		backwards compatibility.
	
		One, and only one of:
		ws_name workspace - name of the workspace or the workspace ID in KBase
			format, e.g. kb|ws.78.
		ws_id id - the numerical ID of the workspace.
			
		Optional arguments:
		string auth - the authentication token of the KBase account accessing
			the list of workspaces. Overrides the client provided authorization
			credentials if they exist.
		
		@deprecated Workspace.WorkspaceIdentity
	*/
	typedef structure { 
		ws_name workspace;
		ws_id id;
		string auth;
	} get_workspacemeta_params;
	
	/*
		Retrieves the metadata associated with the specified workspace.
		Provided for backwards compatibility. 
		@deprecated Workspace.get_workspace_info
	*/
	funcdef get_workspacemeta(get_workspacemeta_params params) 
		returns(workspace_metadata metadata);
	
	/*
		Get information associated with a workspace.
	*/
	funcdef get_workspace_info(WorkspaceIdentity wsi)
		returns (workspace_info info);
	
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
	
		Required arguments:
		type_string type - the type of the object. Omit the version information
			to use the latest version.
		UnspecifiedObject data - the object data.
		
		Optional arguments:
		One of an object name or id. If no name or id is provided the name
			will be set to 'auto' with the object id appended as a string,
			possibly with -\d+ appended if that object id already exists as a
			name.
		obj_name name - the name of the object.
		obj_id objid - the id of the object to save over.
		usermeta meta - arbitrary user-supplied metadata for the object,
			not to exceed 16kb.
		list<ProvenanceAction> provenance - provenance data for the object.
		boolean hidden - true if this object should not be listed when listing
			workspace objects.
	
	*/
	typedef structure {
		type_string type;
		UnspecifiedObject data;
		obj_name name;
		obj_id objid;
		usermeta meta;
		list<ProvenanceAction> provenance;
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
		returns (list<object_info> info);
	
	authentication optional;
	
	/* The data and supplemental info for an object.
	
		UnspecifiedObject data - the object's data.
		object_info_full info - information about the object.
		list<ProvenanceAction> provenance - the object's provenance.
		
	*/
	typedef structure {
		UnspecifiedObject data;
		object_info_full info;
		list<ProvenanceAction> provenance;
	} ObjectData;
	
	/* 
		Get objects from the workspace.
	*/
	funcdef get_objects(list<ObjectIdentity> object_ids)
		returns (list<ObjectData> data);
		
	/* 
		Input parameters for the "list_workspaces" function. Provided for
		backwards compatibility.
		
		Optional parameters:
		string auth - the authentication token of the KBase account accessing
			the list of workspaces. Overrides the client provided authorization
			credentials if they exist.
		boolean excludeGlobal - if excludeGlobal is true exclude world
			readable workspaces. Defaults to false.
		
	*/
	typedef structure { 
		string auth;
		boolean excludeGlobal;
	} list_workspaces_params;
	
	/*
		Lists the metadata of all workspaces a user has access to. Provided for
		backwards compatibility - to be replaced by the functionality of
		list_workspace_info
	*/
	funcdef list_workspaces(list_workspaces_params params)
		returns (list<workspace_metadata> workspaces);
	
	/* 
		Input parameters for the "list_workspace_info" function.
		
		Optional parameters:
		boolean excludeGlobal - if excludeGlobal is true exclude world
			readable workspaces. Defaults to false.
		
	*/
	typedef structure { 
		boolean excludeGlobal;
	} ListWorkspaceInfoParams;
	
	/*
		Early version of list_workspaces with full API.
	 */
	funcdef list_workspace_info(ListWorkspaceInfoParams params)
		returns(list<workspace_info> wsinfo);
	
	/*
		Early version of list_objects.
	*/
	funcdef list_objects(WorkspaceIdentity wsi)
		returns(list<object_info> objinfo);
	
	/* Input parameters for the "get_objectmeta" function.
	
		Required arguments:
		ws_name workspace - name of the workspace containing the object for
			 which metadata is to be retrieved
		obj_name id - name of the object for which metadata is to be retrieved
		
		Optional arguments:
		int instance - Version of the object for which metadata is to be
			 retrieved, enabling retrieval of any previous version of an object
		string auth - the authentication token of the KBase account accessing
			the list of workspaces. Overrides the client provided authorization
			credentials if they exist.
			
		@deprecated Workspace.ObjectIdentity
	*/
	typedef structure { 
		obj_name id;
		ws_name workspace;
		int instance;
		string auth;
	} get_objectmeta_params;
	
	/*
		Retrieves the metadata for a specified object from the specified
		workspace. Provides access to metadata for all versions of the object
		via the instance parameter. Provided for backwards compatibility.
		
		@deprecated Workspace.get_object_info
	*/
	funcdef get_objectmeta(get_objectmeta_params params) 
		returns(object_metadata metadata) authentication optional; 
	
	/* 
		Get information about an object from the workspace.
	*/
	funcdef get_object_info(list<ObjectIdentity> object_ids)
		returns (list<object_info_full> info);
	
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

	/* A function string.
		Specifies the function and its version in a single string in the format
		[module].[funcname]-[major].[minor]:
		
		module - a string. The module name of the typespec containing the function.
		funcname - a string. The name of the function as assigned by the funcdef
			statement.
		major - an integer. The major version of the function. A change in the
			major version implies the function has changed in a non-backwards
			compatible way.
		minor - an integer. The minor version of the function. A change in the
			minor version implies that the function has changed in a way that is
			backwards compatible with previous function definitions.
		
		In many cases, the major and minor versions are optional, and if not
		provided the most recent version will be used.
		
		Example: MyModule.MyFunc-3.1
	*/
	typedef string func_string;
	
	/* The version of a typespec. */
	typedef int spec_version;
	
	/* The JSON Schema for a type. */
	typedef string jsonschema;
	
	authentication required;
	
	/* Request ownership of a module name. */
	funcdef request_module_ownership(modulename mod) returns();
	
	/* Parameters for the compile_typespec function.
	
		Required arguments:
		One of:
		typespec spec - the new typespec to compile.
		modulename mod - the module to recompile.
		
		Optional arguments:
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

	/* 
	Compile a copy of new typespec or recompile an existing typespec which is loaded 
	from another workspace for synchronization. Method returns new version of module 
	in current workspace. Also see the release_types function.
	*/
	funcdef compile_typespec_copy(string external_workspace_url, modulename mod, 
		spec_version version_in_external_workspace) returns(spec_version new_local_version);
		
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
	
		Optional arguments:
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
		 returns(ModuleVersions vers) authentication optional;
	
	/* Parameters for the get_module_info function.
	
		Required arguments:
		modulename mod - the name of the module to retrieve.
		
		Optional arguments:
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
		mapping<modulename, spec_version> included_spec_version - names of 
			included modules associated with their versions.
		string chsum - the md5 checksum of the object.
	*/
	typedef structure {
		list<username> owners;
		spec_version ver;
		typespec spec;
		string description;
		mapping<type_string, jsonschema> types;
		mapping<modulename, spec_version> included_spec_version;
		string chsum;
		list<func_string> functions;
	} ModuleInfo;
	
	funcdef get_module_info(GetModuleInfoParams params)
		returns(ModuleInfo info);
		
	/* Get JSON schema for a type. */
	funcdef get_jsonschema(type_string type) returns (jsonschema schema);

	/* Translation from types qualified with MD5 to their semantic versions */
	funcdef translate_from_MD5_types(list<type_string>) 
		returns(mapping<type_string, list<type_string>>);

	/* Translation from types qualified with semantic versions to their MD5'ed versions */
	funcdef translate_to_MD5_types(list<type_string>) 
		returns(mapping<type_string, type_string>);

	/* Information about type
	
		type_string type_def - resolved type definition id.
		string description - the description of the type from spec-file.
		string spec_def - reconstruction of type definition from spec-file.
		list<spec_version> module_vers - versions of spec-files containing
			given type version.
		list<type_string> type_vers - all versions of type with given type name.
		list<func_string> using_func_defs - list of functions (with versions)
			refering to this type version.
		list<type_string> using_type_defs - list of types (with versions)
			refereing to this type version.
		list<type_string> used_type_defs - list of types (with versions) 
			refered from this type version.
	*/
	typedef structure {
		type_string type_def;
		string description;
		string spec_def;
		list<spec_version> module_vers;
		list<type_string> type_vers;
		list<func_string> using_func_defs;
		list<type_string> using_type_defs;
		list<type_string> used_type_defs;
	} TypeInfo;
	
	funcdef get_type_info(type_string type) returns (TypeInfo);
	
	/* Information about function
	
		func_string func_def - resolved func definition id.
		string description - the description of the function from spec-file.
		string spec_def - reconstruction of function definition from spec-file.
		list<spec_version> module_vers - versions of spec-files containing
			given func version.
		list<func_string> func_vers - all versions of function with given type name.
		list<type_string> used_type_defs - list of types (with versions) 
			refered from this function version.
	*/
	typedef structure {
		func_string func_def;
		string description;
		string spec_def;
		list<spec_version> module_vers;
		list<func_string> func_vers;
		list<type_string> used_type_defs;
	} FuncInfo;
	
	funcdef get_func_info(func_string func) returns (FuncInfo);
		
	/* The administration interface. */
	funcdef administer(UnspecifiedObject command)
		returns(UnspecifiedObject response) authentication required;
};

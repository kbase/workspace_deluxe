Workspace service release notes
===============================

VERSION: 0.8.0 (Released TBD)
--------------------------------

BACKWARDS INCOMPATIBILITIES:

* The ``undelete_workspace`` method has been removed. Workspaces are now considered to be
  permanently deleted.
* Building and running the service now requires Java 8.
* The ``getPermissions`` administration command, like the ``get_permissions`` method, is now
  deprecated.
  
ADMIN NOTES:

* Two new indexes have been added to the workspace versions mongo collection:
    * the index ``{savedby: 1}`` with no options
    * the index ``{ws: 1, id: 1, ver: -1}`` with ``{unique: 1}``
* The workspaces collection name index has been made sparse. The index must be changed before
  deploying this version.
* Added docker file & code for pushing docker image in a travis build.

NEW FEATURES:

* Adds a workspace event listener API. Event listeners must implement the
  ``us.kbase.workspace.listener.WorkspaceEventListenerFactory`` and ``WorkspaceEventListener``
  interfaces. Specify listeners to be loaded on start up in the ``deploy.cfg`` file (see
  ``deploy.cfg.example`` for an example). See
  ``us.kbase.workspace.test.listener.NullListenerFactory`` for an example implementation.
* Added the ``list_workspace_ids`` method.
* Added the ``listWorkspaceIDs`` administration command.
* Added the ``getPermissionsMass`` administration command.
* Added the ``getWorkspaceInfo`` administration command.
* Added the ``listObjects`` administration command.
* Added the ``getObjectInfo`` administration command.
* Added the ``getObjectHistory`` administration command.
* Added the ``getObjects`` administration command.
* ``list_objects`` will now sort the output if no filters other than the object id filters are
  applied. The sort order is workspace id ascending, object id ascending, and version descending.

UPDATED FEATURES / MAJOR BUG FIXES:

* A user name is now optional for the ``getPermissions`` administration command.
* Fixed a bug where the administrator ``setWorkspaceOwner`` command in very specific
  cases could allow setting an illegal workspace name.
* Fixed a bug where an admin could delete a locked workspace.
* Removed ``kbase-admin`` credentials from the ``deploy.cfg`` file as they're obsolete after the
  conversion to auth2.
* The credentials for the Handle Manager service in the ``deploy.cfg`` file now require a token.
* The credentials for the file backend in the ``deploy.cfg`` file now require a token.
* Fixed a bug where performing a permissions search for a readable, deleted object with an
  incoming reference from a readable, non-deleted object would fail with a deleted object
  exception.
* Fixed a bug that could cause workspace clones to fail under certain conditions.

VERSION: 0.7.1 (Released 6/22/17)
---------------------------------

UPDATED FEATURES / MAJOR BUG FIXES:

* Updated the auth client to version 0.4.4 to fix the NoSuchMethod error.

VERSION: 0.7.0 (Released 5/5/17)
--------------------------------

BACKWARDS INCOMPATIBILITIES:

* It is now required to provide either an object name or an object id when saving an object.

NEW FEATURES:

* ``deleteWorkspace`` and ``undeleteWorkspace`` commands have been added to the administration
  interface.

UPDATED FEATURES / MAJOR BUG FIXES:

* When attempting to save an object with metadata containing a null key or value a more
  illuminating error is thrown.
* The administration script now uses the authentication service url set in the deploy.cfg file as
  opposed to a hard coded url.

VERSION: 0.6.0 (Released 12/9/16)
---------------------------------

BACKWARDS INCOMPATIBILITIES:

* The ``kb|ws...`` style of addressing workspaces or objects has been removed.
* A bug allowed workspace names of the form user:X where X is an integer > ~2^32. This style of
  name is temporarily allowed for backwards compatibility reasons but is deprecated and will be
  removed in a future release.

NEW FEATURES:

* The ``ObjectSpecification`` structure now provides a ``find_reference_path``
  field that allows specifying that the permissions for an object should be
  automatically looked up via a search through the object reference graph.
* The resolved (e.g. all references are absolute) path through the object reference graph from an
  accessible object to the target object is now returned with ``get_objects2`` and the new method
  ``get_object_info3``.
* Added a new method, ``get_object_info3`` that returns the path from an accessible object to the
  target object, but is otherwise equivalent to ``get_object_info_new``. ``get_object_info_new``
  is now deprecated.
* Objects containing a semicolon separated reference path rather than just embedded references
  can now be saved. If the reference path is valid and the head of the path accessible, the
  references will be rewritten to the absolute reference of the object at the end of the path.
* Similarly, provenance references can now contain reference paths rather than just single
  references.

UPDATED FEATURES / MAJOR BUG FIXES:

* The ``ObjectSpecification`` structure now allows several new ways to provide
  reference paths into the object graph.
* Fixed a bug where integers > ~2^32 were allowed as workspace and object names.
* Fixed a bug in ``register_typespec_copy`` where any types in common between the new and previous
  version of the spec would be unregistered.

VERSION: 0.5.0 (Released 8/12/16)
---------------------------------

BACKWARDS INCOMPATIBILITIES:

* The ``skip`` parameter of ``list_objects`` has been removed.
* In order to save an object that contains handles to shock nodes, the user
  must own the shock nodes. Previously, the user only needed read permissions.
* Handle Service version b9de6991b851e9cd8fa9b5012db565f051e0894f+ is now
  required.
* Handle Manager version 3e60998fc22bb331e51b189ae1b71ebd54e58b90+ is now
  required.
* Shock version 0.9.6+ is now required.

NEW FEATURES:

* The ``status`` method now returns JVM memory stats and the status of MongoDB,
  Shock, and the Handle service and manager (if using the latter three).

UPDATED FEATURES / MAJOR BUG FIXES:

* ``clone_workspace`` now preserves object IDs from the source workspace such
  that the object name -> id mapping is identical for both workspaces at the
  completion of the clone (unless changes are made to the source workspace
  while the clone is in progress). Due to this change, the maximum object ID
  returned in a ``workspace_info`` tuple may be larger than the number of
  objects in the new workspace. The documentation has been clarified to
  reflect this.
* ``clone_workspace`` now prevents the new workspace from being accessed in any
  way while the clone is in progress.
* ``clone_workspace`` can now exclude user specified objects from the clone.
* Fixed several bugs where various failures could leave temporary files on
  disk.
* Fixed a bug where accessing an object with handles to shock nodes
  anonymously would cause a null pointer error.
* A temporary file is created and deleted at startup to ensure the temporary
  files directory is readable.
* Fixed a bug where under certain circumstances more data than allowed could be
  stored in memory or on disk and returned in a get_objects call.
* The authorization URLs used by the server may now be configured.
* All configuration user id / password combinations may now be alternately
  fulfilled with a token.
* The initialization script now takes a token rather than a user id and
  password for the shock user account.

VERSION: 0.4.1 (Released 5/27/16)
---------------------------------

BACKWARDS INCOMPATIBILIES:

* Java users will need to switch from the ``ObjectIdentity`` to the
  ``ObjectSpecification`` class when calling ``getObjectInfoNew``. The
  interface is a superset of ``ObjectIdentity`` and so is a simple name swap.
* The text of some error messages has changed.

NEW FEATURES:

* Added the ``get_objects2`` method. This method combines the functionality of
  ``get_objects``, ``get_object_provenance``, ``get_object_subset``, and
  ``get_referenced_objects`` and as such those methods are deprecated. In
  particular, a user can now get a subset from a referenced object or get only
  the provenance from a referenced object. ``get_objects2`` also allows for
  returning nulls instead of throwing an error when an object is inaccessible
  in the same way as ``get_object_info_new``.

UPDATED FEATURES / MAJOR BUG FIXES:

* ``get_object_info_new`` can now follow object references like
  ``get_objects2`` and ``get_referenced_objects``.
* Fixed an exploit where an attacker, for an arbitrary workspace, could
  determine the number of objects in that workspace, the number of versions of
  each object, and whether a particular object name exists in the workspace.
* Added the ``custom``, ``subactions``, and ``caller`` fields to
  ``ProvenanceAction``.
* Added original workspace ID to the data returned by ``get_objects*`` methods.
* Unix epoch times are now accepted and emitted where possible (e.g. not in 
  tuples) as well as string timestamps.
* ``list_referencing_object_counts`` has been deprecated.

VERSION: 0.4.0 (Released 2/2/16)
--------------------------------

BACKWARDS INCOMPATIBILITIES:

* the ``list_objects()`` ``skip`` parameter is now deprecated and will be
  removed in a future version. Additionally, the ``list_objects`` method's
  behavior has changed. ``list_objects`` is now guaranteed to return either all
  the remaining objects that match the filters or ``limit`` objects. ``skip``
  now behaves in an unintuitive way in that the same object may appear in
  ``list_objects`` results even when the ``skip`` parameter setting should
  ensure that each set of returned objects is disjoint with all the others. 
* Module names and type names are now limited to 255 bytes.
* Metadata keys and values are limited to 900B for the total of each pair
  of key and value.

NEW FEATURES:

* Added ``get_permissions_mass`` function.
* Added ``get_names_by_prefix`` function.
* A documentation server now provides all available workspace documentation at
  the ``/docs`` endpoint.
* ``list_objects`` output may now be filtered by minimum and maximum object
  IDs.

UPDATED FEATURES / MAJOR BUG FIXES:

* Updated for compatibility with Shock 0.9.6 (tests only), 0.9.12, and 0.9.13.
* Removed internal data subsetting (intended for indexing of data contents)
  code. No plan to use this code and drastically increases database size and
  codebase complexity. All workspace mongo database ``type_[MD5]`` collections
  may be deleted after upgrading.
* Improved logging for the ``administer()`` method.
* Fixed a bug where mongo connections would not be released when redeploying
  the server in an already running glassfish instance.
* Fixed a bug where objects from deleted workspaces could be listed in 
  ``list_objects`` output.
* ``get_permissions`` no longer requires authentication.
* the admin user specified in the ``deploy.cfg`` file can no longer be removed
  by other admins.

VERSION: 0.3.5 (Released 5/15/15)
---------------------------------
BUG FIXES:

* Updated auth library dependency that prevented validating user names
  not in the KBase group, which was preventing sharing with a subset
  of real and active KBase users.

VERSION: 0.3.4 (Released 4/10/15)
---------------------------------
NEW FEATURES:

* Added CLI command for listing properly configured Narratives

UPDATED FEATURES / MAJOR BUG FIXES:

* Updated to the new auth client. Globus APIs changed in a way that broke
  sharing with multiple users at the same time.
* Added required fields to the deploy.cfg file for user credentials to use
  when querying user data. These creds must be for an administrator of
  kbase_users so that all users are visible to the workspace service when
  attempting to share workspaces.
* Empty strings are now accepted as map keys
* Fixed a NPE when calling list_referencing_object_counts with a non-existent
  object version
* Fixed a race condition that could occur when operating on an object that's in
  mid save
* 'strict_maps' and 'strict_arrays' properties are now present in 
  'get_object_subset' method
* Slashes are now supported in paths used in 'get_object_subset' method

VERSION: 0.3.3 (Released 10/28/14)
----------------------------------
NEW FEATURES:

* Object references and types are now logged for many methods.

VERSION: 0.3.2 (Released 10/20/14)
----------------------------------

UPDATED FEATURES / MAJOR BUG FIXES:

* The ProvenanceAction data structure now has fields for entering external
  data sources.
* The workspace client now has streaming mode off by default. To turn it back
  on, do setStreamingModeOn(true).
* Fixed a bug that would cause calls to the handle service or handle manager
  to fail every other call if they were not behind nginx and the call 
  frequency was between 1-4s.
  
VERSION: 0.3.1 (Released 10/1/2014)
-----------------------------------

UPDATED FEATURES / MAJOR BUG FIXES:

* Fixed a bug where adding an @id annotation to the key of a mapping would
  result in a minor version increment vs. the expected major version increment.
* Fixed a bug where a bad workspace @id (unparsable, deleted object, etc) with
  allowed types specified in the typespec would cause a NPE rather than a
  useful typechecking error.

VERSION: 0.3.0 (Released 9/2/2014)
----------------------------------
NEW FEATURES:

* The major change in this release is a major refactoring of the ID handling
  system. ID handling has been generalized to allow for custom ID handlers per
  ID type (e.g. the @id [ID_type] annotation).
* The workspace now supports the @id handle annotation, which allows for
  embedding HandleService handle IDs in workspace objects. When the object
  is retrieved from the workspace, the user retrieving the object is given
  read access to any data referenced by handles in the object.
* There is now a limit of 100,000 IDs in objects per save_objects call.
  IDs duplicated in the same object do not count towards this limit.
* Any IDs extracted from an object are returned in get_objects,
  get_referenced_objects, get_object_subset, and get_object_provenance.
* The source of a copied object, if visible to the user, is now exposed in the
  various get_objects* methods.
* New command line scripts added: ws-diff to compare (client side) two
  workspace objects and ws-typespec-download to automatically download
  registered typespecs and automatically resolve dependencies.
* Support added for the @metadata ws annotation to automatically extract
  ws metadata from the object data.  String/float/int fields in objects
  or subobjects can be selected in addition to the length of lists and
  mappings.
* Support for @range annotation to set limits (inclusive or exclusive)
  on int and float values.

UPDATED FEATURES / MAJOR BUG FIXES:

* Users with write permissions to a workspace can now view permissions for
  all users to that workspace.
* X-Forwarded-For and X-Real-IP headers are now taken into account when
  logging the IP of method calls. Set dont_trust_x_ip_headers=true in
  deploy.cfg to ignore them.
* Updated timestamp format in ws-list and ws-listobj to display readable
  local time by default instead of the ISO timestamp.
* get_object_subset no longer generates an error if a selected field
  or mapping key is not found, which provides better support for optional
  fields.  Errors are still generated if an array element does not exist.

VERSION: 0.2.1 (Released 7/11/14)
---------------------------------
NEW FEATURES:

* get_object_provenance returns the object provenance without the data.
* added get_all_type_info and get_all_func_info to return all type/function
  information registered for a specified module
* a parsed structure of type and function defintions were added to TypeInfo
  and FuncInfo
* the owner of a module now can determine the released versions of a types and 
  and functions (released version info was added to TypeInfo and FuncInfo)
* Java client now has a method to deactivate SSL certification validation
  (primarily for use with self-signed certs)

UPDATED FEATURES / MAJOR BUG FIXES:

* the initialization script will no longer allow setting the mongo typedb
  name to the workspace type db name, and the server will refuse to start up
  if such is the case.
* configuration of the default URL for the CLI is handled properly; in 0.2.0
  the ws-url command needed to be called prior to other commands
* improved documentation and other minor error handling in the CLI
* again allows IRIS deployment of ws-workspace and ws-url
* fixed a bug that could cause date parsing errors on valid incoming
  date strings
* date strings now may contain 'Z' for the timezone
* kbase user is now configurable for deploy-upstart target
* there is now an option in deploy.cfg to specify the number of times to
  attempt to contact MongoDB on startup

VERSION: 0.2.0 (Released 5/18/14)
---------------------------------
PREAMBLE:

v0.2.0 is a complete rewrite of the data path through the workspace, including
type checking, sorting, data extraction, and object retrieval, for the
purpose of controlling memory usage.

BACKWARDS INCOMPATIBLITIES:

* deploy.cfg has several new parameters, most of which have acceptable
  defaults. However temp-dir needs to be set before starting the new version.

NEW FEATURES:

* a new function, list_all_types, returns all the types in the workspace.
* ScriptHelpers workspace library ported to python (from perl) by Mike Mundy.

UPDATED FEATURES / MAJOR BUG FIXES:

* The max object size has been returned to 1GB.
* start_service no longer requires user-env.sh to be sourced.
* Nulls will now pass type checking where an int, float, or string is expected.
* Fixed a bug where get_object_subdata would return the same subdata if two
  different paths through the same object were specified.
* Command-line interface default URLs are configurable via the makefile.
* ws-workspace and ws-url now work against the User and Job State Service when
  in IRIS.
* The characters . and - are now allowed in workspace names.
* Parallel GC has been re-enabled.
* Updating a searchable ws or id annotation in a type definition now results
  in a major version increment instead of a minor version increment.
* Fixed a bug where get_referencing_objects would throw an error if an object
  has no references.

VERSION: 0.1.6 (Released 3/3/14)
--------------------------------

NEW FEATURES:

* Get objects by reference, which allows retrieval of any objects that
  are referenced by objects to which the user has access.
* A new version of get_object_info, get_object_info_new, allows ignoring errors
  when listing object information. get_object_info is deprecated in favor of
  this method.
* Get the number of objects that reference an object via provenance or object-
  to-object references, including inaccessible objects.

UPDATED FEATURES / MAJOR BUG FIXES:

* Filter list_objects and list_workspace_info by date
* Optionally exclude globally readable objects from list_objects
* list_objects now takes skip and limit parameters and returns at most
  10000 objects. list_workspace_objects returns at most 10000 objects.
* A user can reduce their own permissions on any workspace.
* Workspace and object names can now be up to 255 characters in length.
* Workspace mod dates are now updated on a save/copy/revert/delete/rename
  of an object.
* Fixed a bug that caused object checksums to be calculated incorrectly. Note
  that any checksums calculated before this version are incorrect.
* Fixed a bug where trying to copy an object to an object with a version
  > than the maximum existing version would fail. The incoming copy target
  version number should be ignored.
* Fixed a bug where trying to copy an object to a deleted object would fail.
* Clarified some exceptions / error messages.

VERSION: 0.1.5 (Released 2/5/14)
--------------------------------

Hotfix to use updated auth libs with 60d token lifetime.

VERSION: 0.1.4 (Released 1/30/14)
---------------------------------

NEW FEATURES:

* Get the version of the workspace server.
* Set metadata on a workspace and search workspaces by metadata.

UPDATED FEATURES / MAJOR BUG FIXES:

* On startup the WSS attempts to create a node in shock to test for shock
  misconfiguration (shock client change)

VERSION: 0.1.3 (Released 1/24/14)
---------------------------------

UPDATED FEATURES / MAJOR BUG FIXES:

* Fixed a bug where get_module_info and get_type_info reported removed types.
* Scripts now allow IDs or object references to be used in place of object
  and workspace names.

VERSION: 0.1.2 (Released 1/23/14)
---------------------------------

Hotfix release to disallow integer object and workspace names.

VERSION: 0.1.1 (Released 1/21/14)
---------------------------------

BACKWARDS INCOMPATIBILITIES:

* The maximum object size is temporarily limited to 200MB.
* The maximum JSON string size received by the server is temporarily limited
  to 250MB.

NEW FEATURES:

* Add owners to modules so that multiple users can upload typespecs.
* Option to list only deleted objects or workspaces.
* Filter objects or workspaces list by permission level.
* Filter workspaces list by owner.
* Filter object list by the person who saved the object.
* Filter object list by user metadata.
* Return a list of objects that reference another object, either in the object
  data or the provenance data.

UPDATED FEATURES / MAJOR BUG FIXES:

* Module owners can now see unreleased modules and types.
* Turned off parallel garbage collection - was locking the server when
  processing large objects.
* Fixed bug in WS ID relabeling in values of mappings when keys contain forward
  slash character
* Retrieving subset of an object that includes an array element out of the
  array index range now generates an error instead of returning a subset with
  null values in the array
* First error encountered during type checking halts type checking, meaning
  that only the first error is shown to you even if multiple errors exist

VERSION: 0.1.0 (Released 1/9/2014)
----------------------------------
PREAMBLE:

0.1.0 is a complete rewrite of the workspace service and thus has many changes
to the API. A function change list is below.

NEW FEATURES:

* The WSS is configurable to save TOs in MongoDB/GridFS or Shock.
* Load, compile, and view KIDL typespecs.
* Objects are type checked against a KIDL typespec before saving.
* Save provenance information with an object.
* References to other workspace objects in a TO or TO provenance
  are confirmed accessible and type checked before saving.
* A list of references from a TO or TO provenance to other workspace objects is
  saved and retrievable.
* Hide objects. Hidden objects, by default, do not appear in the list_*
  methods.
* Lock a workspace, freezing it permanently. Locked, publicly readable
  workspaces are published.
* Workspaces and objects have a permanent autoincrementing ID as well as a
  mutable name. An object may be addressed by any combination of the
  workspace and object name or id plus a version number, or the KBase ID
  kb|ws.[workspace id].obj.[object id].ver.[object version].
* Workspaces may have a <1000 character description.
* Workspace names may be prefixed by the user's username and a colon. This
  provides a unique per user namespace for workspace names.
* Return only a user specified subset of an object.

UPDATED FEATURES / MAJOR BUG FIXES:

* Many methods now operate on multiple objects rather than one object per
  method call.
* list_objects can list objects from multiple workspaces at once.
* Rename an object or workspace.

FUNCTION CHANGE LIST:

**Deprecated functions, and their replacement**

| get_workspacemeta -> get_workspace_info
| get_objectmeta -> get_object_info
| save_object -> save_objects
| get_object -> get_objects
| list_workspaces -> list_workspace_info
| list_workspace_objects -> list_objects

**Functions with an altered api. Please see the API documentation for details**

| create_workspace
| clone_workspace
| get_objects
| copy_object
| revert_object
| object_history -> get_object_history
| set_global_workspace_permissions -> set_global_permission
| set_workspace_permissions -> set_permissions
| get_workspacepermissions -> get_permissions
| delete_workspace -> delete_workspace and undelete_workspace
| delete_object -> delete_objects and undelete_objects

**Removed functions**

| move_object -> use rename_object or copy_object and delete_objects
| has_object -> use get_object_info
| delete_object_permanently
| add_type -> various new functions below
| get_types -> various new functions below
| remove_type
| load_media_from_bio
| import_bio
| import_map
| queue_job -> AWE and / or the UserJobStateService
| set_job_status -> AWE and / or the UserJobStateService
| get_jobs -> AWE and / or the UserJobStateService
| get_object_by_ref
| save_object_by_ref
| get_objectmeta_by_ref
| get_user_settings -> UserJobStateService
| set_user_settings -> UserJobStateService

**New functions**

| get_object_subset
| get_workspace_description
| set_workspace_description
| lock_workspace
| rename_workspace
| rename_object
| hide_objects
| unhide_objects
| request_module_ownership
| register_typespec
| register_typespec_copy
| release_module
| list_modules
| list_module_versions
| get_module_info
| get_jsonschema
| translate_from_MD5_types
| translate_to_MD5_types
| get_type_info
| get_func_info
| administer

VERSION: 0.0.5 (Released 11/19/2013)
------------------------------------
NEW FEATURES:

* Type compiler provided embedded authorization works
* Connect to mongodb databases requiring authorization
* Optionally exclude world readable workspaces from the output of
  list_workspaces()

UPDATED FEATURES / MAJOR BUG FIXES:

* Authentication is required for all writes, including workspace creation. The
  'public' user is now no different from any other user
* Workspace default permissions are now limited to none and read only
* A user must have at least read access to a workspace to get its metadata
* Only the user's own permission level is now returned by 
  get_workspacepermissions() if a user has read or write access to a workspace 
* Only the workspace's owner can change the owner's permissions
* Type names are now limited to ascii alphanumeric characters and _
* Object names are now limited to ascii alphanumeric characters and .|_-
* Object names must now be unique per workspace, even if the objects are
  different types
* Object and workspace names may not be integers
* Removed one of the two python clients in lib/, as it was not being updated on
  a make while the other was

VERSION: 0.0.4 (Released 8/13/2013)
-----------------------------------
NEW FEATURES:

* Connect to mongodb databases requiring authorization
* get_objects() method

VERSION: 0.0.3 (Released 1/1/2012)
----------------------------------
NEW FEATURES:

* Added functions to manage the addition and removal of types.
* Added functions to handle job management to support running jobs on local
  clusters
* Added "instance" argument to "get_object" to enable users to access all
  object instances
* Created a complete set of command line scripts for interacting with workspace

UPDATED FEATURES / MAJOR BUG FIXES:

* Added ability to retrieve specific instances of objects
* Fixed bug in deletion of workspaces
* Fixed bug in object reversion
* Fixed bug in object retrieval
* Fixed bug in management of persistant state in workspace

VERSION: 0.0.2 (Released 11/30/2012)
------------------------------------
NEW FEATURES:

* This is the first public release of the Workspace Services.
* adjusted functions to accept arguments as a hash instead of an array
* added ability to provide authentication token in input arguments

VERSION: 0.0.1 (Released 10/12/2012)
------------------------------------
NEW FEATURES:

* This is the first internal release of the Workspace Service, all methods are
  new.

/*

The Workspace Service (WSS) is primarily a language independent remote storage
and retrieval system for KBase typed objects (TO) defined with the KBase
Interface Description Language (KIDL). It has the following primary features:
- Immutable storage of TOs with
	- user defined metadata
	- data provenance
- Versioning of TOs
- Referencing from TO to TO
- Typechecking of all saved objects against a KIDL specification
- Collecting typed objects into a workspace
- Sharing workspaces with specific KBase users or the world
- Freezing and publishing workspaces

*/
module Workspace {

	/* A boolean. 0 = false, other = true. */
	typedef int boolean;

	/* The unique, permanent numerical ID of a workspace. */
	typedef int ws_id;

	/* A string used as a name for a workspace.
		Any string consisting of alphanumeric characters and "_", ".", or "-"
		that is not an integer is acceptable. The name may optionally be
		prefixed with the workspace owner's user name and a colon, e.g.
		kbasetest:my_workspace.
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
		A time in the format YYYY-MM-DDThh:mm:ssZ, where Z is either the
		character Z (representing the UTC timezone) or the difference
		in time to UTC in the format +/-HHMM, eg:
			2012-12-17T23:24:06-0500 (EST time)
			2013-04-03T08:56:32+0000 (UTC time)
			2013-04-03T08:56:32Z (UTC time)
	*/
	typedef string timestamp;

	/* A Unix epoch (the time since 00:00:00 1/1/1970 UTC) in milliseconds. */
	typedef int epoch;

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

	/* An id type (e.g. from a typespec @id annotation: @id [idtype]) */
	typedef string id_type;

	/* An id extracted from an object. */
	typedef string extracted_id;

	/* User provided metadata about an object.
		Arbitrary key-value pairs provided by the user.
	*/
	typedef mapping<string, string> usermeta;

	/* The lock status of a workspace.
		One of 'unlocked', 'locked', or 'published'.
	*/
	typedef string lock_status;

	/* A workspace identifier.

		Select a workspace by one, and only one, of the numerical id or name.
		ws_id id - the numerical ID of the workspace.
		ws_name workspace - the name of the workspace.

	*/
	typedef structure {
		ws_name workspace;
		ws_id id;
	} WorkspaceIdentity;

	/* Meta data associated with a workspace. Provided for backwards
		compatibility. To be replaced by workspace_info.

		ws_name id - name of the workspace
		username owner - name of the user who owns (who created) this workspace
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
		int max_objid - the maximum object ID appearing in this workspace.
			Since cloning a workspace preserves object IDs, this number may be
			greater than the number of objects in a newly cloned workspace.
		permission user_permission - permissions for the authenticated user of
			this workspace.
		permission globalread - whether this workspace is globally readable.
		lock_status lockstat - the status of the workspace lock.
		usermeta metadata - arbitrary user-supplied metadata about
			the workspace.

	*/
	typedef tuple<ws_id id, ws_name workspace, username owner, timestamp moddate,
		int max_objid, permission user_permission, permission globalread,
		lock_status lockstat, usermeta metadata> workspace_info;

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

		The format is [ws_name or id]/[obj_name or id]/[obj_ver].
		For example, MyFirstWorkspace/MyFirstObject/3 would identify the third version
		of an object called MyFirstObject in the workspace called
		MyFirstWorkspace. 42/Panic/1 would identify the first version of
		the object name Panic in workspace with id 42. Towel/1/6 would
		identify the 6th version of the object with id 1 in the Towel
		workspace.If the version number is omitted, the latest version of
		the object is assumed.
	*/
	typedef string obj_ref;

	/* An object identifier.

		Select an object by either:
			One, and only one, of the numerical id or name of the workspace.
				ws_id wsid - the numerical ID of the workspace.
				ws_name workspace - the name of the workspace.
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

	/* A chain of objects with references to one another.

		An object reference chain consists of a list of objects where the nth
		object possesses a reference, either in the object itself or in the
		object provenance, to the n+1th object.
	*/
	typedef list<ObjectIdentity> ref_chain;

	/* A chain of objects with references to one another as a string.

		A single string that is semantically identical to ref_chain above.
		Represents a path from one workspace object to another through an
		arbitrarily number of intermediate objects where each object has a
		dependency or provenance reference to the next object. Each entry is
		an obj_ref as defined earlier. Entries are separated by semicolons.
		Whitespace is ignored.

		Examples:
		3/5/6; kbaseuser:myworkspace/myobject; 5/myobject/2
		aworkspace/6
	*/
	typedef string ref_string;

	/* A path into an object.
		Identify a sub portion of an object by providing the path, delimited by
		a slash (/), to that portion of the object. Thus the path may not have
		slashes in the structure or mapping keys. Examples:
		/foo/bar/3 - specifies the bar key of the foo mapping and the 3rd
			entry of the array if bar maps to an array or the value mapped to
			the string "3" if bar maps to a map.
		/foo/bar/[*]/baz - specifies the baz field of all the objects in the
			list mapped by the bar key in the map foo.
		/foo/asterisk/baz - specifies the baz field of all the objects in the
			values of the foo mapping. Swap 'asterisk' for * in the path.
		In case you need to use '/' or '~' in path items use JSON Pointer
			notation defined here: http://tools.ietf.org/html/rfc6901
	*/
	typedef string object_path;

	/*  DEPRECATED

		An object subset identifier.

		Select a subset of an object by:
		EITHER
			One, and only one, of the numerical id or name of the workspace.
				ws_id wsid - the numerical ID of the workspace.
				ws_name workspace - name of the workspace.
			AND
			One, and only one, of the numerical id or name of the object.
				obj_id objid- the numerical ID of the object.
				obj_name name - name of the object.
			OPTIONALLY
				obj_ver ver - the version of the object.
		OR an object reference string:
			obj_ref ref - an object reference string.
		AND a subset specification:
			list<object_path> included - the portions of the object to include
				in the object subset.
		boolean strict_maps - if true, throw an exception if the subset
			specification traverses a non-existant map key (default false)
		boolean strict_arrays - if true, throw an exception if the subset
			specification exceeds the size of an array (default true)

		@deprecated Workspace.ObjectSpecification
	*/
	typedef structure {
		ws_name workspace;
		ws_id wsid;
		obj_name name;
		obj_id objid;
		obj_ver ver;
		obj_ref ref;
		list<object_path> included;
		boolean strict_maps;
		boolean strict_arrays;
	} SubObjectIdentity;

	/* An Object Specification (OS). Inherits from ObjectIdentity (OI).
		Specifies which object, and which parts of that object, to retrieve
		from the Workspace Service.

		The fields wsid, workspace, objid, name, and ver are identical to
		the OI fields.

		The ref field's behavior is extended from OI. It maintains its
		previous behavior, but now also can act as a reference string. See
		reference following below for more information.

		REFERENCE FOLLOWING:

		Reference following guarantees that a user that has access to an
		object can always see a) objects that are referenced inside the object
		and b) objects that are referenced in the object's provenance. This
		ensures that the user has visibility into the entire provenance of the
		object and the object's object dependencies (e.g. references).

		The user must have at least read access to the object specified in this
		SO, but need not have access to any further objects in the reference
		chain, and those objects may be deleted.

		Optional reference following fields:
		Note that only one of the following fields may be specified.

		ref_chain obj_path - a path to the desired object from the object
			specified in this OS. In other words, the object specified in this
			OS is assumed to be accessible to the user, and the objects in
			the object path represent a chain of references to the desired
			object at the end of the object path. If the references are all
			valid, the desired object will be returned.
		- OR -
		list<obj_ref> obj_ref_path - shorthand for the obj_path.
		- OR -
		ref_chain to_obj_path - identical to obj_path, except that the path
			is TO the object specified in this OS, rather than from the object.
			In other words the object specified by wsid/objid/ref etc. is the
			end of the path, and to_obj_path is the rest of the path. The user
			must have access to the first object in the to_obj_path.
		- OR -
		list<obj_ref> to_obj_ref_path - shorthand for the to_obj_path.
		- OR -
		ref_string ref - A string representing a reference path from
			one object to another. Unlike the previous reference following
			options, the ref_string represents the ENTIRE path from the source
			object to the target object. As with the OI object, the ref field
			may contain a single reference.
		- OR -
		boolean find_refence_path - This is the last, slowest, and most expensive resort
			for getting a referenced object - do not use this method unless the
			path to the object is unavailable by any other means. Setting the
			find_refence_path parameter to true means that the workspace service will
			search through the object reference graph from the object specified
			in this OS to find an object that 1) the user can access, and 2)
			has an unbroken reference path to the target object. If the search
			succeeds, the object will be returned as normal. Note that the search
			will automatically fail after a certain (but much larger than necessary
			for the vast majority of cases) number of objects are traversed.


		OBJECT SUBSETS:

		When selecting a subset of an array in an object, the returned
		array is compressed to the size of the subset, but the ordering of
		the array is maintained. For example, if the array stored at the
		'feature' key of a Genome object has 4000 entries, and the object paths
		provided are:
			/feature/7
			/feature/3015
			/feature/700
		The returned feature array will be of length three and the entries will
		consist, in order, of the 7th, 700th, and 3015th entries of the
		original array.

		Optional object subset fields:
		list<object_path> included - the portions of the object to include
				in the object subset.
		boolean strict_maps - if true, throw an exception if the subset
			specification traverses a non-existent map key (default false)
		boolean strict_arrays - if true, throw an exception if the subset
			specification exceeds the size of an array (default true)
	*/
	typedef structure {
		ws_name workspace;
		ws_id wsid;
		obj_name name;
		obj_id objid;
		obj_ver ver;
		ref_string ref;
		ref_chain obj_path;
		list<obj_ref> obj_ref_path;
		ref_chain to_obj_path;
		list<obj_ref> to_obj_ref_path;
		boolean find_reference_path;
		list<object_path> included;
		boolean strict_maps;
		boolean strict_arrays;
	} ObjectSpecification;

	/* Meta data associated with an object stored in a workspace. Provided for
		backwards compatibility.

		obj_name id - name of the object.
		type_string type - type of the object.
		timestamp moddate - date when the object was saved
		obj_ver instance - the version of the object
		string command - Deprecated. Always returns the empty string.
		username lastmodifier - name of the user who last saved the object,
			including copying the object
		username owner - Deprecated. Same as lastmodifier.
		ws_name workspace - name of the workspace in which the object is
			stored
		string ref - Deprecated. Always returns the empty string.
		string chsum - the md5 checksum of the object.
		usermeta metadata - arbitrary user-supplied metadata about
			the object.
		obj_id objid - the numerical id of the object.

		@deprecated object_info
	*/
	typedef tuple<obj_name id, type_string type, timestamp moddate,
		int instance, string command, username lastmodifier, username owner,
		ws_name workspace, string ref, string chsum, usermeta metadata,
		obj_id objid> object_metadata;

	/* Information about an object, including user provided metadata.

		obj_id objid - the numerical id of the object.
		obj_name name - the name of the object.
		type_string type - the type of the object.
		timestamp save_date - the save date of the object.
		obj_ver ver - the version of the object.
		username saved_by - the user that saved or copied the object.
		ws_id wsid - the workspace containing the object.
		ws_name workspace - the workspace containing the object.
		string chsum - the md5 checksum of the object.
		int size - the size of the object in bytes.
		usermeta meta - arbitrary user-supplied metadata about
			the object.

	*/
	typedef tuple<obj_id objid, obj_name name, type_string type,
		timestamp save_date, int version, username saved_by,
		ws_id wsid, ws_name workspace, string chsum, int size, usermeta meta>
		object_info;

	/* An external data unit. A piece of data from a source outside the
		Workspace.

		On input, only one of the resource_release_date or
		resource_release_epoch may be supplied. Both are supplied on output.

		All fields are optional, but at least one field must be present.

		string resource_name - the name of the resource, for example JGI.
		string resource_url - the url of the resource, for example
			http://genome.jgi.doe.gov
		string resource_version - version of the resource
		timestamp resource_release_date - the release date of the resource
		epoch resource_release_epoch - the release date of the resource
		string data_url - the url of the data, for example
			http://genome.jgi.doe.gov/pages/dynamicOrganismDownload.jsf?
				organism=BlaspURHD0036
		string data_id - the id of the data, for example
			7625.2.79179.AGTTCC.adnq.fastq.gz
		string description - a free text description of the data.

	*/
	typedef structure {
		string resource_name;
		string resource_url;
		string resource_version;
		timestamp resource_release_date;
		epoch resource_release_epoch;
		string data_url;
		string data_id;
		string description;
	} ExternalDataUnit;

	/* Information about a subaction that is invoked by a provenance action.

		A provenance action (PA) may invoke subactions (SA), e.g. calling a
		separate piece of code, a service, or a script. In most cases these
		calls are the same from PA to PA and so do not need to be listed in
		the provenance since providing information about the PA alone provides
		reproducibility.

		In some cases, however, SAs may change over time, such that invoking
		the same PA with the same parameters may produce different results.
		For example, if a PA calls a remote server, that server may be updated
		between a PA invoked on day T and another PA invoked on day T+1.

		The SubAction structure allows for specifying information about SAs
		that may dynamically change from PA invocation to PA invocation.

		All fields are optional but at least one field must be present.

		string name - the name of the SA.
		string ver - the version of SA.
		string code_url - a url pointing to the SA's codebase.
		string commit - a version control commit ID for the SA.
		string endpoint_url - a url pointing to the access point for the SA -
			a server url, for instance.
	*/
	typedef structure {
		string name;
		string ver;
		string code_url;
		string commit;
		string endpoint_url;
	} SubAction;

	/* A provenance action.

		A provenance action (PA) is an action taken while transforming one data
		object to another. There may be several PAs taken in series. A PA is
		typically running a script, running an api command, etc. All of the
		following fields are optional, but more information provided equates to
		better data provenance.

		If a provenance action has no fields defined at all, it is silently dropped from
		the list.

		resolved_ws_objects should never be set by the user; it is set by the
		workspace service when returning data.

		On input, only one of the time or epoch may be supplied. Both are
		supplied on output.

		The maximum size of the entire provenance object, including all actions,
		is 1MB.

		timestamp time - the time the action was started
		epoch epoch - the time the action was started.
		string caller - the name or id of the invoker of this provenance
			action. In most cases, this will be the same for all PAs.
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
		list<ref_string> input_ws_objects - the workspace objects that
			were used as input to this action; typically these will also be
			present as parts of the method_params or the script_command_line
			arguments. A reference path into the object graph may be supplied.
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
		list<ExternalDataUnit> external_data - data external to the workspace
			that was either imported to the workspace or used to create a
			workspace object.
		list<SubAction> subactions - the subactions taken as a part of this
			action.
		mapping<string, string> custom - user definable custom provenance
			fields and their values.
		string description - a free text description of this action.
	*/
	typedef structure {
		timestamp time;
		epoch epoch;
		string caller;
		string service;
		string service_ver;
		string method;
		list<UnspecifiedObject> method_params;
		string script;
		string script_ver;
		string script_command_line;
		list<ref_string> input_ws_objects;
		list<obj_ref> resolved_ws_objects;
		list<string> intermediate_incoming;
		list<string> intermediate_outgoing;
		list<ExternalDataUnit> external_data;
		list<SubAction> subactions;
		mapping<string, string> custom;
		string description;
	} ProvenanceAction;

	/* EventDate

		Represents an event in the lifecycle of a resource and the date it occurred on.

		See https://support.datacite.org/docs/datacite-metadata-schema-v44-recommended-and-optional-properties#8-date
		for more information on the events (below).

		Both event and date are required fields.

		date - the date associated with the event. The date may be in the format
			YYYY, YYYY-MM, or YYYY-MM-DD.
			Examples:
				- '2001'
				- 2021-05
				- 1998-02-15

		event - the event that occurred.
			Valid 'event' values:
				- accepted
				- available
				- copyrighted
				- collected
				- created
				- issued
				- submitted
				- updated
				- valid
				- withdrawn
				- other
	*/
	typedef structure {
		string date;
		string event;
	} EventDate;

	/* FundingReference

		Represents a funding source for a resource, including the funding body and the
		grant awarded.

		The 'funder_name' field is required; all others are optional.

		Recommended resources for organization identifiers include:
			- Research Organization Registry, http://ror.org
			- International Standard Name Identifier, https://isni.org
			- Crossref Funder Registry, https://www.crossref.org/services/funder-registry/

		Some organizations may have a digital object identifier (DOI).

		funder_id (optional) - persistent unique identifier for the funder in the format
			<database name>:<identifier within database>.
			Examples:
				- DOI:10.13039/100000015
				- ROR:04xm1d337
				- ISNI:0000000405337147

		funder_name - common name for the funder.
			Examples:
				- Joint Genome Institute
				- National Science Foundation
				- US DOE Office of Science (SC), Biological and Environmental
				Research (BER)

		award_id (optional) - code for the award, assigned by the funder.
			Examples:
				- 1296
				- CBET-0756451
				- DOI:10.46936/10.25585/60000745

		award_title (optional) - title for the award.
			Examples:
				- Metagenomic analysis of the rhizosphere of three biofuel crops at
				the KBS intensive site

		award_url (optional) - URL for the award.
			Examples:
				- https://genome.jgi.doe.gov/portal/Metanaintenssite/Metanaintenssite.info.html

	*/
	typedef structure {
		string funder_id;
		string funder_name;
		string award_id;
		string award_title;
		string award_url;
	} FundingReference;

	/* Organization

		Represents an organization.

		Recommended resources for organization identifiers and canonical organization names
		include:
			- Research Organization Registry, http://ror.org
			- International Standard Name Identifier, https://isni.org
			- Crossref Funder Registry, https://www.crossref.org/services/funder-registry/

		For example, the US DOE would be entered as:
			organization_name: United States Department of Energy
			organization_id:   ROR:01bj3aw27

		'organization_name' is required; 'organization_id' is optional.

		organization_name - common name of the organization; use the name recommended by
			ROR if possible.
			Examples:
				- KBase
				- Lawrence Berkeley National Laboratory
				- The Ohio State University

		organization_id (optional) - persistent unique identifier for the organization
			in the format <database name>:<identifier within database>.
			Examples:
				- ROR:01bj3aw27
				- ISNI:0000000123423717
				- CrossrefFunder:100000015
	*/
	typedef structure {
		string organization_name;
		string organization_id;
	} Organization;

	/* PermanentID

		Represents a persistent unique identifier for an entity, with an optional
		relationship to some other entity.

		The 'id' field is required; all other fields are optional.

		The values in the 'relationship_type' field come from controlled vocabularies
		maintained by DataCite and Crossref. See the documentation links below for more
		details.

		DataCite relation types: https://support.datacite.org/docs/datacite-metadata-schema-v44-recommended-and-optional-properties#12b-relationtype

		Crossref relation types: https://www.crossref.org/documentation/schema-library/markup-guide-metadata-segments/relationships/

		id - persistent unique ID for an entity. Should be in the format
			<database name>:<identifier within database>.
			Examples:
				- DOI:10.46936/10.25585/60000745
				- GO:0005456
				- HGNC:7470
		description (optional) - description of that entity.
			Examples:
				- Amaranthus hypochondriacus genome

		relationship_type (optional) - relationship between the ID and some other entity.
			For example, when a PermanentID class is used to represent objects in the
			CreditMetadata field 'related_identifiers', the 'relationship_type' field
			captures the relationship between the CreditMetadata and this ID.

			Valid 'relationship_type' values:
				- DataCite:Cites
				- DataCite:Compiles
				- DataCite:Continues
				- DataCite:Describes
				- DataCite:Documents
				- DataCite:HasMetadata
				- DataCite:HasPart
				- DataCite:HasVersion
				- DataCite:IsCitedBy
				- DataCite:isCompiledBy
				- DataCite:IsContinuedBy
				- DataCite:IsDerivedFrom
				- DataCite:IsDescribedBy
				- DataCite:IsDocumentedBy
				- DataCite:IsIdenticalTo
				- DataCite:IsMetadataFor
				- DataCite:IsNewVersionOf
				- DataCite:IsOriginalFormOf
				- DataCite:IsPartOf
				- DataCite:IsPreviousVersionOf
				- DataCite:IsPublishedIn
				- DataCite:IsReferencedBy
				- DataCite:IsRequiredBy
				- DataCite:IsReviewedBy
				- DataCite:IsSourceOf
				- DataCite:IsSupplementTo
				- DataCite:IsSupplementedBy
				- DataCite:IsVariantFormOf
				- DataCite:IsVersionOf
				- DataCite:Obsoletes
				- DataCite:References
				- DataCite:Requires
				- DataCite:Reviews
				- Crossref:BasedOnData
				- Crossref:Finances
				- Crossref:HasComment
				- Crossref:HasDerivation
				- Crossref:HasExpression
				- Crossref:HasFormat
				- Crossref:HasManifestation
				- Crossref:HasManuscript
				- Crossref:HasPreprint
				- Crossref:HasRelatedMaterial
				- Crossref:HasReply
				- Crossref:HasReview
				- Crossref:HasTranslation
				- Crossref:IsBasedOn
				- Crossref:IsBasisFor
				- Crossref:IsCommentOn
				- Crossref:IsDataBasisFor
				- Crossref:IsExpressionOf
				- Crossref:IsFinancedBy
				- Crossref:IsFormatOf
				- Crossref:IsManifestationOf
				- Crossref:IsManuscriptOf
				- Crossref:IsPreprintOf
				- Crossref:IsRelatedMaterial
				- Crossref:IsReplacedBy
				- Crossref:IsReplyTo
				- Crossref:IsReviewOf
				- Crossref:IsSameAs
				- Crossref:IsTranslationOf
				- Crossref:Replaces
	*/
	typedef structure {
		string id;
		string description;
		string relationship_type;
	} PermanentID;

	/* Title
		Represents the title or name of a resource.

		The 'title_string' field is required; if no value is supplied for 'title_type', it
		defaults to 'title'.

		If the title is in a language other than English, the 'title_type' should be set to
		'translated_title', and the appropriate BCP-47 tag supplied in the 'title_language'
		field.

		Note that the workspace checks that the 'title_language' field adheres to IETF
		BCP-47 syntax rules, but it does not check the validity of the tag.

		title_string - a string used as a title for a resource.
			Examples:
				- Amaranthus hypochondriacus genome
				- Геном амаранта ипохондрического

		title_language (optional) - language that the title is in, as a IETF BCP-47 tag.
			Examples:
				- fr
				- jp-JP
				- zh-Hant-CN
				- en-Latn-GB
				- mn-Cyrl

		title_type (optional) - the type of title described.
			Valid 'title_type' values:
				- title
				- subtitle
				- alternative_title
				- translated_title
				- other
	*/
	typedef structure {
		string title_string;
		string title_language;
		string title_type;
	} Title;

	/* Contributor

		Represents a contributor to the resource.

		Contributors must have a 'contributor_type', either 'person' or 'organization', and
		a 'name'.

		The 'credit_name' field is used to store the name of a person as it would appear in
		a citation. If there is no 'credit_name' supplied, the 'name' field would be used
		in citations.
		For example:
			name:		Hubert George Wells
			credit_name:	Wells, HG

			name:		Alexandria Ocasio-Cortez
			credit_name:	Ocasio-Cortez, A

			name:		Helena Bonham Carter
			credit_name:	Bonham Carter, H

		The 'contributor_role' field takes values from the DataCite and CRediT contributor
		roles vocabularies. For more information on these resources and choosing the
		appropriate roles, please see the following links:

		DataCite contributor roles: https://support.datacite.org/docs/datacite-metadata-schema-v44-recommended-and-optional-properties#7a-contributortype

		CRediT contributor role taxonomy: https://credit.niso.org

		contributor_type - must be either 'person' or 'organization'.
			Valid 'contributor_type' values:
				- person
				- organization

		contributor_id (optional) - persistent unique identifier for the contributor;
			this might be an ORCID for an individual, or a ROR ID for an organization.
			Examples:
				- ORCID:0000-0001-9557-7715
				- ROR:01znn6x10

		name - contributor name. For organizations, this should be the full (unabbreviated)
			name; for a person, the full name should be entered.
			Examples:
				- Helena Bonham Carter
				- Hubert Wells
				- Madonna
				- Marionetta Cecille de la Carte-Postale
				- National Institute of Mental Health
				- Ransome the Clown

		credit_name (optional) - for a person, how the name should appear in a citation.
			Examples:
				- Carte-Postale, MC
				- Bonham Carter, H
				- Wells, HG

		affiliations (optional) - list of organizations with which the
			contributor is affiliated. For contributors that represent an organization,
			this may be a parent organization (e.g. KBase, US DOE; Arkin lab, LBNL).

		contributor_roles (optional) - list of roles played by the contributor when working
			on the resource.
			Valid 'contributor_role' values:
				- DataCite:ContactPerson
				- DataCite:DataCollector
				- DataCite:DataCurator
				- DataCite:DataManager
				- DataCite:Distributor
				- DataCite:Editor
				- DataCite:HostingInstitution
				- DataCite:Producer
				- DataCite:ProjectLeader
				- DataCite:ProjectManager
				- DataCite:ProjectMember
				- DataCite:RegistrationAgency
				- DataCite:RegistrationAuthority
				- DataCite:RelatedPerson
				- DataCite:Researcher
				- DataCite:ResearchGroup
				- DataCite:RightsHolder
				- DataCite:Sponsor
				- DataCite:Supervisor
				- DataCite:WorkPackageLeader
				- DataCite:Other
				- CRediT:conceptualization
				- CRediT:data-curation
				- CRediT:formal-analysis
				- CRediT:funding-acquisition
				- CRediT:investigation
				- CRediT:methodology
				- CRediT:project-administration
				- CRediT:resources
				- CRediT:software
				- CRediT:supervision
				- CRediT:validation
				- CRediT:visualization
				- CRediT:writing-original-draft
				- CRediT:writing-review-editing
	*/
	typedef structure {
		string contributor_type;
		string name;
		string credit_name;
		string contributor_id;
		list<Organization> affiliations;
		list<string> contributor_roles;
	} Contributor;

	/* CreditMetadata

		Represents the credit metadata associated with a workspace object.

		In the following documentation, 'Resource' is used to refer to the workspace object
		that the CM pertains to.

		The 'resource_type' field should be filled using values from the DataCite
		resourceTypeGeneral field:

		https://support.datacite.org/docs/datacite-metadata-schema-v44-mandatory-properties#10a-resourcetypegeneral

		Currently the KBase workspace only supports credit metadata for objects of type
		'dataset'; anything else will return an error.

		The license may be supplied either as an URL pointing to licensing information for
		the resource, or using a license name. See https://choosealicense.com/appendix/ for
		a list of common open source licenses.

		Required fields are:
		- identifier
		- resource_type
		- versioning information: if the resource does not have an explicit version number,
		one or more dates should be supplied: ideally the date of resource publication and
		the last update (if applicable).
		- contributors (one or more required)
		- titles (one or more required)

		comments - a list of strings of freeform text providing extra information about
			this credit metadata.
			Examples:
				- Credit metadata generated automatically from DOI:10.13039/100000015

		identifier - persistent unique identifier for the resource (i.e. the source data
			for this workspace object).
			Should be in the format <database name>:<identifier within database>
			Examples:
				- RefSeq:GCF_004214875.1
				- GenBank:CP035949.1
				- img.taxon:648028003

		license (optional) - usage license for the resource. May be a text string or an
			URL. Abbreviations should be spelled out where possible (e.g. 'Creative
			Commons 4.0' instead of 'CC-BY-4.0').
			Examples:
				- Creative Commons 4.0
				- MIT
				- https://jgi.doe.gov/user-programs/pmo-overview/policies/

		resource_type - the broad type of the source data for this workspace object.
			Defaults to 'dataset' (the only valid value currently) if no value is
			provided.
			Valid 'resource_type' values:
				- dataset

		version (optional if dates are provided) - the version of the resource. This must
			be an absolute version, not a relative version like 'latest'.
			Examples:
				- 5
				- 1.2.1
				- 20220405

		contributors - a list of people and/or organizations who contributed to the
			resource.

		dates (optional if version is provided) - a list of relevant lifecycle events for
			the resource.

		funding (optional) - funding sources for the resource.

		related_identifiers (optional) - other resolvable persistent unique IDs related to
			the resource.

		titles - one or more titles for the resource.

	*/
	typedef structure {
		string identifier;
		string license;
		string resource_type;
		string version;
		list<string> comments;
		list<Contributor> contributors;
		list<EventDate> dates;
		list<FundingReference> funding;
		list<PermanentID> related_identifiers;
		list<Title> titles;
	} CreditMetadata;

	/* CreditMetadataEntry

		Container for the credit metadata for a workspace object.

		Workspace objects have credit metadata (CM), or citation information for the
		object. This information allows data imported into KBase to retain the appropriate
		details for users wishing to cite the data source, and for those who contributed to
		its creation to be credited for their work.

		Credit metadata is added after object creation, and requires the user to be a
		workspace admin. Username, timestamp, and credit metadata schema version are stored
		with the credit information.

		WS admins can use the workspace `administer` function to add CM to a workspace
		object.

		All fields will be populated.

		credit_metadata - the credit metadata itself

		credit_metadata_schema_version - version of the credit metadata schema used
			Examples:
				- 1.1.0

		saved_by - KBase workspace ID of the user who added this entry

		timestamp - unix timestamp for the addition of this set of credit metadata
	*/
	typedef structure {
		CreditMetadata credit_metadata;
		username saved_by;
		string credit_metadata_schema_version;
		epoch timestamp;
	} CreditMetadataContainer;

	/*
		Returns the version of the workspace service.
	*/
	funcdef ver() returns(string ver) authentication none;

	/* Input parameters for the "create_workspace" function.

		Required arguments:
		ws_name workspace - name of the workspace to be created.

		Optional arguments:
		permission globalread - 'r' to set the new workspace globally readable,
			default 'n'.
		string description - A free-text description of the new workspace, 1000
			characters max. Longer strings will be mercilessly and brutally
			truncated.
		usermeta meta - arbitrary user-supplied metadata for the workspace.
	*/
	typedef structure {
		ws_name workspace;
		permission globalread;
		string description;
		usermeta meta;
	} CreateWorkspaceParams;

	/*
		Creates a new workspace.
	*/
	funcdef create_workspace(CreateWorkspaceParams params) returns
		(workspace_info info) authentication required;

	/* Input parameters for the "alter_workspace_metadata" function.

		Required arguments:
		WorkspaceIdentity wsi - the workspace to be altered

		One or both of the following arguments are required:
		usermeta new - metadata to assign to the workspace. Duplicate keys will
			be overwritten.
		list<string> remove - these keys will be removed from the workspace
			metadata key/value pairs.
	*/
	typedef structure {
		WorkspaceIdentity wsi;
		usermeta new;
		list<string> remove;
	} AlterWorkspaceMetadataParams;

	/*
		Change the metadata associated with a workspace.
	*/
	funcdef alter_workspace_metadata(AlterWorkspaceMetadataParams params)
		returns() authentication required;

	/* Input parameters for the "clone_workspace" function.

		Note that deleted objects are not cloned, although hidden objects are
		and remain hidden in the new workspace.

		Required arguments:
		WorkspaceIdentity wsi - the workspace to be cloned.
		ws_name workspace - name of the workspace to be cloned into. This must
			be a non-existant workspace name.

		Optional arguments:
		permission globalread - 'r' to set the new workspace globally readable,
			default 'n'.
		string description - A free-text description of the new workspace, 1000
			characters max. Longer strings will be mercilessly and brutally
			truncated.
		usermeta meta - arbitrary user-supplied metadata for the workspace.
		list<ObjectIdentity> exclude - exclude the specified objects from the
			cloned workspace. Either an object ID or a object name must be
			specified in each ObjectIdentity - any supplied reference strings,
			workspace names or IDs, and versions are ignored.
	*/
	typedef structure {
		WorkspaceIdentity wsi;
		ws_name workspace;
		permission globalread;
		string description;
		usermeta meta;
		list<ObjectIdentity> exclude;
	} CloneWorkspaceParams;

	/*
		Clones a workspace.
	*/
	funcdef clone_workspace(CloneWorkspaceParams params) returns
		(workspace_info info) authentication required;

	/* Lock a workspace, preventing further changes.

		WARNING: Locking a workspace is permanent. A workspace, once locked,
		cannot be unlocked.

		The only changes allowed for a locked workspace are changing user
		based permissions or making a private workspace globally readable,
		thus permanently publishing the workspace. A locked, globally readable
		workspace cannot be made private.
	*/
	funcdef lock_workspace(WorkspaceIdentity wsi) returns(workspace_info info)
		authentication required;

	/* DEPRECATED

		Input parameters for the "get_workspacemeta" function. Provided for
		backwards compatibility.

		One, and only one of:
		ws_name workspace - name of the workspace.
		ws_id id - the numerical ID of the workspace.

		Optional arguments:
		string auth - the authentication token of the KBase account accessing
			the workspace. Overrides the client provided authorization
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
		returns(workspace_metadata metadata) authentication optional;

	/*
		Get information associated with a workspace.
	*/
	funcdef get_workspace_info(WorkspaceIdentity wsi)
		returns (workspace_info info) authentication optional;

	/*
		Get a workspace's description.
	*/
	funcdef get_workspace_description(WorkspaceIdentity wsi)
		returns (string description) authentication optional;

	/* Input parameters for the "set_permissions" function.

		One, and only one, of the following is required:
		ws_id id - the numerical ID of the workspace.
		ws_name workspace - the name of the workspace.

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

	/*
		Set permissions for a workspace.
	*/
	funcdef set_permissions(SetPermissionsParams params) returns()
		authentication required;

	/* Input parameters for the "set_global_permission" function.

		One, and only one, of the following is required:
		ws_id id - the numerical ID of the workspace.
		ws_name workspace - the name of the workspace.

		Required arguments:
		permission new_permission - the permission to assign to all users,
			either 'n' or 'r'. 'r' means that all users will be able to read
			the workspace; otherwise users must have specific permission to
			access the workspace.
	*/
	typedef structure {
		ws_name workspace;
		ws_id id;
		permission new_permission;
	} SetGlobalPermissionsParams;

	/*
		Set the global permission for a workspace.
	*/
	funcdef set_global_permission(SetGlobalPermissionsParams params) returns()
		authentication required;

	/* Input parameters for the "set_workspace_description" function.

		One, and only one, of the following is required:
		ws_id id - the numerical ID of the workspace.
		ws_name workspace - the name of the workspace.

		Optional arguments:
		string description - A free-text description of the workspace, 1000
			characters max. Longer strings will be mercilessly and brutally
			truncated. If omitted, the description is set to null.
	*/
	typedef structure {
		ws_name workspace;
		ws_id id;
		string description;
	} SetWorkspaceDescriptionParams;

	/*
		Set the description for a workspace.
	*/
	funcdef set_workspace_description(SetWorkspaceDescriptionParams params)
		returns() authentication required;

	/* Input parameters for the "get_permissions_mass" function.
		workspaces - the workspaces for which to return the permissions,
			maximum 1000.
	*/
	typedef structure {
		list<WorkspaceIdentity> workspaces;
	} GetPermissionsMassParams;

	/* A set of workspace permissions.
		perms - the list of permissions for each requested workspace

	*/
	typedef structure {
		list<mapping<username, permission>> perms;
	} WorkspacePermissions;

	/*
		Get permissions for multiple workspaces.
	*/
	funcdef get_permissions_mass(GetPermissionsMassParams mass)
		returns(WorkspacePermissions perms) authentication optional;

	/*
		Get permissions for a workspace.
		@deprecated get_permissions_mass
	*/
	funcdef get_permissions(WorkspaceIdentity wsi) returns
		(mapping<username, permission> perms) authentication optional;

	/* Input parameters for the "save_object" function. Provided for backwards
		compatibility.

		Required arguments:
		type_string type - type of the object to be saved
		ws_name workspace - name of the workspace where the object is to be
			saved
		obj_name id - name behind which the object will be saved in the
			workspace
		UnspecifiedObject data - data to be saved in the workspace

		Optional arguments:
		usermeta metadata - arbitrary user-supplied metadata for the object,
			not to exceed 16kb; if the object type specifies automatic
			metadata extraction with the 'meta ws' annotation, and your
			metadata name conflicts, then your metadata will be silently
			overwritten.
		string auth - the authentication token of the KBase account accessing
			the workspace. Overrides the client provided authorization
			credentials if they exist.

		@deprecated
	*/
	typedef structure {
		obj_name id;
		type_string type;
		UnspecifiedObject data;
		ws_name workspace;
		mapping<string,string> metadata;
		string auth;
	} save_object_params;

	/*
		Saves the input object data and metadata into the selected workspace,
			returning the object_metadata of the saved object. Provided
			for backwards compatibility.

		@deprecated Workspace.save_objects
	*/
	funcdef save_object(save_object_params params)
		returns(object_metadata metadata) authentication optional;

	/* An object and associated data required for saving.

		Required arguments:
		type_string type - the type of the object. Omit the version information
			to use the latest version.
		UnspecifiedObject data - the object data.
		One, and only one, of:
			obj_name name - the name of the object.
			obj_id objid - the id of the object to save over.


		Optional arguments:
		usermeta meta - arbitrary user-supplied metadata for the object,
			not to exceed 16kb; if the object type specifies automatic
			metadata extraction with the 'meta ws' annotation, and your
			metadata name conflicts, then your metadata will be silently
			overwritten.
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
		ws_name workspace - the name of the workspace.

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
		returns (list<object_info> info) authentication required;

	/* Input parameters for the "get_object" function. Provided for backwards
		compatibility.

		Required arguments:
		ws_name workspace - Name of the workspace containing the object to be
			retrieved
		obj_name id - Name of the object to be retrieved

		Optional arguments:
		int instance - Version of the object to be retrieved, enabling
			retrieval of any previous version of an object
		string auth - the authentication token of the KBase account accessing
			the object. Overrides the client provided authorization
			credentials if they exist.

		@deprecated Workspace.ObjectIdentity
	*/
	typedef structure {
		obj_name id;
		ws_name workspace;
		int instance;
		string auth;
	} get_object_params;

	/* Output generated by the "get_object" function. Provided for backwards
		compatibility.

		UnspecifiedObject data - The object's data.
		object_metadata metadata - Metadata for object retrieved/

		@deprecated Workspaces.ObjectData
	*/
	typedef structure {
		UnspecifiedObject data;
		object_metadata metadata;
	} get_object_output;

	/*
		Retrieves the specified object from the specified workspace.
		Both the object data and metadata are returned.
		Provided for backwards compatibility.

		@deprecated Workspace.get_objects
	*/
	funcdef get_object(get_object_params params)
		returns (get_object_output output) authentication optional;

	/*  DEPRECATED

		The provenance and supplemental info for an object.

		object_info info - information about the object.
		list<ProvenanceAction> provenance - the object's provenance.
		username creator - the user that first saved the object to the
			workspace.
		ws_id orig_wsid - the id of the workspace in which this object was
				originally saved. Missing for objects saved prior to version
				0.4.1.
		timestamp created - the date the object was first saved to the
			workspace.
		epoch epoch - the date the object was first saved to the
			workspace.
		list<obj_ref> - the references contained within the object.
		obj_ref copied - the reference of the source object if this object is
			a copy and the copy source exists and is accessible.
			null otherwise.
		boolean copy_source_inaccessible - true if the object was copied from
			another object, but that object is no longer accessible to the
			user. False otherwise.
		mapping<id_type, list<extracted_id>> extracted_ids - any ids extracted
			from the object.
		string handle_error - if an error occurs while setting ACLs on
			embedded external IDs, it will be reported here. If not for historical reasons the
			parameter would be called "external_id_error".
		string handle_stacktrace - the stacktrace for handle_error. As above, the parameter
			should be called "external_id_stacktrace".

		@deprecated
	*/
	typedef structure {
		object_info info;
		list<ProvenanceAction> provenance;
		username creator;
		ws_id orig_wsid;
		timestamp created;
		epoch epoch;
		list<obj_ref> refs;
		obj_ref copied;
		boolean copy_source_inaccessible;
		mapping<id_type, list<extracted_id>> extracted_ids;
		string handle_error;
		string handle_stacktrace;
	} ObjectProvenanceInfo;

	/*  DEPRECATED
		Get object provenance from the workspace.

		@deprecated Workspace.get_objects2
	*/
	funcdef get_object_provenance(list<ObjectIdentity> object_ids)
		returns (list<ObjectProvenanceInfo> data) authentication optional;

	/* The data and supplemental info for an object.

		UnspecifiedObject data - the object's data or subset data.
		object_info info - information about the object.
		list<obj_ref> path - the path to the object through the object reference graph. All the
			references in the path are absolute.
		list<ProvenanceAction> provenance - the object's provenance.
		CreditMetadataContainer credit_metadata - the credit information for the object.
		username creator - the user that first saved the object to the workspace.
		ws_id orig_wsid - the id of the workspace in which this object was
				originally saved. Missing for objects saved prior to version
				0.4.1.
		timestamp created - the date the object was first saved to the
			workspace.
		epoch epoch - the date the object was first saved to the
			workspace.
		list<obj_ref> refs - the references contained within the object.
		obj_ref copied - the reference of the source object if this object is
			a copy and the copy source exists and is accessible.
			null otherwise.
		boolean copy_source_inaccessible - true if the object was copied from
			another object, but that object is no longer accessible to the
			user. False otherwise.
		mapping<id_type, list<extracted_id>> extracted_ids - any ids extracted
			from the object.
		string handle_error - if an error occurs while setting ACLs on
			embedded external IDs, it will be reported here. If not for historical reasons the
			parameter would be called "external_id_error".
		string handle_stacktrace - the stacktrace for handle_error. As above, the parameter
			should be called "external_id_stacktrace".

	*/
	typedef structure {
		UnspecifiedObject data;
		object_info info;
		list<obj_ref> path;
		list<ProvenanceAction> provenance;
		CreditMetadataContainer credit_metadata;
		username creator;
		ws_id orig_wsid;
		timestamp created;
		epoch epoch;
		list<obj_ref> refs;
		obj_ref copied;
		boolean copy_source_inaccessible;
		mapping<id_type, list<extracted_id>> extracted_ids;
		string handle_error;
		string handle_stacktrace;
	} ObjectData;

	/*  DEPRECATED
		Get objects from the workspace.
		@deprecated Workspace.get_objects2
	*/
	funcdef get_objects(list<ObjectIdentity> object_ids)
		returns (list<ObjectData> data) authentication optional;

	/* Input parameters for the get_objects2 function.

		Required parameters:
		list<ObjectSpecification> objects - the list of object specifications
			for the objects to return (via reference chain and as a subset if
			specified).

		Optional parameters:
		boolean ignoreErrors - Don't throw an exception if an object cannot
			be accessed; return null for that object's information instead.
			Default false.
		boolean no_data - return the provenance, credit metadata, references, and
			object_info for this object without the object data. Default false.
		boolean skip_external_system_updates - if the objects contain any external IDs, don't
			contact external systems to perform any updates for those IDs (often ACL updates,
			e.g. for handle / blobstore / sample IDs). In some cases this can speed up fetching the
			data. Default false.
		boolean batch_external_system_updates - if the objects contain any external IDs,
			send all external system updates in a batch to each external system when possible
			rather than object by object. This can potentially speed up the updates, but the
			drawback is that if the external update fails for any object, all the objects that
			required updates for that system will be marked as having a failed update.
			Has no effect if skip_external_system_updates is true. Default false.
	*/
	typedef structure {
		list<ObjectSpecification> objects;
		boolean ignoreErrors;
		boolean no_data;
		boolean skip_external_system_updates;
		boolean batch_external_system_updates;
	} GetObjects2Params;

	/* Results from the get_objects2 function.

		list<ObjectData> data - the returned objects.
	*/
	typedef structure {
		list<ObjectData> data;
	} GetObjects2Results;

	/* Get objects from the workspace. */
	funcdef get_objects2(GetObjects2Params params)
		returns(GetObjects2Results results) authentication optional;

	/*  DEPRECATED
		Get portions of objects from the workspace.

		When selecting a subset of an array in an object, the returned
		array is compressed to the size of the subset, but the ordering of
		the array is maintained. For example, if the array stored at the
		'feature' key of a Genome object has 4000 entries, and the object paths
		provided are:
			/feature/7
			/feature/3015
			/feature/700
		The returned feature array will be of length three and the entries will
		consist, in order, of the 7th, 700th, and 3015th entries of the
		original array.
		@deprecated Workspace.get_objects2
	*/
	funcdef get_object_subset(list<SubObjectIdentity> sub_object_ids)
		returns (list<ObjectData> data) authentication optional;

	/*
		Get an object's history. The version argument of the ObjectIdentity is
		ignored.
	*/
	funcdef get_object_history(ObjectIdentity object)
		 returns (list<object_info> history) authentication optional;

	/*
		List objects that reference one or more specified objects. References
		in the deleted state are not returned.
	*/
	funcdef list_referencing_objects(list<ObjectIdentity> object_ids)
		returns (list<list<object_info>> referrers) authentication optional;

	/*
		DEPRECATED

		List the number of times objects have been referenced.

		This count includes both provenance and object-to-object references
		and, unlike list_referencing_objects, includes objects that are
		inaccessible to the user.

		@deprecated
	*/
	funcdef list_referencing_object_counts(list<ObjectIdentity> object_ids)
		returns (list<int> counts) authentication optional;

	/*  DEPRECATED

		Get objects by references from other objects.

		NOTE: In the vast majority of cases, this method is not necessary and
		get_objects should be used instead.

		get_referenced_objects guarantees that a user that has access to an
		object can always see a) objects that are referenced inside the object
		and b) objects that are referenced in the object's provenance. This
		ensures that the user has visibility into the entire provenance of the
		object and the object's object dependencies (e.g. references).

		The user must have at least read access to the first object in each
		reference chain, but need not have access to any further objects in
		the chain, and those objects may be deleted.

		@deprecated Workspace.get_objects2

	*/
	funcdef get_referenced_objects(list<ref_chain> ref_chains)
		returns (list<ObjectData> data) authentication optional;

	/*
		Input parameters for the "list_workspaces" function. Provided for
		backwards compatibility.

		Optional parameters:
		string auth - the authentication token of the KBase account accessing
			the list of workspaces. Overrides the client provided authorization
			credentials if they exist.
		boolean excludeGlobal - if excludeGlobal is true exclude world
			readable workspaces. Defaults to false.

		@deprecated Workspace.ListWorkspaceInfoParams
	*/
	typedef structure {
		string auth;
		boolean excludeGlobal;
	} list_workspaces_params;

	/*
		Lists the metadata of all workspaces a user has access to. Provided for
		backwards compatibility - to be replaced by the functionality of
		list_workspace_info

		@deprecated Workspace.list_workspace_info
	*/
	funcdef list_workspaces(list_workspaces_params params)
		returns (list<workspace_metadata> workspaces) authentication optional;

	/*
		Input parameters for the "list_workspace_info" function.

		Only one of each timestamp/epoch pair may be supplied.

		Optional parameters:
		permission perm - filter workspaces by minimum permission level. 'None'
			and 'readable' are ignored.
		list<username> owners - filter workspaces by owner.
		usermeta meta - filter workspaces by the user supplied metadata. NOTE:
			only one key/value pair is supported at this time. A full map
			is provided as input for the possibility for expansion in the
			future.
		timestamp after - only return workspaces that were modified after this
			date.
		timestamp before - only return workspaces that were modified before
			this date.
		epoch after_epoch - only return workspaces that were modified after
			this date.
		epoch before_epoch - only return workspaces that were modified before
			this date.
		boolean excludeGlobal - if excludeGlobal is true exclude world
			readable workspaces. Defaults to false.
		boolean showDeleted - show deleted workspaces that are owned by the
			user.
		boolean showOnlyDeleted - only show deleted workspaces that are owned
			by the user.

	*/
	typedef structure {
		permission perm;
		list<username> owners;
		usermeta meta;
		timestamp after;
		timestamp before;
		epoch after_epoch;
		epoch before_epoch;
		boolean excludeGlobal;
		boolean showDeleted;
		boolean showOnlyDeleted;
	} ListWorkspaceInfoParams;

	/*
		List workspaces viewable by the user.
	 */
	funcdef list_workspace_info(ListWorkspaceInfoParams params)
		returns(list<workspace_info> wsinfo) authentication optional;

	/*
		Input parameters for the "list_workspace_ids" function.

		Optional parameters:
		permission perm - filter workspaces by minimum permission level. 'None'
			and 'readable' are ignored.
		boolean onlyGlobal - if onlyGlobal is true only include world readable
			workspaces. Defaults to false. If true, excludeGlobal is ignored.
		boolean excludeGlobal - if excludeGlobal is true exclude world
			readable workspaces. Defaults to true.
	*/
	typedef structure {
		permission perm;
		boolean excludeGlobal;
		boolean onlyGlobal;
	} ListWorkspaceIDsParams;

	/*
		Results of the "list_workspace_ids" function.

		list<int> workspaces - the workspaces to which the user has explicit
			access.
		list<int> pub - the workspaces to which the user has access because
			they're globally readable.
	*/
	typedef structure {
		list<int> workspaces;
		list<int> pub;
	} ListWorkspaceIDsResults;

	/*
		List workspace IDs to which the user has access.

		This function returns a subset of the information in the
		list_workspace_info method and should be substantially faster.
	*/
	funcdef list_workspace_ids(ListWorkspaceIDsParams params)
		returns(ListWorkspaceIDsResults results) authentication optional;

	/* Input parameters for the "list_workspace_objects" function. Provided
		for backwards compatibility.

		Required arguments:
		ws_name workspace - Name of the workspace for which objects should be
			listed

		Optional arguments:
		type_string type - type of the objects to be listed. Here, omitting
			version information will find any objects that match the provided
			type - e.g. Foo.Bar-0 will match Foo.Bar-0.X where X is any
			existing version.
		boolean showDeletedObject - show objects that have been deleted
		string auth - the authentication token of the KBase account requesting
			access. Overrides the client provided authorization credentials if
			they exist.

		@deprecated Workspace.ListObjectsParams
	*/
	typedef structure {
	   ws_name workspace;
	   type_string type;
	   boolean showDeletedObject;
	   string auth;
	} list_workspace_objects_params;

	/*
		Lists the metadata of all objects in the specified workspace with the
		specified type (or with any type). Provided for backwards compatibility.

		@deprecated Workspace.list_objects
	*/
	funcdef list_workspace_objects(list_workspace_objects_params params)
		returns(list<object_metadata> objects) authentication optional;

	/* Parameters for the 'list_objects' function.

		At least one, and no more than 10000, workspaces must be specified in one of the
		two following parameters. It is strongly recommended that the list is restricted to
		the workspaces of interest, or the results may be very large:
		list<ws_id> ids - the numerical IDs of the workspaces of interest.
		list<ws_name> workspaces - the names of the workspaces of interest.

		Only one of each timestamp/epoch pair may be supplied.

		Optional arguments:
		type_string type - type of the objects to be listed.  Here, omitting
			version information will find any objects that match the provided
			type - e.g. Foo.Bar-0 will match Foo.Bar-0.X where X is any
			existing version.
		permission perm - DEPRECATED, no longer useful. Filter on minimum permission by providing
			only workspaces with the desired permission levels in the input list(s).
		list<username> savedby - filter objects by the user that saved or
			copied the object.
		usermeta meta - filter objects by the user supplied metadata. NOTE:
			only one key/value pair is supported at this time. A full map
			is provided as input for the possibility for expansion in the
			future.
		timestamp after - only return objects that were created after this
			date.
		timestamp before - only return objects that were created before this
			date.
		epoch after_epoch - only return objects that were created after this
			date.
		epoch before_epoch - only return objects that were created before this
			date.
		string startafter - a reference-like string that determines where the
			list of objects will begin. It takes the form X/Y/Z, where X is
			the workspace ID, Y the object ID, and Z the version. The version
			may be omitted, and the object ID omitted if the version is also
			omitted. After a '/' separator either an integer or no characters
			at all, including whitespace, may occur. Whitespace strings are
			ignored. If startafter is provided, after, before,
			after_epoch, before_epoch, savedby, meta, minObjectID, and
			maxObjectID may not be provided. Only objects that are ordered
			after the reference, exclusive, will be included in the
			result, and the resulting list will be sorted by reference.
		obj_id minObjectID - only return objects with an object id greater or
			equal to this value.
		obj_id maxObjectID - only return objects with an object id less than or
			equal to this value.
		boolean showDeleted - show deleted objects in workspaces to which the
			user has write access.
		boolean showOnlyDeleted - only show deleted objects in workspaces to
			which the user has write access.
		boolean showHidden - show hidden objects.
		boolean showAllVersions - show all versions of each object that match
			the filters rather than only the most recent version.
		boolean includeMetadata - include the user provided metadata in the
			returned object_info. If false (0 or null), the default, the
			metadata will be null.
		boolean excludeGlobal - DEPRECATED, no longer useful. Filter on global workspaces by
			excluding them from the input workspace list(s).
		int limit - limit the output to X objects. Default and maximum value
			is 10000. Limit values < 1 are treated as 10000, the default.
	*/
	typedef structure {
		list<ws_name> workspaces;
		list<ws_id> ids;
		type_string type;
		permission perm;
		list<username> savedby;
		usermeta meta;
		timestamp after;
		timestamp before;
		epoch after_epoch;
		epoch before_epoch;
		string startafter;
		obj_id minObjectID;
		obj_id maxObjectID;
		boolean showDeleted;
		boolean showOnlyDeleted;
		boolean showHidden;
		boolean showAllVersions;
		boolean includeMetadata;
		boolean excludeGlobal;
		int limit;
	} ListObjectsParams;

	/*
		List objects in one or more workspaces.
	*/
	funcdef list_objects(ListObjectsParams params)
		returns(list<object_info> objinfo) authentication optional;

	/* Input parameters for the "get_objectmeta" function.

		Required arguments:
		ws_name workspace - name of the workspace containing the object for
			 which metadata is to be retrieved
		obj_name id - name of the object for which metadata is to be retrieved

		Optional arguments:
		int instance - Version of the object for which metadata is to be
			 retrieved, enabling retrieval of any previous version of an object
		string auth - the authentication token of the KBase account requesting
			access. Overrides the client provided authorization credentials if
			they exist.

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

		@deprecated Workspace.get_object_info3
	*/
	funcdef get_objectmeta(get_objectmeta_params params)
		returns(object_metadata metadata) authentication optional;

	/*
		Get information about objects from the workspace.

		Set includeMetadata true to include the user specified metadata.
		Otherwise the metadata in the object_info will be null.

		This method will be replaced by the behavior of get_object_info_new
		in the future.

		@deprecated Workspace.get_object_info3
	*/
	funcdef get_object_info(list<ObjectIdentity> object_ids,
		boolean includeMetadata) returns (list<object_info> info)
		authentication optional;

	/* Input parameters for the "get_object_info_new" function.

		Required arguments:
		list<ObjectSpecification> objects - the objects for which the
			information should be fetched. Subsetting related parameters are
			ignored.

		Optional arguments:
		boolean includeMetadata - include the object metadata in the returned
			information. Default false.
		boolean ignoreErrors - Don't throw an exception if an object cannot
			be accessed; return null for that object's information instead.
			Default false.

		@deprecated Workspace.GetObjectInfo3Params
	*/
	typedef structure {
		list<ObjectSpecification> objects;
		boolean includeMetadata;
		boolean ignoreErrors;
	} GetObjectInfoNewParams;

	/*
		Get information about objects from the workspace.

		@deprecated Workspace.get_object_info3

	*/
	funcdef get_object_info_new(GetObjectInfoNewParams params)
		returns (list<object_info> info) authentication optional;

	/* Input parameters for the "get_object_info3" function.

		Required arguments:
		list<ObjectSpecification> objects - the objects for which the
			information should be fetched. Subsetting related parameters are
			ignored.

		Optional arguments:
		boolean includeMetadata - include the object metadata in the returned
			information. Default false.
		boolean ignoreErrors - Don't throw an exception if an object cannot
			be accessed; return null for that object's information and path instead.
			Default false.
	*/
	typedef structure {
		list<ObjectSpecification> objects;
		boolean includeMetadata;
		boolean ignoreErrors;
	} GetObjectInfo3Params;

	/* Output from the get_object_info3 function.

		list<object_info> infos - the object_info data for each object.
		list<list<obj_ref> paths - the path to the object through the object reference graph for
			each object. All the references in the path are absolute.
	*/
	typedef structure {
		list<object_info> infos;
		list<list<obj_ref>> paths;
	} GetObjectInfo3Results;

	funcdef get_object_info3(GetObjectInfo3Params params)
		returns (GetObjectInfo3Results results) authentication optional;

	/* Input parameters for the 'rename_workspace' function.

		Required arguments:
		WorkspaceIdentity wsi - the workspace to rename.
		ws_name new_name - the new name for the workspace.
	*/
	typedef structure {
		WorkspaceIdentity wsi;
		ws_name new_name;
	} RenameWorkspaceParams;

	/*
		Rename a workspace.
	*/
	funcdef rename_workspace(RenameWorkspaceParams params)
		returns(workspace_info renamed) authentication required;

	/* Input parameters for the 'rename_object' function.

		Required arguments:
		ObjectIdentity obj - the object to rename.
		obj_name new_name - the new name for the object.
	*/
	typedef structure {
		ObjectIdentity obj;
		obj_name new_name;
	} RenameObjectParams;

	/*
		Rename an object. User meta data is always returned as null.
	*/
	funcdef rename_object(RenameObjectParams params)
		returns(object_info renamed) authentication required;

	/* Input parameters for the 'copy_object' function.

		If the 'from' ObjectIdentity includes no version and the object is
		copied to a new name, the entire version history of the object is
		copied. In all other cases only the version specified, or the latest
		version if no version is specified, is copied.

		The version from the 'to' ObjectIdentity is always ignored.

		Required arguments:
		ObjectIdentity from - the object to copy.
		ObjectIdentity to - where to copy the object.
	*/
	typedef structure {
		ObjectIdentity from;
		ObjectIdentity to;
	} CopyObjectParams;

	/*
		Copy an object. Returns the object_info for the newest version.
	*/
	funcdef copy_object(CopyObjectParams params)
		returns(object_info copied) authentication required;

	/* Revert an object.

		The object specified in the ObjectIdentity is reverted to the version
		specified in the ObjectIdentity.
	*/
	funcdef revert_object(ObjectIdentity object)
		returns(object_info reverted) authentication required;

	/* Input parameters for the get_names_by_prefix function.

		Required arguments:
		list<WorkspaceIdentity> workspaces - the workspaces to search.
		string prefix - the prefix of the object names to return.

		Optional arguments:
		boolean includeHidden - include names of hidden objects in the results.
			Default false.
	*/
	typedef structure {
		list<WorkspaceIdentity> workspaces;
		string prefix;
		boolean includeHidden;
	} GetNamesByPrefixParams;

	/* Results object for the get_names_by_prefix function.

		list<list<obj_name>> names - the names matching the provided prefix,
			listed in order of the input workspaces.
	*/
	typedef structure {
		list<list<obj_name>> names;
	} GetNamesByPrefixResults;

	/*
		Get object names matching a prefix. At most 1000 names are returned.
		No particular ordering is guaranteed, nor is which names will be
		returned if more than 1000 are found.

		This function is intended for use as an autocomplete helper function.
	*/
	funcdef get_names_by_prefix(GetNamesByPrefixParams params)
		returns(GetNamesByPrefixResults res) authentication optional;

	/*
		Hide objects. All versions of an object are hidden, regardless of
		the version specified in the ObjectIdentity. Hidden objects do not
		appear in the list_objects method.
	*/
	funcdef hide_objects(list<ObjectIdentity> object_ids) returns()
		authentication required;

	/*
		Unhide objects. All versions of an object are unhidden, regardless
		of the version specified in the ObjectIdentity.
	*/
	funcdef unhide_objects(list<ObjectIdentity> object_ids) returns()
		authentication required;

	/*
		Delete objects. All versions of an object are deleted, regardless of
		the version specified in the ObjectIdentity.
	*/
	funcdef delete_objects(list<ObjectIdentity> object_ids) returns()
		authentication required;

	/*
		Undelete objects. All versions of an object are undeleted, regardless
		of the version specified in the ObjectIdentity. If an object is not
		deleted, no error is thrown.
	*/
	funcdef undelete_objects(list<ObjectIdentity> object_ids) returns()
		authentication required;

	/*
		Delete a workspace. All objects contained in the workspace are deleted.
	*/
	funcdef delete_workspace(WorkspaceIdentity wsi) returns()
		authentication required;

	/* **************** Type registering functions ******************** */

	/* A type specification (typespec) file in the KBase Interface Description
		Language (KIDL).
	*/
	typedef string typespec;

	/* A module name defined in a KIDL typespec. */
	typedef string modulename;

	/* A type definition name in a KIDL typespec. */
	typedef string typename;

	/* A version of a type.
		Specifies the version of the type  in a single string in the format
		[major].[minor]:

		major - an integer. The major version of the type. A change in the
			major version implies the type has changed in a non-backwards
			compatible way.
		minor - an integer. The minor version of the type. A change in the
			minor version implies that the type has changed in a way that is
			backwards compatible with previous type definitions.
	*/
	typedef string typever;

	/* A function string for referencing a funcdef.
		Specifies the function and its version in a single string in the format
		[modulename].[funcname]-[major].[minor]:

		modulename - a string. The name of the module containing the function.
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

	/* The version of a typespec file. */
	typedef int spec_version;

	/* The JSON Schema (v4) representation of a type definition. */
	typedef string jsonschema;

	/* Request ownership of a module name. A Workspace administrator
		must approve the request.
	*/
	funcdef request_module_ownership(modulename mod) returns()
		authentication required;

	/* Parameters for the register_typespec function.

		Required arguments:
		One of:
		typespec spec - the new typespec to register.
		modulename mod - the module to recompile with updated options (see below).

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
	} RegisterTypespecParams;

	/* Register a new typespec or recompile a previously registered typespec
		with new options.
		See the documentation of RegisterTypespecParams for more details.
		Also see the release_types function.
	*/
	funcdef register_typespec(RegisterTypespecParams params)
		returns (mapping<type_string,jsonschema>) authentication required;

	/* Parameters for the register_typespec_copy function.

		Required arguments:
		string external_workspace_url - the URL of the  workspace server from
			which to copy a typespec.
		modulename mod - the name of the module in the workspace server

		Optional arguments:
		spec_version version - the version of the module in the workspace
			server

	*/
	typedef structure {
		string external_workspace_url;
		modulename mod;
		spec_version version;
	} RegisterTypespecCopyParams;

	/* Register a copy of new typespec or refresh an existing typespec which is
		loaded from another workspace for synchronization. Method returns new
		version of module in current workspace.

		Also see the release_types function.
	*/
	funcdef register_typespec_copy(RegisterTypespecCopyParams params)
		returns(spec_version new_local_version) authentication required;

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
	funcdef release_module(modulename mod) returns(list<type_string> types)
		authentication required;

	/* Parameters for the list_modules() function.

		Optional arguments:
		username owner - only list modules owned by this user.
	*/
	typedef structure {
		username owner;
	} ListModulesParams;

	/* List typespec modules. */
	funcdef list_modules(ListModulesParams params)
		returns(list<modulename> modules) authentication none;

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
		list<spec_version> - a set or subset of released versions associated
			with the module.
	*/
	typedef structure {
		modulename mod;
		list<spec_version> vers;
		list<spec_version> released_vers;
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
		list<func_string> functions - list of names of functions registered in spec.
		boolean is_released - shows if this version of module was released (and
			hence can be seen by others).
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
		boolean is_released;
	} ModuleInfo;

	funcdef get_module_info(GetModuleInfoParams params)
		returns(ModuleInfo info) authentication optional;

	/* Get JSON schema for a type. */
	funcdef get_jsonschema(type_string type) returns(jsonschema schema)
		authentication optional;

	/* Translation from types qualified with MD5 to their semantic versions */
	funcdef translate_from_MD5_types(list<type_string> md5_types)
		returns(mapping<type_string, list<type_string>> sem_types)
		authentication none;

	/* Translation from types qualified with semantic versions to their MD5'ed versions */
	funcdef translate_to_MD5_types(list<type_string> sem_types)
		returns(mapping<type_string, type_string> md5_types)
		authentication optional;

	/* Information about a type

		type_string type_def - resolved type definition id.
		string description - the description of the type from spec file.
		string spec_def - reconstruction of type definition from spec file.
		jsonschema json_schema - JSON schema of this type.
		string parsing_structure - json document describing parsing structure of type
			in spec file including involved sub-types.
		list<spec_version> module_vers - versions of spec-files containing
			given type version.
		list<spec_version> released_module_vers - versions of released spec-files
			containing given type version.
		list<type_string> type_vers - all versions of type with given type name.
		list<type_string> released_type_vers - all released versions of type with
			given type name.
		list<func_string> using_func_defs - list of functions (with versions)
			referring to this type version.
		list<type_string> using_type_defs - list of types (with versions)
			referring to this type version.
		list<type_string> used_type_defs - list of types (with versions)
			referred from this type version.
	*/
	typedef structure {
		type_string type_def;
		string description;
		string spec_def;
		jsonschema json_schema;
		string parsing_structure;
		list<spec_version> module_vers;
		list<spec_version> released_module_vers;
		list<type_string> type_vers;
		list<type_string> released_type_vers;
		list<func_string> using_func_defs;
		list<type_string> using_type_defs;
		list<type_string> used_type_defs;
	} TypeInfo;

	funcdef get_type_info(type_string type) returns(TypeInfo info)
		authentication optional;

	funcdef get_all_type_info(modulename mod) returns(list<TypeInfo>)
		authentication optional;

	/* DEPRECATED
		@deprecated
	*/
	typedef structure {
		func_string func_def;
		string description;
		string spec_def;
		string parsing_structure;
		list<spec_version> module_vers;
		list<spec_version> released_module_vers;
		list<func_string> func_vers;
		list<func_string> released_func_vers;
		list<type_string> used_type_defs;
	} FuncInfo;

	/* @deprecated */
	funcdef get_func_info(func_string func) returns(FuncInfo info)
		authentication optional;

	/* @deprecated */
	funcdef get_all_func_info(modulename mod) returns(list<FuncInfo> info)
		authentication optional;

	/* Parameters for the grant_module_ownership function.

		Required arguments:
		modulename mod - the module to modify.
		username new_owner - the user to add to the module's list of
			owners.

		Optional arguments:
		boolean with_grant_option - true to allow the user to add owners
			to the module.
	*/
	typedef structure {
		modulename mod;
		username new_owner;
		boolean with_grant_option;
	} GrantModuleOwnershipParams;

	/* Grant ownership of a module. You must have grant ability on the
		module.
	*/
	funcdef grant_module_ownership(GrantModuleOwnershipParams params)
		returns() authentication required;

	/* Parameters for the remove_module_ownership function.

		Required arguments:
		modulename mod - the module to modify.
		username old_owner - the user to remove from the module's list of
			owners.
	*/
	typedef structure {
		modulename mod;
		username old_owner;
	} RemoveModuleOwnershipParams;

	/* Remove ownership from a current owner. You must have the grant ability
		on the module.
	*/
	funcdef remove_module_ownership(RemoveModuleOwnershipParams params)
		returns() authentication required;

	/* Parameters for list_all_types function.

		Optional arguments:
		boolean with_empty_modules - include empty module names, optional flag,
			default value is false.
	*/
	typedef structure {
		boolean with_empty_modules;
	} ListAllTypesParams;

	/* List all released types with released version from all modules. Return
		mapping from module name to mapping from type name to released type
		version.
	*/
	funcdef list_all_types(ListAllTypesParams params)
		returns (mapping<modulename, mapping<typename, typever>>)
		authentication optional;

	/* The administration interface. */
	funcdef administer(UnspecifiedObject command)
		returns(UnspecifiedObject response) authentication required;
};

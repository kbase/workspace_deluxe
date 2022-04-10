# Improved data citations in workspace provenance

## Background

The KBase Workspace service supports adding information about external data used in the creation
of a workspace data object to that object's provenance data structure at the time the object
is saved. After saving, the object and its provenance are immutable.

The KBase data citation effort would like to expand the fields available in the external data
data structure to support data citations and add an ability to add more external data information
to an object post save.

## Document purpose

This document provides options for improvements to the workspace external data records to
support data citations and post creation updates.

## Author

Gavin Price, 2022/04/09

## Nomenclature

NMDC - The [National Microbiome Data Collaborative](https://microbiomedata.org/)

EDU - The external data unit data structure in the KBase Workspace Service object provenance
    data structure
    
UPA - The Unique Permanent Address of a data object in KBase

## Resources

[NMDC citation fields](https://microbiomedata.org/nmdc-data-use-policy/)

[Workspace API](https://ci.kbase.us/services/ws/docs/Workspace.html)

[External data unit data structure in the workspace API](https://ci.kbase.us/services/ws/docs/Workspace.html#typedefWorkspace.ExternalDataUnit)

## Notes

* The workspace provenance data structure is limited to 1MB of information per object.
* Work estimates assume that the engineer is familiar with the workspace database and
  requires no learning curve. They take normal meetings and other standard distractions into
  account, but not rare distractions such as devPOC duty, etc.

## Proposal stage 1 - add new fields to the Workspace EDU data structure

The intent is to be able to support NMDC citations (and presumably other citation sources)
in the external data unit structure of the workspace. Mapping NMDC fields to the current EDU
structure results in

**Author** -> new field  
**Public Release Date** -> `resource_release_[date|epoch]`  
**Title** -> new field, since the `data_id` and `description` fields have different semantics  
**Version ID** -> `resource_version`  
**Repository** -> `resource_name`  
**Resolvable Persistent Identifier(s)** -> related to `data_url`, but needs to be a list among
other issues, so new field  
**Access Date** -> new field

Most new fields are simple to add:  
**Title** -> `title`, an arbitrary string  
**Resolvable Persistent Identifiers(s)** -> `resolvable_persistent_identifiers`, a list of
arbitrary strings. No validation is possible here as it's not given that any particular
identifier is resolvable via a network query [1].  
**Access Date** -> `access_epoch`, the access date in Unix epoch milliseconds.

**Author**, however, is much more difficult. Human names are
[notoriously difficult](https://www.kalzumeus.com/2010/06/17/falsehoods-programmers-believe-about-names/)
to represent - most humans have two names, but some have one, some have many, and legally a human
can change their name to [almost anything](https://en.wikipedia.org/wiki/Prince_(musician)).
As such, the standard convention of two name fields can't
accommodate all human names. Furthermore, human names are not necessarily unique.

One approach for representing names, as taken in the
[KBase auth2 service](https://github.com/kbase/auth2), is to represent names by a single
completely arbitrary string, which can handle most cases. We use the same approach here.

Note that for all the cases below, the the EDUs in the `MongoDB` database are currently stored in
a list embedded in a list of provenance actions, which means they are unindexable. No features in
this proposal require indexing so this case is not addressed.

1. Another option is making the identifier entries a structure instead of an arbitrary string.
   Whether the identifier is resolvable by a network query could then be added as a field,
   and the workspace could validate the identifier. This seems generally unnecessary, however -
   it seems reasonable for the code saving the object to validate the identifier when necessary.

### Author option 1 - arbitrary strings

In this case the `authors` field is a list of arbitrary strings which will be populated
verbatim from the data source.

Pros:

* Simple implementation

Cons:

* Names don't uniquely identify people
* Names can change over time, leaving outdated records in the database
* The same names can be represented differently, meaning the database could have different
  records that refer to the same person
* No possibility of adding new fields in the future
  
Estimated work: Conservatively 1 week

### Author option 2 - structure

In this case the `authors` field is a list of structures with the fields

* `name` - an arbitrary string
* `kbaseid` - the person's KBase ID, if any. Any entries will be validated as existing users.
* `orcid` - the persons's OrcID, if any. See validations options below.

Pros:

* Relatively simple implementation
* Allows for more information about the person, including theoretically unique IDs (although
  a person can have many KBase accounts. A person *should* only have one OrcID but it's not clear
  if that constraint is enforced or enforceable).
* Allows for adding more fields in the future to add more information about people in data
  citations.
  
Cons:

* In many cases only the person's name will be available
* Names don't uniquely identify people
* Names can change over time, leaving outdated records in the database
* The same names can be represented differently, meaning the database could have different
  records that refer to the same person
* The name could be entered differently for the same KBase or OrcID in different records
* If a person creates a KBase ID or OrcID after data creation, that will not be reflected in
  the database records

Estimated work: 1 week

Further options:

* Validate OrcIDs
  * Needs further research but probably work estimate + 1 week
* If a KBase ID is present, populate the name and OrcID (if present) from the Auth2 server
  * The workspace will need access to an Auth2 Administrator account
  * Work estimate + 1 week
  * Note that the OrcID in the Auth2 server can change or be removed for a specific user over time,
    hence storing them permanently with the user record at the time of creation

### Names option 3 - central DB with unique ID in structure

In this case the `authors` field is a list of IDs, each of which uniquely identifies a person
in the world. Each IDs refers to a record in a database which contains the user's name,
optionally a KBase ID, and optionally an OrcID. Other fields may be present as needed.

This option requires the discovery or creation of a database of humans, uniquely
identified with no duplication, with the fields we need. The database must be updateable by
anyone who wishes to add EDUs to an object, and must ensure that duplicate records are not
added for the same person.

Pros:

* Each ID uniquely identifies a single person in the world. Workspace provenance records
  will not contain multiple different records referring to the same person.
* As a user's information changes over time, those changes can be reflected in views of the
  EDU data by pulling the information from the central database, rather than storing it in the
  records.
* Allows for adding more fields in the future to add more information about people in data
  citations.

Cons:

* Extremely difficult implementation. It is not clear if there is any way to prevent duplicate
  records for the same person from being added to the database.
* Privacy is a concern, as we are creating a database, presumably separate from the workspace,
  which will need access control.
  
Estimated work: Unable to estimate without extensive research into uniquely identifying
people globally and avoiding duplicate entries in a database

## Proposal stage 2 - adding new EDUs to an existing object

Currently once an object is saved it is impossible to update the provenance, including adding
EDUs. Ideally all EDU information is known at the time of object creation, but that is not always
the case.

Add new APIs to add and view EDUs after objects have been saved. These post-save EDUs
will be stored in a different `MongoDB` collection, indexed by object UPA, to keep them separate
from the EDUs present at save time, thus maintaining reproducibility of results.

A new field, `added`, will be added to the EDU structure that contains the Unix epoch timestamp,
in milliseconds, when the EDU was added to the object post-save.

A Kafka event will be sent any time an EDU is added to an object.

There are at least two implementation options:

1. Set a maximum number of EDUs that can be added to any particular object post-creation.
  We propose 1000 here. In this case, all the EDUs can be stored in a single `MongoDB` document
  with plenty of room (~15KB each for 1000) for each EDU.
  * When the maximum number of EDUs has been added for a particular object, any further attempts
    to add an EDU fail.
  * Add a new API to add EDUs to objects after save.
  * Add a toggle to the ``get_objects2`` method to include all post-save EDUs, if present, in
    the returned data structure.
  * Since ``get_objects2`` can return 10K objects and this design allows 15MB of EDUs per object
    when serialized, that means up to 150GB of JSON EDU data could be returned in one call, which
    is clearly excessive. Worse, deserialized JSON data is often 5-20x larger in memory.
    * Store the sizes of the EDU objects and check sizes before returning the data. If the total
      size is more than some limit (100MB?) throw an error. 
    * If we wish to reduce memory usage pull the EDUs in batches and serialize, then discard
      the object data (is there a way to pull the raw BSON from Mongo rather than returning
      deserialized data?).
  * Work estimate: 3 weeks
2. Allow any number of EDUs to be added to any particular object.
  * In this case, each EDU will get its own `MongoDB` document.
  * Add a new API to add EDUs to objects after save.
  * Add a new API to list, sort, filter, and paginate EDUs.
  * Work estimate: 6 weeks

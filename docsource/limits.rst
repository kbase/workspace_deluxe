.. _limits:

Workspace limits
================

This document provides a list of limits of the WSS.

Limits
------

====================================================    =======
Parameter                                               Limit
====================================================    =======
Maximum RPC call size                                   1.005GB
Maximum object size                                     1GB
Maximum total size of returned objects                  1GB
Maximum provenance size                                 1MB
Maximum user metadata size                              16000B
Maximum total size of user metadata key / value pair    900B
Maximum total size of autometadata key / value pair     900B
Maximum memory use for sorting objects                  200MB
Maximum object_infos returned by list_objects           10000
Maximum workspace references per save                   100000
====================================================    =======

.. _sorting_notes:

Notes on sorting
----------------

The workspace service sorts the contents of all objects before MD5
calculations, serialization, and storage.

When sorting objects, object mapping and structure keys in a single path from
the object root to a single object leaf are stored in memory at one time. The
memory limit applies to these keys plus the memory required for the object
itself.

Objects > 100MB in size are dumped to disk, so the maximum memory allowed for
keys is 200MB. Objects < 100MB are kept in memory, so the maximum memory
allowed is 200MB - object size.

Thus, objects may violate this limit if 1) they have very large maps,
2) have many very large keys in the same map, or
3) have very deeply nested maps (which probably still need to be fairly large).

As a point of reference, sorting a 550MB Network object required only ~10MB of
memory for keys.

Notes on workspace references
-----------------------------

The workspace service supports a maximum of 100,000 object references (e.g.
a reference specified by @id ws in a typespec) per saveObjects() call. The
references may be in a single object, or spread across many objects.

References that are duplicated in a single object only count once towards this
limit.
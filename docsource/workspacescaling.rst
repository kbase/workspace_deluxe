.. _workspacescaling:

Workspace scaling
=================

When written, and to this day, the type database portion of the workspace incorporated
application level read and write locks. This means that only one instance of the workspace
can safely read and write from the type database at once, or data corruption may result. In
turn, this means that the workspace is a single point of failure and cannot be horizontally scaled.

Workspace version 0.13.2 introduced type delegation as a first step towards resolving this issue.
With type delegation, one workspace can be set as the central type handler while all other
workspaces delegate type operations to that workspace. The single workspace handles all writes
and reads to the type database; since the load on the type database is extremely low compared
to the workspace proper, this allows scaling the high load portion of the work the Workspace
handles. It also means that only the the type handling Workspace becomes a single point of
failure - many Workspace instances can be deployed behind a load balancer to provide redundancy
in case one fails.

In the future we plan to split the type database from the workspace entirely to simplify setup
and reduce the possibility of incorrect configuration of the workspaces in the set.

Acronyms
--------

* TWS - the type Workspace service. The single workspace that handles reading and writing
  type information from the type database.
* DWS - delegating Workspace services. All other workspace services in the set. They must all
  delegate type operations to the TWS.

Set up
------

.. warning:: It is extremely important to be careful when setting up a workspace delegation set as
   incorrect setup can result in corrupt type information or infinite delegation loops.

Set up instructions:

* All workspaces in the delegation set should have the same parameter settings in the deployment
  configuration except for ``temp-dir`` and as discussed below. Differing configurations can lead
  to many problems including data corruption, failing admin methods, etc.
* For the TWS, the ``mongodb-type-database`` parameter should be set in the deploy configuration
  file. See :ref:`configurationparameters` for details. **No other** workspace in the set may
  have this parameter populated - if that happens, type data corruption may result. Similarly,
  workspaces not in the set should not use the same type database.
* All DWS in the set must have the ``type-delegation-target`` parameter set in the configuration
  file, and the url must point to the TWS. Incorrectly setting the url will cause the DWS not
  to start, or worse, create an infinite delegation loop.

Deployment tips and notes
-------------------------

* Although all the workspaces in the delegation set can be exposed to users, it may be wise
  to prevent contact to the TWS by any systems other than the DWS. This keeps the overall load
  on the TWS low and reduces risk of a single point failure.
* The DWS do not cache results from any type related service methods (such as ``get_module_info``)
  or admin methods (such as ``approveModRequest``).
* The DWS cache type JSONSchemas used for type checking objects in the ``save_objects`` method.
  Up to 1MB of JSONSchemas are cached indefinitely with less frequently used schemas
  expiring from the cache when it is full. Mappings from non-absolute types (e.g. ``Module.Type``
  or ``Module.Type-2``) to absolute types (e.g. ``Module.Type-2.1``) are cached for 5 minutes
  and infrequently used mappings are expired when the cache reaches its 100MB limit.
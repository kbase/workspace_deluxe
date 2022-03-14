Upgrading the workspace
=======================

Some workspace version upgrades are not strictly drop in replacements and require action from
the service admins. This document describes versions that need special treatment and what actions
are required.

Prerequisites
-------------

* A working knowledge of the workspace service start up and shut down methods
* A working knowledge of MongoDB, in particular creating indexes, viewing documents,
  and updating documents via the CLI.

Notes on index creation
-----------------------

The Workspace will build any new indexes in the foreground prior to starting to avoid possible
data corruption and / or table scans. This startup time can be avoided by building the indexes
in the background while the prior version of the service is running. However, indexes that must
be altered - e.g. dropped and recreated - must be handled while all workspace services are down
and no database writes are occurring.

Upgrading to version 0.1.0
--------------------------

* Upgrading to version 0.1.0 from an earlier version is not supported.


Upgrading to version 0.2.0
--------------------------

* The ``temp-dir`` parameter in the ``deploy.cfg`` file must be set.

Upgrading to version 0.5.0
--------------------------

* Handle Service version ``b9de6991b851e9cd8fa9b5012db565f051e0894f+`` is now required.
* Handle Manager version ``3e60998fc22bb331e51b189ae1b71ebd54e58b90+`` is now required.
* Shock version ``0.9.6+`` is now required.

Upgrading to version 0.8.0
--------------------------

* Java 8 is now required.
* A Dockerfile was added, using Tomcat rather than Glassfish as the application server.
* Several index changes are required.
    * New indexes (build in the background on the prior version):
        * ``db.workspaceObjVersions.createIndex({savedby: 1}, {background: true})``
        * ``db.workspaceObjVersions.createIndex({ws: 1, id: 1, ver: -1}, {unique: true, background: true})``
    * Replaced indexes (shutdown all workspace servers)
        * ``db.workspaces.dropIndex({name: 1})``
        * ``db.workspaces.createIndex({name: 1}, {sparse: true})``

Upgrading to version 0.9.0
--------------------------

* The configuration that was previously stored in the workspace MongoDB database has been moved
  to the ``deploy.cfg`` file. The new configuration parameters are ``mongodb-type-database``,
  ``backend-type``, ``backend-url``, and ``backend-user``. Correct values for these parameters
  can be determined for existing installations by examining the contents of the ``settings``
  collection in the MongoDB workspace database (although note that ``shock`` and ``gridFS``
  are now capitalized as ``Shock`` and ``GridFS``).

.. warning:: Setting these values incorrectly can cause unexpected and undesired behavior,
   including data corruption.

* Due to the configuration changes, the initialize.py script is no longer needed and has been
  removed.
* The ``globus-url`` configuration parameter has been replaced by the ``auth2-service-url``
  parameter.

Upgrading to version 0.11.0
---------------------------

* The workspace is no longer compatible with the old Perl-based handle service, and must use
  https://github.com/kbase/handle_service2.

.. _upgrade0.12:

Upgrading to version 0.12.0
---------------------------

Version 0.12.0 requires a database schema upgrade and is therefore more involved than prior
upgrades.

Prior to attempting an upgrade note that:

* MongoDB 3.6+ is required.
* Shock is no longer supported as a data backend, and data must be transferred to an S3 compatible
  instance. This is a complicated process - please contact KBase for help.
* The Sample service is no longer supported as a KBase dynamic service, only as a core service.
  As such, the ``sample-service-tag`` configuration key has been removed.
* The ``SearchPrototypeEventHandlerFactory`` listener has been removed. It must be deactivated
  or removed from the configuration.
* The MongoDB driver API has been updated from the 2.X driver legacy interface to
  the new interface introduced in the 3.0 driver. This change is invisible other than the
  ``sorted`` key in ``GridFS`` file documents has been moved to the new ``metadata``
  field from the root of the document. Since the ``GridFS`` backend is not recommended for
  production use and no known productions installations exist, a schema updater for the GridFS
  backend is not provided.
  
Updating the schema
^^^^^^^^^^^^^^^^^^^

The schema upgrade has several steps.

* While the prior workspace service version is running, add new indexes:
    * ``db.workspaceObjVersions.createIndex({tyname: 1, tymaj: 1, tymin: 1, ws: 1, id: 1, ver: -1}, {background: true})``
    * ``db.workspaceObjVersions.createIndex({tyname: 1, tymaj: 1, ws: 1, id: 1, ver: -1}, {background: true})``
    * ``db.workspaceObjVersions.createIndex({tyname: 1, ws: 1, id: 1, ver: -1}, {background: true})``
    * The updater script (below) will create these indexes in the foreground if they do not already
      exist.

* Create the updater script:
    * This requires the standard Workspace compilation dependencies such as ``ant`` and the
      ``JDK``.
    * You may wish to leave the production installation alone and create a new 0.12.0 repo
      just for the upgrade, and then update the production installation when ready to
      switch to 0.12.0.
    * In the 0.12.0 workspace repo, run ``ant script``.

* While the prior workspace service version is running, run the updater script:
    * ``update_workspace_database_schema <path to deploy.cfg file for the service>``
    * The script is non-destructive and will pick up where it left off if interrupted.
    * The script can be run multiple times against the database to 'top off' the changes prior
      to shutting down the workspace, minimizing downtime.

* Shutdown the workspace and finish the ugrade.
    * No writes may occur to the database while the upgrade is in progress.
    * Run the updater script as above with the ``--complete`` option.

.. warning::
   If database writes occur at this point, all database records may not be updated. This
   may cause errors or incorrect query results. 

.. warning::
   Once the upgrade is complete, versions of the Workspace prior to 0.12.0 will no longer run
   against the upgraded database.
    
* Start up the 0.12.0 workspace.

* Optional: if it is clear that a rollback to an earlier workspace version is not required,
  the old type index can be deleted. Do not perform this action until **after** the update is
  complete, as the updater depends on the index and will recreate it if it's missing.

    * ``db.workspaceObjVersions.dropIndex({type: 1, chksum: 1})``
    
Rolling back to an earlier version
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

If a rollback to an earlier version of the workspace is required:

* Recreate the old type index (in the background) if it was deleted.
* Shut down the 0.12.0 workspace.
* The single document in the ``config`` collection has a ``schemaver`` key with a value of
  ``2``. Update that value to ``1``.
* Start the older version of the workspace.

Upgrading to version 0.13.0
---------------------------

* If used, Sample Service 0.1.1 is now required.

Build, Configure, and Deploy
============================

These instructions assume the reader is familiar with the process of deploying
a KBase module, including the `runtime <https://github.com/kbase/bootstrap>`_
and `dev_container <https://github.com/kbase/dev_container>`_, and has access to
a system with the KBase runtime installed. These instructions are based on the
``kbase-image-v26`` runtime image.

Unlike many modules the WSS can be built and tested outside the
``dev_container``, but the ``dev_container`` is required to build and test the
scripts. These instructions are for deploying the server and so do not
address the scripts. Building outside the ``dev_container`` means the Makefile
uses several default values for deployment - if you wish to use other values
deploy from the ``dev_container`` as usual.

Building the Workspace Service
------------------------------

First checkout the ``dev_container``::

    /kb$ sudo git clone https://github.com/kbase/dev_container
    Cloning into 'dev_container'...
    remote: Counting objects: 1097, done.
    remote: Total 1097 (delta 0), reused 0 (delta 0), pack-reused 1097
    Receiving objects: 100% (1097/1097), 138.81 KiB, done.
    Resolving deltas: 100% (661/661), done.

.. note::
   In the v26 image, ``/kb`` is owned by ``root``. As an alternative to
   repetitive ``sudo`` s, ``chown`` ``/kb`` to the user.

Bootstrap and source the user environment file, which sets up Java and Perl
paths which the WSS build needs::

    /kb$ cd dev_container/
    /kb/dev_container$ sudo ./bootstrap /kb/runtime/
    /kb/dev_container$ source user-env.sh
    
Now the WSS may be built. If building inside the ``dev_container`` all the
dependencies from the ``DEPENDENCIES`` file are required, but to build outside
the ``dev_container``, only the ``jars`` and ``workspace_deluxe`` repos are
necessary::

    ~$ mkdir kb
    ~$ cd kb
    ~/kb$ git clone https://github.com/kbase/workspace_deluxe
    Cloning into 'workspace_deluxe'...
    remote: Counting objects: 21961, done.
    remote: Compressing objects: 100% (40/40), done.
    remote: Total 21961 (delta 20), reused 0 (delta 0), pack-reused 21921
    Receiving objects: 100% (21961/21961), 21.42 MiB | 16.27 MiB/s, done.
    Resolving deltas: 100% (13979/13979), done.

    ~/kb$ git clone https://github.com/kbase/jars
    Cloning into 'jars'...
    remote: Counting objects: 1466, done.
    remote: Total 1466 (delta 0), reused 0 (delta 0), pack-reused 1466
    Receiving objects: 100% (1466/1466), 59.43 MiB | 21.49 MiB/s, done.
    Resolving deltas: 100% (626/626), done.

    ~/kb$ cd workspace_deluxe/
    ~/kb/workspace_deluxe$ make
    *snip*
    
``make`` will build:

* A workspace client jar in ``/dist/client``
* A workspace server jar in ``/dist``
* This documentation in ``/docs``

.. note::
   If the build fails due to a sphinx error, sphinx may require an upgrade to
   >= 1.3::
   
       $ sudo pip install sphinx --upgrade

Service Dependencies
--------------------

The WSS requires `MongoDB <https://mongodb.org>`_ 2.4+ to run. The workspace
may optionally use:

* `Shock <https://github.com/kbase/shock_service>`_ 0.9.6+ 
  (`d590033 <https://github.com/kbase/shock_service/commit/d59003359b63ff1a0829126c608251c8beef502b>`_ +)
  as a file storage backend.
* The `Handle Service <https://github.com/kbase/handle_service>`_ 
  `41df0fa <https://github.com/kbase/handle_service/commit/41df0fa9c4eef2cc1899b3af7477b264c7920393>`_ +
  and `Handle Manager <https://github.com/kbase/handle_mngr>`_ 
  `81297d5 <https://github.com/kbase/handle_mngr/commit/81297d52e8ef9467d5f1f86329bd85d8b3758952>`_ +
  to allow linking workspace objects to Shock nodes (see
  :ref:`shockintegration`).
  
Please see the respective service documentation to set up and run the services
required.

.. note::
   The alternative to Shock as a file storage backend is MongoDB GridFS.
   GridFS is simpler to set up, but locks the entire database when writing
   files. Since the workspace can consume very large files, this can cause a
   significant impact on other database operations. 

Configuration
-------------

There are two sources of configuration data for the WSS. The first is contained
in the ``deploy.cfg`` file in the repository root (see
:ref:`configurationparameters`). These parameters may change from invocation to
invocation of the workspace service. The second is contained in the workspace
MongoDB database itself and is set once by the configuration script (see
:ref:`configurationscript`).

.. _configurationparameters:

Configuration Parameters
^^^^^^^^^^^^^^^^^^^^^^^^

mongodb-host
""""""""""""
**Required**: Yes

**Description**: Host and port of the MongoDB server, eg. localhost:27017

mongodb-database
""""""""""""""""
**Required**: Yes

**Description**: Name of the workspace MongoDB database

mongodb-user
""""""""""""
**Required**: If the MongoDB instance requires authorization

**Description**: Username for an account with readWrite access to the MongoDB
database

mongodb-pwd
"""""""""""
**Required**: If the MongoDB instance requires authorization

**Description**: Password for an account with readWrite access to the MongoDB
database

kbase-admin-user
""""""""""""""""
**Required**: Yes

**Description**: Username for an administrator of the Globus kbase_users group

kbase-admin-pwd
"""""""""""""""
**Required**: Yes

**Description**: Password for an administrator of the Globus kbase_users group

ignore-handle-service
"""""""""""""""""""""
**Required**: If not using handles

**Description**: Set to anything (``true`` is good) to not use handles.
Delete or leave blank to use handles (the default).

handle-service-url
""""""""""""""""""
**Required**: If using handles

**Description**: The URL of the Handle Service

handle-manager-url
""""""""""""""""""
**Required**: If using handles

**Description**: The URL of the Handle Manager

handle-manager-user
"""""""""""""""""""
**Required**: If using handles

**Description**: Username for the account approved for Handle Manager use

handle-manager-pwd
""""""""""""""""""
**Required**: If using handles

**Description**: Password for the account approved for Handle Manger use

ws-admin
""""""""
**Required**: No

**Description**: the user name for a workspace administrator. This name, unlike
names added via the ``administer`` API call, is not permanently stored in the
database and thus the administrator will change if this name is changed and the
server restarted. This administrator cannot be removed by the ``administer``
API call.

backend-secret
""""""""""""""
**Required**: If using shock as the file backend

**Description**: Password for the file backend user account used by the WSS
to communicate with the backend. The user name is set in configuration script.

port
""""
**Required**: Yes

**Description**: The port on which the service will listen

server-threads
""""""""""""""
**Required**: Yes

**Description**: See :ref:`serverthreads`

min-memory
""""""""""
**Required**: Yes

**Description**: See :ref:`minmaxmemory`

max-memory
""""""""""
**Required**: Yes

**Description**: See :ref:`minmaxmemory`

temp-dir
""""""""
**Required**: Yes

**Description**: See :ref:`tempdir`

mongodb-retry
"""""""""""""
**Required**: No

**Description**: Startup MongoDB reconnect retry count. The workspace will try
to reconnect 1/s until this limit has been reached. This is useful for starting
the Workspace automatically after a server restart, as MongoDB can take quite a
while to get from start to accepting connections. The default is no retries.

dont_trust_x_ip_headers
"""""""""""""""""""""""
**Required**: No

**Description**: When ``true``, the server ignores the ``X-Forwarded-For`` and
``X-Real-IP`` headers. Otherwise (the default behavior), the logged IP address
for a request, in order of precedence, is 1) the first address in
``X-Forwarded-For``, 2) ``X-Real-IP``, and 3) the address of the client.

.. _configurationscript:

Configuration Script
^^^^^^^^^^^^^^^^^^^^
.. todo::
   startup script example

Deploy and start the server
---------------------------

To avoid various issues when deploying, ``chown`` the deployment directory
to the user. Alternatively, chown ``/kb/`` to the user, or deploy as root.
::

    ~/kb/workspace_deluxe$ sudo mkdir /kb/deployment
    ~/kb/workspace_deluxe$ sudo chown ubuntu /kb/deployment
    ~/kb/workspace_deluxe$ make deploy
    *snip*
    Makefile:53: Warning! Running outside the dev_container - scripts will not be deployed or tested.

.. todo::
   start the server instrcutions, plus logging locations


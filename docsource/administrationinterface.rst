Administration interface
========================

This document describes the administration functions available via the
``administer`` API call. All administration calls, including running
standard workspace operations like ``create_workspace``, go through
``administer`` to avoid accidental use of administrative powers when calling
the API or using scripts (similar to ``sudo``).

First initialize a workspace client with administrator credentials::

    from biokbase.workspace.client import Workspace
    wsadmin = Workspace('https://kbase.us/services/ws', token=[token])

.. note::
   These examples use the Python client, but translating the commands to
   other languages is trivial.

Getting type delegation information
-----------------------------------

Getting the url of the workspace service to which the current workspace service is delegating
type operations::

    wsadmin.administer({'command': 'getTypeDelegationTarget'})
    {'delegateTarget', 'https://kbase.us/services/ws_for_types'}

See :ref:`workspacescaling` for more information.

Managing administrators
-----------------------

Adding an administrator::

    wsadmin.administer({'command': 'addAdmin', 'user': 'lolcats'})

Listing administrators::

    wsadmin.administer({'command': 'listAdmins'})
    [u'lolcats', u'superadminman']

Removing administrators::

    wsadmin.administer({'command': 'removeAdmin', 'user': 'lolcats'})
    
.. note::
   The administrator specified in the ``deploy.cfg`` file cannot be removed by
   this method. See :ref:`configurationparameters`.

Managing module ownership requests
----------------------------------

See :ref:`typedobjectregandver`.

List module ownership requests::

    wsadmin.administer({'command': 'listModRequests'})
    [{u'moduleName': u'KBaseLolCats',
      u'ownerUserId': u'jkbaumohl',
      u'withChangeOwnersPrivilege': True}]

Accept module ownership request::
    
    wsadmin.administer({'command': 'approveModRequest', 'module': 'KBaseLolCats'})
    
Reject module ownership request::

    wsadmin.administer({'command': 'denyModRequest', 'module': 'KBaseLolCats'})

Managing workspaces
-------------------

Change the owner of a workspace:
   
The ``setWorkspaceOwner`` command is more complex than the commands seen so
far. It takes a map with a ``params`` key that maps to a map with the
keys:

* ``wsi`` - a ``WorkspaceIdentity`` as specified in the API specification.
  Required.
* ``new_user`` - the user who will own the workspace. Required.
* ``new_name`` - the new name of the workspace. Optional.

Example::

    wsadmin.administer(
        {'command': 'setWorkspaceOwner',
         'params': {'wsi': {'workspace': 'someuser:lolcats'},
                    'new_user': 'jkbaumohl'
                    }
         })
    [3303,
     u'jkbaumohl:lolcats',
     u'jkbaumohl',
     u'2015-12-13T00:45:06+0000',
     0,
     u'a',
     u'n',
     u'unlocked',
     {}]

Note that the workspace is automatically renamed such that the user prefix
matches the new user.

.. note::
   Only a workspace administrator can change workspace ownership.

List all workspace owners::

    wsadmin.administer({'command': 'listWorkspaceOwners'})
    [u'auser',
     u'anotheruser',
     u'yetanotheruser',
     u'jkbaumohl']

.. _dynamicconfiguration:

Managing the dynamic configuration
----------------------------------

Some configuration parameters can be changed dynamically, vs. the parameters in ``deploy.cfg``
that require a server restart to change.

Get the configuration::

    wsadmin.administer({'command': 'getConfig'})
    {'config': {'backend-file-retrieval-scaling': 1}}

Set the configuration::

    wsadmin.administer(
        {'command': 'setConfig',
         'params': {'set': {'backend-file-retrieval-scaling': 3}}
         }) 

    wsadmin.administer({'command': 'getConfig'})
    {'config': {'backend-file-retrieval-scaling': 3}}

Currently there is only one configuration parameter:

backend-file-retrieval-scaling
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

This parameter sets the parallelization factor to use when retrieving object data from file
stores like ``S3`` or ``GridFS``. Each call to any of the methods that return object data
(such as ``get_objects2``) will spawn a thread pool with up to this many threads to use for
fetching data. This parameter can be tuned to speed up getting object data while not overloading
the file backend with simultaneous requests. The value must be an integer and minimum value is 1.
For example, if the parallelization factor is 10 and 1000 objects are requested, up to 10
objects at a time will be simultaneously fetched from the backend. If set to the default value,
then each data object is fetched serially.

General workspace commands
--------------------------

The ``administer`` interface allows running normal WSS API methods while
acting as a different user (except in a few cases, see below). The commands
all have the same basic structure:

.. code-block:: python

    wsadmin.administer(
        {'command': [method name inCamelCase],
         'params':  [parameters of the method per the API specification]
         'user':    [username under which the command will run]
        })
        
The methods currently available are:

==============================  =================
Method                          ``user`` required
==============================  =================
createWorkspace                 yes
setPermissions                  no
getPermissions (DEPRECATED)     optional (1)
getPermissionsMass              no
setWorkspaceDescription         no
getWorkspaceDescription         no
getWorkspaceInfo                no (2)
getObjectInfo                   no (3)
getObjectHistory                no
getObjects                      no (4)
setGlobalPermission             yes
saveObjects                     yes
listWorkspaces                  yes
listWorkspaceIDs                yes
listObjects                     optional (5)
deleteWorkspace                 no
undeleteWorkspace               no (6)
grantModuleOwnership            no
removeModuleOwnership           no
==============================  =================

#. If omitted, returns the permissions as if the user is an administrator of the workspace.
#. The user permission is always returned as 'none'.
#. Parameters are as get_object_info3.
#. Parameters are as get_objects2.
#. If omitted, returns all objects requested.
#. Parameters are as delete_workspace.

Example usage:

.. code-block:: python

    wsadmin.administer(
        {'command': 'createWorkspace',
         'params': {'workspace': 'morelolcats',
                    'description': 'Golly, I really love lolcats.'
                    },
         'user': 'jkbaumohl'
         })
    [3304,
     u'morelolcats',
     u'jkbaumohl',
     u'2015-12-13T01:16:50+0000',
     0,
     u'a',
     u'n',
     u'unlocked',
     {}]

    wsadmin.administer(
        {'command': 'getPermissions',
         'params': {'id': 3304},
         'user': 'superadminman'
         })
    {u'superadminman': u'n'}

    wsadmin.administer(
        {'command': 'setPermissions',
         'params': {'id': 3304,
                    'new_permission': 'w',
                    'users': ['superadminman']
                    }
         })

    wsadmin.administer(
        {'command': 'getPermissions',
         'params': {'id': 3304},
         'user': 'superadminman'})
    {u'jkbaumohl': u'a', 'superadminman': u'w'}

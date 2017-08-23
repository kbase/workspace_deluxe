Administration interface
========================

This document describes the administration functions available via the
``administer`` API call. All administration calls, including running
standard workspace operations like ``create_workspace``, go through
``administer`` to avoid accidental use of administrative powers when calling
the API or using scripts (similar to ``sudo``).

First initialize a workspace client with administrator credentials::

    from biokbase.workspace.client import Workspace
    wsadmin = Workspace('https://kbase.us/services/ws', user_id=[user], password=[pwd])

.. note::
   These examples use the Python client, but translating the commands to
   other languages is trivial.

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
far. It takes a map with a ``param`` key that maps to a map with the
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
getWorkspaceInfo                no
getObjectInfo                   no (2)
getObjectHistory                no
getObjects                      no (3)
setGlobalPermission             yes
saveObjects                     yes
listWorkspaces                  yes
listWorkspaceIDs                yes
listObjects                     optional (4)
deleteWorkspace                 no
undeleteWorkspace               no
grantModuleOwnership            no
removeModuleOwnership           no
==============================  =================

#. If omitted, returns the permissions as if the user is an administrator of the workspace.
#. Parameters are as get_object_info3.
#. Parameters are as get_objects2.
#. If omitted, returns all objects requested, but at least one and no more than 1000 workspaces
   must be specified.

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

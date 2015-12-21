.. _workspaces:

Workspaces
==========

Workspaces provide a means to collect multiple typed objects (TOs) into one
container and share the container with other people. This documentation will
demonstrate some of the most common operations on workspaces (see
:ref:`apidocs` for the full API). It assumes that
a functional client is available (see :ref:`buildinitclient`). The examples
use the Python client, but translating to other clients is trivial.

.. _createworkspaces:

Creating workspaces
-------------------

Create a workspace called MyWorkspace:

.. code-block:: python

    In [4]: ws.create_workspace({'workspace': 'MyWorkspace'})
    Out[4]: 
    [12,                             # workspace numerical ID
     u'MyWorkspace',                 # workspace name
     u'kbasetest',                   # workspace creator
     u'2015-12-13T20:48:00+0000',    # modification date of the workspace
     0,                              # number of objects created in this workspace
     u'a',                           # user's permission for the workspace
     u'n',                           # global permissions for the workspace
     u'unlocked',                    # whether the workspace is locked
     {}]                             # user provided metadata
     
Once created, a workspace's numerical ID is permanent and unchangeable. A 
locked workspace cannot be altered (other than making it world-readable).

Note that the object count is the total objects ever created in this
workspace, not the currently existing objects.

.. _wsperms:

Permissions
^^^^^^^^^^^

Permissions are coded according to the following table:

==========    ================================================
Permission    Allows
==========    ================================================
n             No access
r             Read access
w             Write access, see permissions of other users
a             Admin access, set permissions of other users
==========    ================================================

A workspace can have a description and arbitrary key-value metadata
associated with it:

.. code-block:: python

    In [5]: ws.create_workspace({'workspace': 'MyOtherWorkspace',
       ...:                      'description': 'Workspace for other things',
       ...:                      'meta': {'contents': 'other things',
       ...:                               'project_id': '42'}
       ...:                      })
    Out[5]: 
    [13,
     u'MyOtherWorkspace',
     u'kbasetest',
     u'2015-12-13T20:51:57+0000',
     0,
     u'a',
     u'n',
     u'unlocked',
     {u'contents': u'other things', u'project_id': u'42'}]
     
Retrieving information about workspaces
---------------------------------------
     
The workspace description and information list can be retrieved:

.. code-block:: python

    In [6]: ws.get_workspace_description({'id': 13}) # retrieving by ID
    Out[6]: u'Workspace for other things'

    In [11]: ws.get_workspace_info({'workspace': 'MyOtherWorkspace'})
    Out[11]: 
    [13,
     u'MyOtherWorkspace',
     u'kbasetest',
     u'2015-12-13T20:51:57+0000',
     0,
     u'a',
     u'n',
     u'unlocked',
     {u'contents': u'other things', u'project_id': u'42'}]

Listing workspaces
------------------

Workspaces with at least read access can be listed:

.. code-block:: python

    In [8]: ws.list_workspace_info({})
    Out[8]: 
     [12,
      u'MyWorkspace',
      u'kbasetest',
      u'2015-12-13T20:48:00+0000',
      0,
      u'a',
      u'n',
      u'unlocked',
      {}],
     [13,
      u'MyOtherWorkspace',
      u'kbasetest',
      u'2015-12-13T20:51:57+0000',
      0,
      u'a',
      u'n',
      u'unlocked',
      {u'contents': u'other things', u'project_id': u'42'}]]

The list can be filtered in several ways. Here it's filtered by the user
provided metadata:

.. code-block:: python

    In [10]: ws.list_workspace_info({'meta': {'project_id': '42'}})
    Out[10]: 
    [[13,
      u'MyOtherWorkspace',
      u'kbasetest',
      u'2015-12-13T20:51:57+0000',
      0,
      u'a',
      u'n',
      u'unlocked',
      {u'contents': u'other things', u'project_id': u'42'}]
      
Sharing workspaces
------------------

Users with admin privileges with to a workspace can allow other users
to read, write to, and administrate the workspace. These privileges apply
to all objects contained in the workspace. 

.. code-block:: python

    In [12]: ws.set_permissions({'workspace': 'MyWorkspace',
                                 'users': ['kbasetest2'],
                                 'new_permission': 'a'
                                 })

    In [13]: ws.set_permissions({'workspace': 'MyWorkspace',
                                 'users': ['kbasetest8'],
                                 'new_permission': 'r'
                                 })

    In [16]: ws.get_permissions_mass([{'id': 12},
                                      {'workspace': 'MyOtherWorkspace'}
                                      ])
    Out[16]:
    [{u'kbasetest': u'a', u'kbasetest2': u'a', u'kbasetest8': u'r'},
     {u'kbasetest': u'a'}]

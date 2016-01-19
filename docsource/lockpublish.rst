Locking and publishing workspaces
=================================

A workspace administrator may lock a workspace, preventing (most) further
changes. If a locked workspace is globally readable, it is considered published
- a user might wish to publish a workspace that contains supplemental 
information for a publication, for example, so that the publication editors can
see that the information is permanently recorded.

.. warning::
   Once a workspace is locked, it can never be unlocked, even by a
   server administrator.

As usual, it is assumed that a functional client is available (see
:ref:`buildinitclient`). The examples use the Python client, but translating to
other clients is trivial.

Lock a workspace (see :ref:`createworkspaces` and :ref:`saveobjects` for
information on creating workspaces and saving objects):

.. code-block:: python
    :emphasize-lines: 10

    In [8]: ws.lock_workspace({'workspace': 'MyWorkspace'})
    Out[8]: 
    [12,
     u'MyWorkspace',
     u'kbasetest',
     u'2015-12-20T01:09:49+0000',
     2,
     u'a',
     u'n',
     u'locked',
     {}]

The following methods are not allowed on locked workspaces:

* ``alter_workspace_metadata``
* ``delete_objects``
* ``undelete_objects``
* ``delete_workspace``
* ``hide_objects``
* ``unhide_objects``
* ``lock_workspace``
* ``rename_object``
* ``rename_workspace``
* ``revert_object``
* ``save_object`` (note this function is deprecated)
* ``save_objects``
* ``set_workspace_description``

``set_permissions`` does work.

Additionally ``set_global_permission`` may only be used to make the workspace
globally readable. A locked, globally readable workspace may not be made
private:

.. code-block:: python

     In [9]: ws.set_global_permission({'workspace': 'MyWorkspace',
                                       'new_permission': 'r'})
     
     In [10]: ws.set_global_permission({'workspace': 'MyWorkspace',
                                        'new_permission': 'n'})
     ---------------------------------------------------------------------------
     ServerError                               Traceback (most recent call last)
     <ipython-input-10-c700ea19406a> in <module>()
     ----> 1 ws.set_global_permission({'workspace': 'MyWorkspace',
                                       'new_permission': 'n'})
     
     *snip*
     
     ServerError: JSONRPCError: -32500. The workspace with id 12, name MyWorkspace,
      is locked and may not be modified

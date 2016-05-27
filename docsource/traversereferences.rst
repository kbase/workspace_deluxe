.. _traverseobjects:

Traversing object references
============================
   
Object to object references, whether dependency or provenance references,
not only indicate, respectively, objects that are required to compute on or
understand the origin of the referencing object, but also provide permanent
access to the referenced objects. The philosophy behind this design is that
data is useless if it is incomplete (dependency references) or has unknown
origins (provenance references). The object to object references
form a graph structure which, in the context of the workspace, allows unlimited
traversal *in the direction of the references*. Methods exist to provide
traversal capabilities in the opposite direction, but only to objects to which
the user has direct access.

As usual, it is assumed that a functional client is available (see
:ref:`buildinitclient`). The examples use the Python client, but translating to
other clients is trivial. Only the most common cases are covered - see the
:ref:`apidocs` for complete coverage.

The following examples use this new type specification:

.. code-block:: python

    In [12]: print user1client.get_module_info({'mod': 'Ref'})['spec']
    module Ref {
    
        /* @id ws */
        typedef string aref;
    
        typedef structure {
            aref ref;
        } RefType;
    };

See :ref:`saveobjects` for the ``SimpleObjects`` specification and
:ref:`saveobjectwithrefs` for details regarding saving objects with
references.

In the interest of simplicity, saving the example objects is not shown. User 1
(kbasetest2) saved two objects in their workspace, one of which contains a
reference to the other:

.. code-block:: python
    :emphasize-lines: 14, 18, 20, 30, 44

    In [28]: user1client.get_objects2({'objects':
                                       [{'ref': 'user1ws/simple'},
                                       {'ref': 'user1ws/refobj1'}
                                       ]})['data']
    Out[28]: 
    [{u'copy_source_inaccessible': 0,
      u'created': u'2015-12-18T04:13:15+0000',
      u'creator': u'kbasetest2',
      u'data': {u'a_float': 6.02e-23,
       u'a_string': u'towel',
       u'an_int': 42,
       u'array_of_maps': []},
      u'extracted_ids': {},
      u'info': [1,
       u'simple',
       u'SimpleObjects.SimpleObject-1.0',
       u'2015-12-18T04:13:15+0000',
       1,
       u'kbasetest2',
       13,
       u'user1ws',
       u'6b76d883ffa1357e52e1020594317dd7',
       70,
       {}],
      u'provenance': [],
      u'refs': []},
     {u'copy_source_inaccessible': 0,
      u'created': u'2015-12-18T04:14:33+0000',
      u'creator': u'kbasetest2',
      u'data': {u'ref': u'13/1/1'},   # points at object above
      u'extracted_ids': {},
      u'info': [2,
       u'refobj1',
       u'Ref.RefType-1.0',
       u'2015-12-18T04:14:33+0000',
       1,
       u'kbasetest2',
       13,
       u'user1ws',
       u'160cf883f216b170f5d2074652e1bf5d',
       16,
       {}],
      u'provenance': [],
      u'refs': [u'13/1/1']}]

This workspace is readable to User 2 (kbasetest8):

.. code-block:: python

    In [30]: user1client.get_permissions_mass(
                 {'workspaces': [{'workspace': 'user1ws'}]})
    Out[30]: [{u'kbasetest2': u'a', u'kbasetest8': u'r'}]

As such, User 2 saved an object that references User 1's ``refobj1``:

.. code-block:: python
    :emphasize-lines: 7, 21
    
    In [31]: user2client.get_objects2(
                 {'objects': [{'ref': 'user2ws/refobj2'}]})['data']
    Out[31]: 
    [{u'copy_source_inaccessible': 0,
      u'created': u'2015-12-18T04:16:20+0000',
      u'creator': u'kbasetest8',
      u'data': {u'ref': u'13/2/1'},
      u'extracted_ids': {},
      u'info': [1,
       u'refobj2',
       u'Ref.RefType-1.0',
       u'2015-12-18T04:16:20+0000',
       1,
       u'kbasetest8',
       14,
       u'user2ws',
       u'ad38c241c9a46bb940fb4574a343b3c5',
       16,
       {}],
      u'provenance': [],
      u'refs': [u'13/2/1']}]

If User 1 now sets ``user1ws`` to unreadable, and worse, deletes the second
object:

.. code-block:: python

    In [32]: user1client.set_permissions({'workspace': 'user1ws',
                                          'users': ['kbasetest8'],
                                          'new_permission': 'n'})
    In [34]: user1client.delete_objects([{'ref': 'user1ws/refobj1'}])
    
... as expected User 2 now cannot access the object referenced by their
``refobj2`` object, which renders it useless.

.. code-block:: python

    In [35]: user2client.get_objects2(
                 {'objects': [{'ref': 'user1ws/refobj1'}]})['data']
    --------------------------------------------------------------------------
    ServerError                               Traceback (most recent call last)
    <ipython-input-35-7c5faa02c112> in <module>()
    ----> 1 user2client.get_objects([{'ref': 'user1ws/refobj1'}])
    
    *snip*
    
    ServerError: JSONRPCError: -32500. Object refobj1 cannot be accessed: User
    kbasetest8 may not read workspace user1ws

However, using the ``get_objects2`` method and providing the path
from an accessible object to the desired object, User 2 can still retrieve
the hidden/deleted objects, and thus use ``refobj2``. The path can be deduced
from the references in each object:

.. code-block:: python
    :emphasize-lines: 7, 21, 25, 32, 46, 50-51

    In [51]: user2client.get_objects2(
                 {'objects': [{'ref': 'user2ws/refobj2'}]})['data']
    Out[51]: 
    [{u'copy_source_inaccessible': 0,
      u'created': u'2015-12-18T04:16:20+0000',
      u'creator': u'kbasetest8',
      u'data': {u'ref': u'13/2/1'},
      u'extracted_ids': {},
      u'info': [1,
       u'refobj2',
       u'Ref.RefType-1.0',
       u'2015-12-18T04:16:20+0000',
       1,
       u'kbasetest8',
       14,
       u'user2ws',
       u'ad38c241c9a46bb940fb4574a343b3c5',
       16,
       {}],
      u'provenance': [],
      u'refs': [u'13/2/1']}]
    
    In [52]: user2client.get_objects2(
                 {'objects': [{'ref': 'user2ws/refobj2',
                               'obj_path': [{'ref': '13/2/1'}]
                               }
                              ]})
    Out[52]: 
    [{u'copy_source_inaccessible': 0,
      u'created': u'2015-12-18T04:14:33+0000',
      u'creator': u'kbasetest2',
      u'data': {u'ref': u'13/1/1'},
      u'extracted_ids': {},
      u'info': [2,
       u'refobj1',
       u'Ref.RefType-1.0',
       u'2015-12-18T04:14:33+0000',
       1,
       u'kbasetest2',
       13,
       u'user1ws',
       u'160cf883f216b170f5d2074652e1bf5d',
       16,
       {}],
      u'provenance': [],
      u'refs': [u'13/1/1']}]
    
    In [53]: user2client.get_objects2(
                 {'objects': [{'ref': 'user2ws/refobj2',
                               'obj_path': [{'ref': '13/2/1'},
                                            {'ref': '13/1/1'}
                                            ]
                              ]})
    Out[53]: 
    [{u'copy_source_inaccessible': 0,
      u'created': u'2015-12-18T04:13:15+0000',
      u'creator': u'kbasetest2',
      u'data': {u'a_float': 6.02e-23,
       u'a_string': u'towel',
       u'an_int': 42,
       u'array_of_maps': []},
      u'extracted_ids': {},
      u'info': [1,
       u'simple',
       u'SimpleObjects.SimpleObject-1.0',
       u'2015-12-18T04:13:15+0000',
       1,
       u'kbasetest2',
       13,
       u'user1ws',
       u'6b76d883ffa1357e52e1020594317dd7',
       70,
       {}],
      u'provenance': [],
      u'refs': []}]

It is also possible for User 1 to find objects that reference their
objects if they are readable and not in the deleted state:

.. code-block:: python

    In [54]: user1client.undelete_objects([{'ref': '13/2'}])
    
    In [55]: user1client.list_referencing_objects([{'ref': 'user1ws/simple'}])
    Out[55]: 
    [[[2,
       u'refobj1',
       u'Ref.RefType-1.0',
       u'2015-12-18T04:14:33+0000',
       1,
       u'kbasetest2',
       13,
       u'user1ws',
       u'160cf883f216b170f5d2074652e1bf5d',
       16,
       {}]]]
   
Attempting to list User 2's object, which references ``refobj1`` and is
unreadable by User 1, is not possible:

.. code-block:: python

    In [56]: user1client.list_referencing_objects([{'ref': 'user1ws/refobj1'}])
    Out[56]: [[]]

Note that although not shown, provenance references work exactly the same way.
This example is, of course, very simple - a single object could have many
references, and those objects may also have many references, et cetera.

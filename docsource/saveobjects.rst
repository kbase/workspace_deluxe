.. _objects:

Objects
=======

This documentation describes some of the most common operations on objects
in the workspace, with a focus on saving objects (see
:ref:`apidocs` for the full API). It assumes that
a functional client is available (see :ref:`buildinitclient`). The examples
use the Python client, but translating to other clients is trivial.

.. warning::
   Objects saved to the WSS cannot contain binary data anywhere within
   their hierarchy. Binary data must be encoded (e.g. via Base64) prior to
   saving to the WSS.

.. _saveobjects:

Save an object
--------------

The object will be saved as the type ``SimpleObjects.SimpleObject-1.0``,
defined as:

.. code-block:: python

    In [4]: print ws.get_type_info('SimpleObjects.SimpleObject-1.0')['spec_def']
    /*
    @optional opt
    */
    typedef structure {
      list<mapping<string, int>> array_of_maps;
      int an_int;
      float a_float;
      string a_string;
      int opt;
    } SimpleObject;


Saving an object requires specifying either the name or id of the workspace,
and for each object the name or id of the object, the type of the object,
and the object data.

.. code-block:: python

    In [16]: obj = {'array_of_maps': [],
       ....:        'an_int': 42,
       ....:        'a_float': 6.02e-23,
       ....:        'a_string': 'towel'}

    In [18]: ws.save_objects(
                 {'workspace': 'MyWorkspace',
                  'objects': [{'name': 'simple',
                               'type': u'SimpleObjects.SimpleObject-1.0',
                               'data': obj
                               }
                              ]
                  })
    Out[18]: 
    [[1,                                    # object numerical ID
      u'simple',                            # object name
      u'SimpleObjects.SimpleObject-1.0',    # object type
      u'2015-12-14T04:11:55+0000',          # save or copy date of the object
      1,                                    # version of the object
      u'kbasetest',                         # user that saved or copied the object
      12,                                   # numerical workspace ID
      u'MyWorkspace',                       # workspace name
      u'6b76d883ffa1357e52e1020594317dd7',  # MD5 digest of the object
      70,                                   # size of the object in bytes
      {}]]                                  # user provided metadata
      
Once created, an object's numerical ID is permanent and unchangeable.

The MD5 is calculated *after* any references are translated (see below) and
the object ``structure`` and ``mapping`` keys are sorted.

Saving an object that does not match the typespec causes an error:

.. code-block:: python

    In [23]: obj['a_string'] = 42

    In [24]: ws.save_objects(
                 {'workspace': 'MyWorkspace',
                  'objects': [{'name': 'simple2',
                               'type': u'SimpleObjects.SimpleObject-1.0',
                               'data': obj
                               }
                              ]
                  })
    ---------------------------------------------------------------------------
    ServerError                               Traceback (most recent call last)
    <ipython-input-11-91a2d5e7f85e> in <module>()
          3               'objects': [{'name': 'simple2',
          4                            'type': u'SimpleObjects.SimpleObject-1.0',
    ----> 5                            'data': obj
          6                            }
          7                           ]

    *snip*

    ServerError: JSONRPCError: -32500. Object #1, simple2 failed type checking:
    instance type (integer) does not match any allowed primitive type (allowed: ["string"]), at /a_string
    *snip*

Saving an object with ``null`` s (or in Python's case ``None`` s) where an
``int``, ``float``, or ``string`` is expected is allowed:

.. code-block:: python

    In [21]: obj = {'array_of_maps': [],
                    'an_int': None,
                    'a_float': None,
                    'a_string': None}

    In [22]: ws.save_objects(
                 {'id': 12,
                  'objects': [{'name': 'nullobj',
                               'type': u'SimpleObjects.SimpleObject-1.0',
                               'data': obj 
                               }
                              ]
                  })
    Out[22]: 
    [[3,
      u'nullobj',
      u'SimpleObjects.SimpleObject-1.0',
      u'2015-12-14T22:58:55+0000',
      1,
      u'kbasetest',
      12,
      u'MyWorkspace',
      u'0eb7130429570c6fe23017091df0a654',
      65,
      {}]]


Save a new version
------------------

Providing an existing name or ID when saving an object causes the creation
of a new object version:

.. code-block:: python

    In [20]: obj = {'array_of_maps': [],
                    'an_int': 42,
                    'a_float': 6.02e-23,
                    'a_string': 'hoopty frood'}

    In [22]: ws.save_objects(
                 {'id': 12,
                  'objects': [{'objid': 1,
                               'type': u'SimpleObjects.SimpleObject-1.0',
                               'data': obj
                               }
                              ]
                  })
    Out[22]: 
    [[1,                                    # same object ID
      u'simple',                            # same name
      u'SimpleObjects.SimpleObject-1.0',
      u'2015-12-14T04:22:38+0000',
      2,                                    # new version
      u'kbasetest',
      12,
      u'MyWorkspace',
      u'8aba51168748e7a7a91847f510ce2807',  # new MD5
      77,                                   # 7 more bytes wasted
      {}]]

Save an object with metadata
----------------------------

As with workspaces, arbitrary key-value metadata can be associated with
objects:

.. code-block:: python

    In [27]: ws.save_objects(
                 {'workspace': 'MyWorkspace',
                  'objects': [{'name': 'simple3',
                               'type': u'SimpleObjects.SimpleObject-1.0',
                               'data': obj,
                               'meta': {'Eccentrica': 'Gallumbits',
                                        'Wowbagger': 'Prolonged'
                                        }
                               }
                              ]
                  })
    Out[27]: 
    [[2,
      u'simple3',
      u'SimpleObjects.SimpleObject-1.0',
      u'2015-12-14T04:43:21+0000',
      1,
      u'kbasetest',
      12,
      u'MyWorkspace',
      u'8aba51168748e7a7a91847f510ce2807',
      77,
      {u'Eccentrica': u'Gallumbits', u'Wowbagger': u'Prolonged'}]]

Save an object with provenance
------------------------------

Provenance data may be saved along with the object data as a list of 
provenance actions (PAs). Each PA represents a step taken to convert a data
unit into another - for example, passing a genome sequence to a server
which returns a metabolic model for that sequence. The PA contains
fields for recording how an object was generated. See the :ref:`apidocs` for
the full specification, but some common fields are:

===========    ================================================
Field          Description
===========    ================================================
time           The time the action took place
service        The name of the service that produced the object
service_ver    The version of the service
method         The method called on the service
description    A free text description of the action
===========    ================================================

Some fields require special explanation. The ``intermediate_incoming`` and
``intermediate_outgoing`` fields allow linking the outputs of one PA with
the inputs of the next. The list of PAs is assumed to be in the order the
actions took place, so, for example, if workspace object ``A`` was passed to
a service method as ``X.process(A)`` which produced the object tuple
``[B, C]``, and those results were passed to a service method as 
``Y.dothing(C, B)`` which produced the object ``D``, the provenance list might
look like:

.. code-block:: python

    pl = [{'service': 'X',
           'method': 'process',
           'intermediate_outgoing': ['B', 'C']
           },
          {'service': 'Y',
           'method': 'dothing',
           'intermediate_incoming': ['C', 'B']
           'method_params': ['C', 'B']
           }
          ]

``B`` and ``C``, in this example, are merely symbols that describe the ordering
of the inputs and outputs of each step and any permutations of those orders
from step to step. Any unique names could be used.

The ``input_ws_objects`` field allows specifying workspace objects that were
used in the creation of the current object and therefore are part of its
provenance. In the example above, object ``A`` is part of the provenance of
object ``D``, and should therefore be specified in ``input_ws_objects``:

.. code-block:: python

    pl = [{'service': 'X',
           'method': 'process',
           'intermediate_outgoing': ['B', 'C'],
           'input_ws_objects': ['MyWorkspace/2/2']
           },
          {'service': 'Y',
          ...

In this case, ``A`` was the 2nd version of object ID ``2`` in ``MyWorkspace``.
The name or ID of the workspace and object may be used in the reference string.
Names will always be translated to IDs by the WSS before the provenance is
saved, since IDs are permanent and names are not.

For example:

.. code-block:: python

    In [27]: ps = [{'description': 'assemble paired end reads',
                    'input_ws_objects': ['MyWorkspace/simple/1'],
                    'method': 'annotatePairedReads',
                    'method_params': [{'objname': 'simple',
                                       'workspace': 'MyWorkspace',
                                       'ver': 1
                                       }
                                      ],
                    'service': 'Annotation',
                    'service_ver': '2.1.3',
                    'time': '2015-12-15T22:58:55+0000'
                    }
                   ]

    In [30]: ws.save_objects(
                 {'workspace': 'MyWorkspace',
                  'objects': [{'name': 'simpleWithProv',
                               'type': u'SimpleObjects.SimpleObject-1.0',
                               'data': obj,
                               'provenance': ps
                               }
                              ]
                  })
    Out[30]: 
    [[4,
      u'simpleWithProv',
      u'SimpleObjects.SimpleObject-1.0',
      u'2015-12-14T23:44:35+0000',
      2,
      u'kbasetest',
      12,
      u'MyWorkspace',
      u'6b76d883ffa1357e52e1020594317dd7',
      70,
      {}]]

If the object is retrieved, it can be seen that the ``resolved_ws_objects``
field has been added to the provenance. This field contains the translated
object references supplied in ``input_ws_objects``:

.. code-block:: python
    :emphasize-lines: 25, 30

    In [32]: ws.get_objects2({'objects':
                 [{'ref': 'MyWorkspace/simpleWithProv'}]})['data']
    Out[32]: 
    [{u'copy_source_inaccessible': 0,
      u'created': u'2015-12-14T23:44:35+0000',
      u'creator': u'kbasetest',
      u'data': {u'a_float': 6.02e-23,
       u'a_string': u'towel',
       u'an_int': 42,
       u'array_of_maps': []},
      u'extracted_ids': {},
      u'info': [4,
       u'simpleWithProv',
       u'SimpleObjects.SimpleObject-1.0',
       u'2015-12-14T23:44:35+0000',
       2,
       u'kbasetest',
       12,
       u'MyWorkspace',
       u'6b76d883ffa1357e52e1020594317dd7',
       70,
       {}],
      u'provenance': [{u'description': u'assemble paired end reads',
        u'external_data': [],
        u'input_ws_objects': [u'MyWorkspace/simple/1'],
        u'method': u'annotatePairedReads',
        u'method_params': [{u'objname': u'simple',
                            u'workspace': u'MyWorkspace'
                            u'ver': 1}],
        u'resolved_ws_objects': [u'12/1/1'],
        u'service': u'Annotation',
        u'service_ver': u'2.1.3',
        u'time': u'2015-12-15T22:58:55+0000'}],
      u'refs': []}]

Saving provenance with objects is optional, but strongly encouraged.

.. warning::
   The WSS does not inherently know anything about the provenance of the
   objects it stores, and cannot evaluate the reliability or completeness of
   the provenance. It is entirely up to the user or application storing the
   objects to ensure accurate and complete provenance. Clearly the provenance
   in the examples above is fradulent.
   
.. _saveobjectwithrefs:

Save an object with dependency references
-----------------------------------------

The following types will be used to demonstrate saving objects with
dependency references:

.. code-block:: python

    In [52]: print ws.get_module_info({'mod': 'SimpleObjects'})['spec']
    module SimpleObjects {

        /* @optional opt */
        typedef structure {
            list<mapping<string, int>> array_of_maps;
            int an_int;
            float a_float;
            string a_string; 
            int opt;
        } SimpleObject;
    
        typedef structure {
            int i;
            string thing;
        } SimplerObject;
    
        /* @id ws */
        typedef string ref;
        
        /* @id ws SimpleObjects.SimplerObject */
        typedef string typedref;
        
        typedef structure {
            ref r;
            string thing;
        } RefObject;
    
        typedef structure {
            typedref r;
            string thing;
        } TypeRefObject;
    };

Saving an object with a dependency reference required by the typespec is just
like saving any other object:

.. code-block:: python
    :emphasize-lines: 1, 8, 32, 48

    In [57]: refobj = {'r': 'MyWorkspace/simple',
                       'thing': 'this object has a reference'
                       }

    In [58]: ws.save_objects(
                 {'workspace': 'MyWorkspace',
                  'objects': [{'name': 'ref',
                               'type': u'SimpleObjects.RefObject-2.0',
                               'data': refobj,
                               }
                              ]
                  })
    Out[58]: 
    [[6,
      u'ref',
      u'SimpleObjects.RefObject-2.0',
      u'2015-12-15T03:12:41+0000',
      1,
      u'kbasetest',
      12,
      u'MyWorkspace',
      u'44e0ef9dff44c4840ddf77abbfc555bd',
      52,
      {}]]

    In [59]: ws.get_objects2({'objects':
                 [{'workspace': 'MyWorkspace', 'name': 'ref'}]})['data']
    Out[59]: 
    [{u'copy_source_inaccessible': 0,
      u'created': u'2015-12-15T03:12:41+0000',
      u'creator': u'kbasetest',
      u'data': {u'r': u'12/1/2',
                u'thing': u'this object has a reference'
                },
      u'extracted_ids': {},
      u'info': [6,
       u'ref',
       u'SimpleObjects.RefObject-2.0',
       u'2015-12-15T03:12:41+0000',
       1,
       u'kbasetest',
       12,
       u'MyWorkspace',
       u'44e0ef9dff44c4840ddf77abbfc555bd',
       52,
       {}],
      u'provenance': [],
      u'refs': [u'12/1/2']}]

Note that the reference in the saved object was translated to a permanent
reference, and that the references are extracted into the ``refs`` ``list`` in
the returned data.

If the referenced object is not accessible to the user saving the object,
the save will fail. If the save succeeds, the referent will be forever
accessible to users with access to the referencing object as described
previously.

Types may specify that a reference must point to an object with a specific
type, as in the ``TypeRefObject`` type. In this case, saving with a reference
that does not point to an object with type ``SimpleObjects.SimplerObject`` will
fail:

.. code-block:: python
    :emphasize-lines: 4

    In [73]: ws.save_objects(
                 {'workspace': 'MyWorkspace',
                  'objects': [{'name': 'typedref',
                               'type': u'SimpleObjects.TypeRefObject',
                               'data': refobj,
                               }
                              ]
                  })
    ---------------------------------------------------------------------------
    ServerError                               Traceback (most recent call last)
    <ipython-input-73-80b8ab6aabd0> in <module>()
          3               'objects': [{'name': 'typedref',
          4                            'type': u'SimpleObjects.TypeRefObject',
    ----> 5                            'data': refobj,
          6                            }
          7                           ]

    *snip*

    ServerError: JSONRPCError: -32500. Object #1, typedref has invalid
    reference: The type SimpleObjects.SimpleObject-1.0 of reference
    MyWorkspace/simple in this object is not allowed - allowed types are
    [SimpleObjects.SimplerObject] at /r



List objects
------------

Listing objects is similar to listing workspaces:

.. code-block:: python

    In [74]: ws.list_objects({'ids': [12]})
    Out[74]: 
    [[2,
      u'simple3',
      u'SimpleObjects.SimpleObject-1.0',
      u'2015-12-14T22:58:01+0000',
      1,
      u'kbasetest',
      12,
      u'MyWorkspace',
      u'8aba51168748e7a7a91847f510ce2807',
      77,
      None],
     
     *snip*
     
     [4,
      u'simpleWithProv',
      u'SimpleObjects.SimpleObject-1.0',
      u'2015-12-14T23:44:35+0000',
      2,
      u'kbasetest',
      12,
      u'MyWorkspace',
      u'6b76d883ffa1357e52e1020594317dd7',
      70,
      None]]

Listing also works by type:

.. code-block:: python

    In [78]: ws.list_objects({'type': 'SimpleObjects.RefObject'})
    Out[78]: 
    [[6,
      u'ref',
      u'SimpleObjects.RefObject-2.0',
      u'2015-12-15T03:12:41+0000',
      1,
      u'kbasetest',
      12,
      u'MyWorkspace',
      u'44e0ef9dff44c4840ddf77abbfc555bd',
      52,
      None]]
      
A large number of filters exist for ``list_objects`` - see the :ref:`apidocs`
for comprehensive coverage. In this example, the list is filtered by the object
metadata:

.. code-block:: python

    In [82]: ws.list_objects({'ids': [12],
                              'meta': {'Wowbagger': 'Prolonged'},
                              'includeMetadata': 1
                              })
    Out[82]: 
    [[2,
      u'simple3',
      u'SimpleObjects.SimpleObject-1.0',
      u'2015-12-14T22:58:01+0000',
      1,
      u'kbasetest',
      12,
      u'MyWorkspace',
      u'8aba51168748e7a7a91847f510ce2807',
      77,
      {u'Eccentrica': u'Gallumbits', u'Wowbagger': u'Prolonged'}]]

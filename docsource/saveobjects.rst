Objects
=======

This documentation describes how to save objects to a workspace and 
demonstrates some of the most common options (see
:ref:`apidocs` for the full API). It assumes that
a functional client is available (see :ref:`buildinitclient`). The examples
use the Python client, but translating to other clients is trivial.

Save an object
--------------

The object will be saved as the type ``SimpleObjects.SimpleObject-1.0``,
defined as:

.. code-block:: python

    In [15]: print ws.get_module_info({'mod': 'SimpleObjects'})['spec']
    module SimpleObjects {

        /* @optional opt */
        typedef structure {
            list<mapping<string, int>> array_of_maps;
            int an_int;
            float a_float;
            string a_string; 
            int opt;
        } SimpleObject;
    };

Saving an object requires specifying either the name or id of the workspace,
the name or id of the object, the type of the object, and the object data.

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

The MD5 is calculated *after* any references are rewritten (see below) and
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
    <ipython-input-24-16c91c62f5fe> in <module>()
    ----> 1 ws.save_objects({'workspace': 'MyWorkspace', 'objects': [{'name': 'simple2', 'type': u'SimpleObjects.SimpleObject-1.0', 'data': obj}]})

    *snip*

    ServerError: JSONRPCError: -32500. Object #1, simple2 failed type checking:
    instance type (integer) does not match any allowed primitive type (allowed: ["string"]), at /a_string
    *snip*

Save a new version
------------------

Providing an existing name or ID when saving an object causes the creation
of a new object version:

.. code-block:: python

    In [20]: obj['a_string'] = 'hoopty frood'

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


Save an object with object references
-------------------------------------



List objects
------------






.. todo::
   save object example w/ prov, meta
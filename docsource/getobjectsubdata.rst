Subsetting objects
==================

When retrieving objects from the WSS, the user may specify which parts of the
object to retrieve. This is useful for quickly retrieving small portions of
large objects (to a webpage, for example) rather than having to fetch the
entire object which might be hundreds of megabytes.

Note that performing subsetting on small objects may provide little to no
benefit, and in some cases may be slower, since the WSS has to parse the
serialized object rather than directly returning the serialized form to the
client.

As usual, it is assumed that a functional client is available (see
:ref:`buildinitclient`). The examples use the Python client, but translating to
other clients is trivial. Only the most common cases are covered - see the
:ref:`apidocs` for complete coverage.

For the examples, the following spec was used:

.. code-block:: python

    In [16]: print ws.get_type_info("SubSetExample.SubSetExample")['spec_def']
    typedef structure {
        mapping<string, mapping<string, string>> map;
        list<mapping<string, string>> array;
    } SubSetExample;

The object in question:

.. code-block:: python

    In [20]: data = {'map': {'mid1': {'id': 'id1', 'stuff': 'foo'},
       ....:                 'mid2': {'id': 'id2', 'stuff': 'bar'}
       ....:                 },
       ....:         'array': [{'id': 'id1', 'stuff': 'foo'},
       ....:                   {'id': 'id2', 'stuff': 'bar'},
       ....:                   {'id': 'id3', 'stuff': 'baz'}
       ....:                   ]
       ....:         }
    
    In [24]: ws.save_objects(
                 {'workspace': 'MyWorkspace',
                  'objects': [{'name': 'subsetexample',
                               'type': u'SubSetExample.SubSetExample',
                               'data': data,
                               }
                              ]
                  })
    Out[24]: 
    [[1,
      u'subsetexample',
      u'SubSetExample.SubSetExample-1.0',
      u'2015-12-16T03:57:03+0000',
      1,
      u'kbasetest',
      13,
      u'MyWorkspace',
      u'f9449880abc5722c7add56e773544719',
      168,
      {}]]

Get the contents of a single key of the mapping:

.. code-block:: python
    :emphasize-lines: 11

    In [11]: ws.get_objects2({'objects':
                 [{'workspace': 'MyWorkspace',
                   'name': 'subsetexample',
                   'included': ['/map/mid1']
                   }
                  ]})['data']
    Out[25]: 
    [{u'copy_source_inaccessible': 0,
      u'created': u'2015-12-16T03:57:03+0000',
      u'creator': u'kbasetest',
      u'data': {u'map': {u'mid1': {u'id': u'id1', u'stuff': u'foo'}}},
      u'extracted_ids': {},
      u'info': [1,
       u'subsetexample',
       u'SubSetExample.SubSetExample-1.0',
       u'2015-12-16T03:57:03+0000',
       1,
       u'kbasetest',
       13,
       u'MyWorkspace',
       u'f9449880abc5722c7add56e773544719',
       168,
       {}],
      u'provenance': [],
      u'refs': []}]

Get all the ``stuff`` fields from the mapping:

.. code-block:: python
    :emphasize-lines: 11-12

    In [39]: ws.get_objects2({'objects':
                 [{'workspace': 'MyWorkspace',
                   'name': 'subsetexample',
                   'included': ['/map/*/stuff']
                   }
                  ]})['data']
    Out[39]: 
    [{u'copy_source_inaccessible': 0,
      u'created': u'2015-12-16T04:04:59+0000',
      u'creator': u'kbasetest',
      u'data': {u'map': {u'mid1': {u'stuff': u'foo'},
        u'mid2': {u'stuff': u'bar'}}},
      u'extracted_ids': {},
      u'info': [1,
       u'subsetexample',
       u'SubSetExample.SubSetExample-1.0',
       u'2015-12-16T04:04:59+0000',
       2,
       u'kbasetest',
       13,
       u'MyWorkspace',
       u'24cd918528461efcb9d6f6a02c3a7965',
       168,
       {}],
      u'provenance': [],
      u'refs': []}]

Get all the ``id`` fields from the array:

.. code-block:: python
    :emphasize-lines: 11

    In [33]: ws.get_objects2({'objects':
                 [{'workspace': 'MyWorkspace',
                   'name': 'subsetexample',
                   'included': ['/array/[*]/id']
                   }
                  ]})['data']
    Out[33]: 
    [{u'copy_source_inaccessible': 0,
      u'created': u'2015-12-16T04:04:59+0000',
      u'creator': u'kbasetest',
      u'data': {u'array': [{u'id': u'id1'}, {u'id': u'id2'}, {u'id': u'id3'}]},
      u'extracted_ids': {},
      u'info': [1,
       u'subsetexample',
       u'SubSetExample.SubSetExample-1.0',
       u'2015-12-16T04:04:59+0000',
       2,
       u'kbasetest',
       13,
       u'MyWorkspace',
       u'24cd918528461efcb9d6f6a02c3a7965',
       168,
       {}],
      u'provenance': [],
      u'refs': []}]

Get the first and third elements of the array (note that the returned array
is compressed to only 2 cells, but the ordering of the source array is
maintained):

.. code-block:: python
    :emphasize-lines: 11

    In [35]: ws.get_objects2({'objects':
                 [{'workspace': 'MyWorkspace',
                   'name': 'subsetexample',
                   'included': ['/array/2', '/array/0']
                   }
                  ]})['data']
    Out[35]: 
    [{u'copy_source_inaccessible': 0,
      u'created': u'2015-12-16T04:04:59+0000',
      u'creator': u'kbasetest',
      u'data': {u'array': [{u'id': u'id1', u'stuff': u'foo'},
        {u'id': u'id3', u'stuff': u'baz'}]},
      u'extracted_ids': {},
      u'info': [1,
       u'subsetexample',
       u'SubSetExample.SubSetExample-1.0',
       u'2015-12-16T04:04:59+0000',
       2,
       u'kbasetest',
       13,
       u'MyWorkspace',
       u'24cd918528461efcb9d6f6a02c3a7965',
       168,
       {}],
      u'provenance': [],
      u'refs': []}]

The previous two calls can be used to find and fetch portions of an array.
First fetch the parts of the subdocuments to be used to determine which
portions of the array are desired, and next fetch the array subdocuments of
interest based on processing the first query. This approach may or may not
be faster than fetching the entire array, so the user should test their
particular use case.

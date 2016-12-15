.. _shockintegration:

Shock integration with the workspace service
============================================

Overview
--------

`Shock <https://github.com/kbase/shock_service>`_ is a data storage system
originally designed for metagenomics data. As such, it is designed for fast
reads and writes of data structured, for the most part, as linear arrays of
strings (such as FASTA biologic sequence files), but more generally
bytestreams.

In contrast, the WSS is designed for storing the hierarchical data objects used
in most programming languages and specifically as specified by the KIDL. In
many cases, bytestream and sequential data may be more efficiently stored in
and retrieved from Shock, and in the cases of data > 1GB, cannot be stored in
the WSS (see :ref:`limits`).

This document describes how to use the
`Handle Service <https://github.com/kbase/handle_service>`_ to link WSS
objects to Shock nodes, such that when the object is shared via the Workspace
API, the linked Shock nodes are shared (more specifically, made readable) as
well.
If a data type developer merely stored a Shock node ID in a workspace object
as a string, sharing the object would not share the underlying Shock node, and
sharees would not be able to access the Shock data.

.. warning::
   Handles, by their nature, are not necessarily permanent. The owner of the
   data referenced by a handle could remove or otherwise make it inaccessible
   at any time.
   
.. warning::
   Shock nodes shared by the workspace are not unshared if the workspace object
   containing the Shock node handle is unshared. The Shock nodes can always be
   unshared via the Shock API.

.. warning::
   Sharing workspace objects containing handles to Shock nodes shares the
   nodes as well. If a workspace object is copied into a user's workspace and
   that workspace is made public, the Shock nodes are set to publically
   readable.

Resources
---------

:ref:`typedobjects` describes how to create workspace types.

`Shock API <https://github.com/MG-RAST/Shock/wiki/API>`_

Handles
-------
You can create handles to data in Shock via the Handle Service. The Handle
Service manages pointers, or handles, to arbitrary pieces of data, and provides
unique IDs for each handle you create. The type definition for a handle is::

    typedef string HandleId;
    typedef structure {
        HandleId hid;
        string file_name;
        string id;
        string type;
        string url;
        string remote_md5;
       string remote_sha1;
    } Handle;

For Shock handles, the fields are defined to be:

============    ======================================================
Member          Description
============    ======================================================
hid             a unique id assigned to a Handle by the Handle service
file_name       the file’s name
id              the shock node id
type            the string “shock”
url             the shock server url
remote_md5      the md5 of the shock data
remote_sha1     unused
============    ======================================================

The remainder of the document covers the procedure for linking a Workspace
object to a Shock node.

Step 1 - save data to Shock with a Handle in the Handle Service
---------------------------------------------------------------

Method 1 - use the Perl HandleService client
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The Perl HandleService client makes creating a handle to shock data simple - it
uploads the file to Shock and creates a handle in one step::

    $hs = Bio::KBase::HandleService->new();
    $handle = $hs->upload($filename);

The ID of the handle can then be retrieved via the hid field::

    $hid = $handle->{hid};

If the Shock data already exists, merely persist a handle you create (leave the
hid field empty for this usage)::

    $hid = $hs->persist_handle($handle);


Method 2 - pre-existing Shock data without the HandleService client
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Save data to Shock
""""""""""""""""""

Here it is assumed that you are familiar with the Shock API, but as an
example::

    $ curl -X POST -H "Authorization: OAuth $TOKEN" -F upload=@important_data.txt https://[shock url]/node

    {"status":200,"data":{"id":"e9f1b8b2-0012-47a9-89ef-fb8fad5a2a5e",
     "version":"e757db0fff0398841505c314179e85f8","file":{"name":
     *snip*
     "2014-08-01T13:12:47.091885252-07:00","type":"basic"},"error":null}

Create one or more handles to Shock data
""""""""""""""""""""""""""""""""""""""""

If you’re working in a language other than Perl, you can use the AbstractHandle
client to persist handles. Here’s a python example:

.. code-block:: python


    In [1]: from biokbase.AbstractHandle.Client import AbstractHandle
    In [2]: ah = AbstractHandle('https://[handle url]', user_id='kbasetest', password=[redacted])

    In [3]: handle = {'type': 'shock', 'url':
                      'https://[shock url]',
                      'id': 'e9f1b8b2-0012-47a9-89ef-fb8fad5a2a5e'
                      }

    In [4]: ah.persist_handle(handle)
    Out[4]: u'KBH_8'

Method 3 - new Shock data without the HandleService client
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Create one or more handles for your data
""""""""""""""""""""""""""""""""""""""""

Use the Handle Service new_handle method to create handles:

.. code-block:: python

    In [48]: from biokbase.AbstractHandle.Client import AbstractHandle
    In [49]: ah = AbstractHandle('https://[handle url]',
                                 user_id='kbasetest', password=[redacted])

    In [50]: ah.new_handle()
    Out[50]:
    {u'file_name': None,
     u'hid': u'KBH_12',
     u'id': u'70ff43ff-db14-405a-bc03-e4dc46860833',
     u'type': u'shock',
     u'url': u'https://[shock url]'}

Save data to the Shock node referenced by the handle
""""""""""""""""""""""""""""""""""""""""""""""""""""

Again, using the Shock API::

    $ curl -X PUT -H "Authorization: OAuth $KBASETEST_TOKEN" -F upload=@important_data.txt https://[shock url]/node/70ff43ff-db14-405a-bc03-e4dc46860833

    {"status":200,"data":{"id":"70ff43ff-db14-405a-bc03-e4dc46860833",
     "version":"458bf368a56ffeeb0a33faa2349b0b7e","file":{"name":
     *snip*
     "2014-08-02T10:32:04.278684787-07:00","type":"basic"},"error":null}


Step 2 - create a Workspace type for your data
----------------------------------------------

If a type specification doesn’t already exist for your data, you will need to
create one. The key point is that you must make the Workspace Service aware
that your data contains one or more Handle IDs. This is done via the
``@id handle`` annotation (see :ref:`idannotations`)::

    /* @id handle */
    typedef string HandleId;
 
    /* @optional file_name
       @optional remote_sha1
       @optional remote_md5
    */
    typedef structure {
        HandleId hid;
        string file_name;
        string id;
        string type;
        string url;
        string remote_md5;
        string remote_sha1;
    } Handle;

Depending on your requirements, you may wish to mark some of the fields
optional as above. All the Workspace service absolutely requires is the handle
ID (``hid``), although marking the ``url`` or ``id`` as optional is unwise, as
the ``Handle`` will not contain enough information for users to retrieve the
shock data.

We then can embed Handles in our data type::

    /* @optional handles */
    typedef structure {
        Handle handle;
        list<Handle> handles;
        string veryimportantstring;
        int veryimportantint;
    } VeryImportantData;

At this point type creation proceeds along normal lines (see
:ref:`typedobjects`).

Step 3 - save data with embedded Handles to the Workspace
---------------------------------------------------------

Saving data with embedded handles is identical to saving any other WSS object.
This example assumes the the type described in the previous section is present
in the VeryImportantModule module and has been registered and released.

.. code-block:: python

    In [1]: from biokbase.workspace.client import Workspace
    In [3]: ws = Workspace('https://[workspace url]',
                           user_id='kbasetest', password=[redacted])

    In [13]: handle1 = {'hid': 'KBH_8',
                        'id': 'e9f1b8b2-0012-47a9-89ef-fb8fad5a2a5e',
                        'url': 'https://[shock url]',
                        'type': 'shock'
                        }
    In [14]: handle2 = {'hid': 'KBH_5',
                        'id': 'ed732169-31a6-4acb-a59c-401d95cc7e3e',
                        'url': 'https://[shock url]',
                        'type': 'shock'
                        }
    In [20]: vip_data = {'handle': handle1,
                         'handles': [handle2],
                         'veryimportantstring': 'My word, I am important',
                         'veryimportantint': 42
                         }

    In [23]: ws.save_objects(
                 {'workspace': 'foo',
                  'objects': [{'name': 'foo',
                               'type': 'VeryImportantModule.VeryImportantData-2.0',
                               'data': vip_data
                               }
                              ]
                  })
    Out[23]:
    [[1,
      u'foo',
      u'VeryImportantModule.VeryImportantData-2.0',
      u'2014-08-01T20:20:58+0000',
      13,
      u'kbasetest',
      2,
      u'foo',
      u'e62152ed3bd328e3001083d0d230ecc0',
      302,
      {}]]

During the save, the Workspace checks with the Handle Service to confirm the
user owns the Shock data. If such is not the case, the save will fail.

Step 4 - share data in the Workspace
------------------------------------

Sharing data works completely normally.

Step 5 - retrieve the data from the Workspace
---------------------------------------------

Retrieving the data from the workspace also works normally, but there’s a
couple of important points. When calling the ``get_objects``, ``get_objects2``,
``get_referenced_objects``, ``get_object_subset``, or
``get_object_provenance`` methods:

* The Handle IDs found in the object are returned in the output as strings, and
* The Workspace makes a request to the Handle Service such that the caller of
  the method is given read access to the data referenced by the handles
  embedded in the object.

This means that, mostly invisibly, the shock nodes embedded via Handles in a
Workspace object are shared as the object is shared.

.. code-block:: python
    :emphasize-lines: 19-22

    In [18]: ws.get_objects2({'objects': [{'ref': 'foo/foo'}]})['data']
    Out[18]:
    [{u'created': u'2014-08-01T20:20:58+0000',
      u'creator': u'kbasetest',  
      u'data': {u'handle': {u'hid': u'KBH_8',
                            u’id': u'e9f1b8b2-0012-47a9-89ef-fb8fad5a2a5e',
                            u'type': u'shock',
                            u'url': [shock_url]
                            },
                u'handles': [{u'hid': u'KBH_5',
                              u'id': u'ed732169-31a6-4acb-a59c-401d95cc7e3e',
                              u'type': u'shock',
                              u'url': [shock_url]
                              }
                             ],
                u'veryimportantint': 42,
                u'veryimportantstring': u'My word, I am important'
                },
      u'extracted_ids': {u'handle': [u'KBH_8',
                                     u'KBH_5'
                                     ]
                         },
      u'info': [1,
                u'foo',
                u'VeryImportantModule.VeryImportantData-2.0', 
                u'2014-08-01T20:20:58+0000',
                13,
                u'kbasetest',
                2,
                u'foo',
                u'e62152ed3bd328e3001083d0d230ecc0',
                302,
                {}],
      u'provenance': [],
      u'refs': []}]

The Shock data can then be retrieved via the Shock API using the handle
information embedded in the object.


If a node has been deleted, the handle service is uncontactable, or some other
error occurs, the workspace will still return the workspace object. However,
the error will be embedded in the returned data structure. The handle_error
field will contain a brief description of the error, and the handle_stacktrace
field will contain the full stacktrace. If these fields are populated the ACLs
of some or all of the Shock nodes embedded in the object could not be updated.

.. code-block:: python
    :emphasize-lines: 7, 8

    In [26]: ws.get_objects2({'objects': [{'ref': 'foo/foo'}]})['data']
    Out[26]:
    [{u'created': u'2014-08-08T00:07:10+0000',
      u'creator': u'kbasetest',
      u'data': {u'handles': [u'KBH_5', u'KBH_6']},
      u'extracted_ids': {u'handle': [u'KBH_6', u'KBH_5']},
      u'handle_error': u'The Handle Manager reported a problem while attempting to set Handle ACLs: Unable to set acl(s) on handles KBH_6, KBH_5',
      u'handle_stacktrace': u'us.kbase.common.service.ServerException: Unable to set acl(s) on handles KBH_6, KBH_5\n
      \tat us.kbase.common.service.JsonClientCaller.jsonrpcCall(JsonClientCaller.java:269)\n
      
      *snip*
      
      \tat java.lang.Thread.run(Thread.java:724)\n',
      u'info': [1,
                u'foo',
                u'ListHandleIds.HandleList-0.1',
                u'2014-08-08T00:07:12+0000',
                5,
                u'kbasetest',
                334,
                u'foo',
                u'd98067db987ccdf5321819b39f73440d',
                29,
                {}
                ],
      u'provenance': [],
      u'refs': []
      }
     ]


.. _paging:

Paging workspace objects
========================

Workspaces can contain many objects, and the ``list_objects`` command returns a maximum of
10000 ``object_info`` data structures (referred to as objects from now on) at a time. This
section of the documentation discusses how to handle the case where access to large amounts
of data is required.

If it is reasonable to expect that a ``list_objects`` command will return no more than 10000
objects then paging doesn't need to be considered. For example if the command only encompasses
one workspace, only the most recent version of each object is required, and the ``max_objid``
field in the workspace information returned by various API methods (for example
``get_workspace_info``) is no more than 10000, then a single ``list_objects`` command can return
all the data requested. See :ref:`workspaces` for more information about getting information
about workspaces and :ref:`listobjects` for information about the ``list_objects`` command.

There are currently two ways to page through workspace data - via object ID limits, and via
reference limit.

Parameters to avoid when paging
-------------------------------

Several parameters should be avoided when using the ``list_objects`` command to page
through data:

* Any of the time stamps (``after``, ``before``, ``after_epoch``, and ``before_epoch``)
* ``savedby``
* ``meta``

The parameters are allowed, but strongly discouraged, when paging via object ID limits. Depending
on the parameter set and the data to be listed, they may slow down the listing substantially,
and may cause the data not to be sorted (see below).

When paging via reference limit, these parameter are not allowed at all, and specifying a
reference limit (the ``startfrom`` parameter) and one of the parameters above will cause an
error. Additionally, the object ID limits (``minObjectID`` and ``maxObjectID``) are not allowed
and including them will cause an error.

Sort order
----------

When paging by reference limit and when paging via object ID limits without including any of
the parameters to avoid listed above, data will be sorted by:

1. the workspace ID, ascending
2. the object ID, ascending
3. the version, **descending**

This makes it easy to determine how many versions an object has the first time it is encountered
in the list of returned data.

Object ID versus reference limits
---------------------------------

Paging by object ID limits works well for paging through data in a single workspace where only
the most recent version of each object is required. Its advantage over reference based paging
is that any range of object IDs can be accessed at any time, whereas in reference based paging
each set of objects returned depends on the reference of the last object in the prior set.

Paging by reference works well for any number of workspaces and when all object versions are
required. The drawback is that data from the prior set of objects is required to get the next set.

Paging by object ID limits
--------------------------

To page by object ID limits, provide the ``minObjectID`` and ``maxObjectID`` parameters
to limit the object IDs, and thus the number of objects, returned in the result. Both parameters
are inclusive. ``maxObjectID - minObjectID`` should always be <= 10000. Setting a smaller
``limit`` can be done by specifying object ID parameters with a smaller difference.

Note that by default, hidden and deleted objects will not be returned in the set of objects.
For this reason, it is important to specify both a minimum and maximum object ID when
incrementing the minimum object ID by a set amount - otherwise
there may be duplicated objects in subsequent ``list_objects`` calls.

Specifying that all object versions should be included in the results is not recommended, as
the ``limit`` may be exceeded, and any excess objects will not be returned in the set.

The following example shows how to page through a large workspace <=10000 objects at a time:

.. code-block:: python

    In [3]: max_id = ws.get_workspace_info({'id': 19217})[4]
    
    In [4]: max_id
    Out[4]: 606146
    
    In [5]: for i in range(0, max_id, 10000): 
       ...:     objs = ws.list_objects({ 
       ...:         'ids': [19217], 
       ...:         'minObjectID': i + 1, 
       ...:         'maxObjectID': i + 10000} 
       ...:     ) 
       ...:     # do something with objs 

If the result is sorted, an alternative is to omit the maximum object ID and generate the
minimum ID for each loop from the maximum object ID in the data from the prior loop. This
may result in fewer overall ``list_objects`` calls, but has the disadvantage that getting each
data set depends on the prior set.

Paging by reference limit
-------------------------

To page by a reference limit, provide the ``startfrom`` parameter and optionally a ``limit``
if less than 10000 objects should be returned at a time. Paging with a reference limit is useful
for paging through multiple workspaces or object versions. The ``startfrom`` parameter looks
like a workspace object reference of the form ``X/Y/Z`` where ``X`` is the integer workspace ID,
``Y`` the integer object ID, and ``Z`` the version. The version may be omitted, and and object ID
may be omitted if the version is omitted. To page through data, start by submitting only the
smallest workspace ID from the set of workspaces submitted to the ``list_objects`` command and
the object ID of the starting object of interest (often 1). Construct the ``startfrom`` parameter
from the last object in the list of returned objects, and decrement the version. If the
resulting version is 0, increment the object ID and omit the version from the ``startfrom`` string.

The following example shows how the paging proceeds, using a very small limit for clarity:

.. code-block:: python

    In [5]: def calc_startfrom(obj_info):
       ...:     ver = obj_info[4] - 1
       ...:     obj_id = obj_info[0]
       ...:     wsid = obj_info[6]
       ...:     if not ver:
       ...:         ver = ''
       ...:         obj_id += 1
       ...:     return '/'.join([str(wsid), str(obj_id), str(ver)])
       ...:
    
    In [6]: startfrom = '1/1'
    
    In [7]: objs = ['fake data']
    
    In [8]: while objs:
       ...:     objs = ws.list_objects({
       ...:         'ids': [1, 2],
       ...:         'startfrom': startfrom,
       ...:         'limit': 3,
       ...:         'showAllVersions': 1
       ...:     })
       ...:     for o in objs:
       ...:         # do something more meaningful here
       ...:         print(f'{o[6]}/{o[0]}/{o[4]}')
       ...:     if objs:
       ...:         startfrom = calc_startfrom(objs[-1])
       ...:         print(f'startfrom: {startfrom}')
       ...:
    1/1/3
    1/1/2
    1/1/1
    startfrom: 1/2/
    1/2/2
    1/2/1
    2/1/2
    startfrom: 2/1/1
    2/1/1
    startfrom: 2/2/

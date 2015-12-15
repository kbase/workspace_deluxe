Subsetting objects
==================

Hi all,

I've received enough questions about how to do subobject extraction from workspace objects that I think it's worthwhile spamming the list with an example.

There's really only one major catch right now, which is that you can't select subobjects from a list based on the contents of the subobjects. This is something we hope to improve in the future. For now, there's a workaround which may or may not be more efficient than just fetching the entire array:

1) Fetch the key by which you want to select the subobjects from all the subobjects in the array (e.g. /array/[*]/key)
2) Find the index of the subobject you're interested in from the list of {key: value}s
3) Fetch the subobject (e.g. /array/36)

There's an example of this approach below.

Also, be sure to read the API docs and the note about array compression: http://140.221.84.209/workspace.html#get_object_subset

On with the examples:

~/localgit/workspace_deluxe/lib$ ipython

In [1]: from biokbase.workspace.client import Workspace

In [2]: ws = Workspace('http://140.221.84.209:7058', user_id='kbasetest', password=[censored])

In [3]: ws.create_workspace({"workspace": "subdatatest", 'globalread': 'r', 'description': 'test ws for subdata extraction'})
Out[3]:
[632,
 u'subdatatest',
 u'kbasetest',
 u'2014-01-09T19:39:48+0000',
 0,
 u'a',
 u'r',
 u'unlocked']

In [4]: testobj = {'map': {'id1': {'id': 'id1', 'stuff': 'foo'},
'id2': {'id': 'id2', 'stuff': 'bar'}},
   ...: 'array': [{'id': 'id1', 'stuff': 'foo'},
   ...: {'id': 'id2', 'stuff': 'bar'}]
   ...: }

In [5]: testobj
Out[5]:
{'array': [{'id': 'id1', 'stuff': 'foo'}, {'id': 'id2', 'stuff': 'bar'}],
 'map': {'id1': {'id': 'id1', 'stuff': 'foo'},
  'id2': {'id': 'id2', 'stuff': 'bar'}}}

In [10]: ws.save_objects({"workspace": "subdatatest", "objects": [{'name': 'sub', "type": "Empty.AType-0.1", "data": testobj}]})
Out[10]:
[[1,
  u'sub',
  u'Empty.AType-0.1',
  u'2014-01-09T19:46:40+0000',
  1,
  u'kbasetest',
  632,
  u'subdatatest',
  u'9af59bca2dd6d4a9d173404a9a815c14',
  139,
  {}]]

In [11]: ws.get_object_subset([{'workspace': 'subdatatest', 'name': 'sub',
   ....: 'included': ['/map/id1']}])
Out[11]:
[{u'created': u'2014-01-09T19:46:40+0000',
  u'creator': u'kbasetest',
  u'data': {u'map': {u'id1': {u'id': u'id1', u'stuff': u'foo'}}},
  u'info': [1,
   u'sub',
   u'Empty.AType-0.1',
   u'2014-01-09T19:46:40+0000',
   1,
   u'kbasetest',
   632,
   u'subdatatest',
   u'9af59bca2dd6d4a9d173404a9a815c14',
   139,
   {}],
  u'provenance': [],
  u'refs': []}]

In [12]: ws.get_object_subset([{'workspace': 'subdatatest', 'name': 'sub',
'included': ['/array/[*]/id']}])
Out[12]:
[{u'created': u'2014-01-09T19:46:40+0000',
  u'creator': u'kbasetest',
  u'data': {u'array': [{u'id': u'id1'}, {u'id': u'id2'}]},
  u'info': [1,
   u'sub',
   u'Empty.AType-0.1',
   u'2014-01-09T19:46:40+0000',
   1,
   u'kbasetest',
   632,
   u'subdatatest',
   u'9af59bca2dd6d4a9d173404a9a815c14',
   139,
   {}],
  u'provenance': [],
  u'refs': []}]

In [13]: ws.get_object_subset([{'workspace': 'subdatatest', 'name': 'sub',
'included': ['/array/1']}])
Out[13]:
[{u'created': u'2014-01-09T19:46:40+0000',
  u'creator': u'kbasetest',
  u'data': {u'array': [{u'id': u'id2', u'stuff': u'bar'}]},
  u'info': [1,
   u'sub',
   u'Empty.AType-0.1',
   u'2014-01-09T19:46:40+0000',
   1,
   u'kbasetest',
   632,
   u'subdatatest',
   u'9af59bca2dd6d4a9d173404a9a815c14',
   139,
   {}],
  u'provenance': [],
  u'refs': []}]

   
   
.. todo::
   subdata example

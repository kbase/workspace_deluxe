WS with 100 objects, 9900 versions spread randomly between the objects (e.g. mongo natural ordering of the versions should be random).
Easy to tell whether sorting is active since the order is ascending for versions without explicit sort instructions and descending with sort.

With sort code active:

    In 29: %timeit drop = ws.list_objects({'workspaces': ['sorttest3'], 'showAllVersions': 1})
    1 loop, best of 3: 9.97 s per loop

With sort code commented out:

    In 31: %timeit drop = ws.list_objects({'workspaces': ['sorttest3'], 'showAllVersions': 1})
    1 loop, best of 3: 10.3 s per loop
'''
Created on Apr 27, 2014

@author: crusherofheads
'''
from __future__ import print_function
from configobj import ConfigObj
from pymongo import MongoClient
import time
import sys

# where to get credentials (don't check these into git, idiot)
CREDS_FILE = 'ws_mongo_creds'
CREDS_SECTION = 'WorkspaceMongoCreds'
USER = 'user'
PWD = 'pwd'
# mongo db location
WS_MONGO_HOST = 'localhost'
WS_MONGO_PORT = 49996
WS_MONGO_DB = 'workspace'
#collection names
COL_WS = 'workspaces'
COL_ACLS = 'workspaceACLs'
COL_OBJ = 'workspaceObjects'
COL_VERS = 'workspaceObjVersions'

PUBLIC = 'pub'
PRIVATE = 'priv'

LIMIT = 10000

if __name__ == '__main__':
    cfg = ConfigObj(CREDS_FILE)
    user = cfg[CREDS_SECTION][USER]
    pwd = cfg[CREDS_SECTION][PWD]
    mongo = MongoClient(WS_MONGO_HOST, WS_MONGO_PORT)
    db = mongo[WS_MONGO_DB]
    db.authenticate(user, pwd)
    # may need to do this in chunks in the future, for now there's
    # < 2000 workspaces
    mongo_workspaces = db[COL_WS].find({'del': False}, ['ws'])
    pub_read = db[COL_ACLS].find({'user': '*'}, ['id', 'perm'])
    pub_ws = {}
    for ws in mongo_workspaces:
        pub_ws[ws['ws']] = False
    for pr in pub_read:
        pub_ws[pr['id']] = True
    print("Total objects: " + str(db[COL_OBJ].count()))
    types = {}
    skip = 0
    no_record = False
    while(not no_record):
        print('Skip: {}'.format(skip))
        sys.stdout.flush()
        no_record = True
        objtime = time.time()
        objs = db[COL_OBJ].find({'del': False}, ['ws', 'id', 'numver'],
                                 skip=skip, limit=LIMIT)
        ttlstart = time.time()
        for o in objs:
            no_record = False
            #this is faster than $or queries - although it might be faster
            # to do $or if the # of items is < 100 say. > 1000 was about 10x
            # slower
            v = db[COL_VERS].find_one({'ws': o['ws'], 'id': o['id'],
                                   'ver': o['numver']}, ['type', 'ws'])
            tname, ver = v['type'].split('-')
            if tname not in types:
                types[tname] = {}
            if ver not in types[tname]:
                types[tname][ver] = {}
                types[tname][ver][PUBLIC] = 0
                types[tname][ver][PRIVATE] = 0
            p = PUBLIC if pub_ws[v['ws']] else PRIVATE
            types[tname][ver][p] += 1
        print('total ver query time: ' + str(time.time() - ttlstart))
        sys.stdout.flush()
        skip += LIMIT

    print()
    print('\t'.join(['Type', 'Version', 'Public', 'Private', 'TTL']))
    pub_tot = 0
    priv_tot = 0
    for t in types:
        pub_type_tot = 0
        priv_type_tot = 0
        for v in types[t]:
            print('\t'.join([t, v, str(types[t][v][PUBLIC]),
                             str(types[t][v][PRIVATE]),
                             str(types[t][v][PUBLIC] + types[t][v][PRIVATE])]))
            pub_type_tot += types[t][v][PUBLIC]
            priv_type_tot += types[t][v][PRIVATE]
        print('\t'.join([t, 'TTL', str(pub_type_tot), str(priv_type_tot),
                         str(pub_type_tot + priv_type_tot)]))
        pub_tot += pub_type_tot
        priv_tot += priv_type_tot
    print('\t'.join(['TTL', '-', str(pub_tot), str(priv_tot)]))

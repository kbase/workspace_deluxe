'''
Created on Apr 27, 2014

@author: crusherofheads

Get stats about the workspace. Object counts by pub/priv/type, unique users
who have saved data, pub/priv workspaces, total size of data (note this
double counts copied data and data with the same MD5)

Don't run this during high loads - runs through every object in the DB
Hasn't been optimized much either
'''
from __future__ import print_function
from configobj import ConfigObj
from pymongo import MongoClient
import time
import sys
from collections import defaultdict

# where to get credentials (don't check these into git, idiot)
CREDS_FILE = 'ws_mongo_creds'
CREDS_SECTION = 'WorkspaceMongoCreds'
USER = 'user'
PWD = 'pwd'
# mongo db location
WS_MONGO_HOST = 'localhost'
WS_MONGO_PORT = 49990
WS_MONGO_DB = 'workspace'
#collection names
COL_WS = 'workspaces'
COL_ACLS = 'workspaceACLs'
COL_OBJ = 'workspaceObjects'
COL_VERS = 'workspaceObjVersions'

PUBLIC = 'pub'
PRIVATE = 'priv'

LIMIT = 10000
MAX_WS = -1  # for testing, set to < 1 for all ws

if __name__ == '__main__':
    cfg = ConfigObj(CREDS_FILE)
    user = cfg[CREDS_SECTION][USER]
    pwd = cfg[CREDS_SECTION][PWD]
    mongo = MongoClient(WS_MONGO_HOST, WS_MONGO_PORT, slaveOk=True)
    db = mongo[WS_MONGO_DB]
    db.authenticate(user, pwd)
    # may need to do this in chunks in the future, for now there's
    # < 2000 workspaces
    ws_cursor = db[COL_WS].find({'del': False}, ['ws', 'numObj'])
    pub_read = db[COL_ACLS].find({'user': '*'}, ['id'])
    workspaces = defaultdict(dict)
    for ws in ws_cursor:
        workspaces[ws['ws']]['pub'] = False
        workspaces[ws['ws']]['numObj'] = ws['numObj']
    for pr in pub_read:
        if pr['id'] in workspaces:  # otherwise deleted
            workspaces[pr['id']]['pub'] = True
    print('Total workspaces: ' + str(len(workspaces)))
    print("Total objects: " + str(db[COL_OBJ].count()))
    types = {}
    wscount = 0
    objcount = 0
    unique_users = set()
    total_size = 0
    for ws in workspaces:
        if MAX_WS > 0 and wscount > MAX_WS:
            break
        wsobjcount = workspaces[ws]['numObj']
        print('\nProcessing workspace {}, {} objects'.format(ws, wsobjcount))
        for lim in xrange(LIMIT, wsobjcount + LIMIT, LIMIT):
            print('\tProcessing objects {} - {}'.format(
                lim - LIMIT + 1, wsobjcount if lim > wsobjcount else lim))
            sys.stdout.flush()
            objtime = time.time()
            query = {'del': False, 'ws': ws,
                      'id': {'$gt': lim - LIMIT, '$lte': lim}}
            objs = db[COL_OBJ].find(query, ['ws', 'id', 'numver'])
            print('\ttotal obj query time: ' + str(time.time() - objtime))
            ttlstart = time.time()
            objsproc = 0
            for o in objs:
                #this is faster than $or queries - although it might be faster
                # to do $or if the # of items is < 100 say. > 1000 was about
                # 10x slower
                # should make some batching code for small batches
                v = db[COL_VERS].find_one({'ws': o['ws'], 'id': o['id'],
                                          'ver': o['numver']},
                                          ['type', 'ws', 'savedby', 'size'])
                unique_users.add(v['savedby'])
                total_size += v['size']
                tname, ver = v['type'].split('-')
                if tname not in types:
                    types[tname] = {}
                if ver not in types[tname]:
                    types[tname][ver] = {}
                    types[tname][ver][PUBLIC] = 0
                    types[tname][ver][PRIVATE] = 0
                p = PUBLIC if workspaces[v['ws']]['pub'] else PRIVATE
                types[tname][ver][p] += 1
                objsproc += 1
            print('\ttotal ver query time: ' + str(time.time() - ttlstart))
            print('\tobjects processed: ' + str(objsproc))
            objcount += objsproc
            print('total objects processed: ' + str(objcount))
            sys.stdout.flush()
        wscount += 1

    print('\nResults:')
    pubws = 0
    privws = 0
    for ws in workspaces:
        if workspaces[ws]['pub']:
            pubws += 1
        else:
            privws += 1
    print('Total public workspaces ' + str(pubws))
    print('Total private workspaces ' + str(privws))
    print('Total users who have saved or copied an object: ' +
          str(len(unique_users)))
    print('Total size of stored data (double counts copies and identical ' +
          'data saved > 1 times): ' + str(total_size))

    print('\n' + '\t'.join(['Type', 'Version', 'Public', 'Private', 'TTL']))
    pub_tot = 0
    priv_tot = 0
    for t in types:
        pub_type_tot = 0
        priv_type_tot = 0
        for v in sorted(types[t]):
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

'''
Created on Apr 27, 2014

@author: crusherofheads
'''
from __future__ import print_function
from configobj import ConfigObj
from pymongo import MongoClient

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
    workspaces = {}
    for ws in mongo_workspaces:
        workspaces[ws['ws']] = False
    for pr in pub_read:
        workspaces[pr['id']] = True
    print(workspaces)

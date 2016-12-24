#!/usr/bin/env python

import requests
import time
import sys
from pymongo.mongo_client import MongoClient


MONGO_DB = 'ws_test'
SHOCK_HOST = 'http://localhost:7044'

def main():
    token = sys.argv[1]
    mcli = MongoClient()
    db = mcli[MONGO_DB]
    ws = -1
    md5s = []
    for ver in db.workspaceObjVersions.find().sort([('ws', 1), ('id', 1)]):
        if ws == -1:
            ws = ver['ws']
        else:
            if ws != ver['ws']:
                raise ValueError('more than one workspace found')
        md5s.append(ver['chksum'])
    nodes = []
    for md5 in md5s:
        rec = db.shock_nodeMap.find_one({'chksum': md5})
        nodes.append(rec['node'])
    print nodes
    headers = {'Authorization': 'OAuth ' + token}
    times = []
    count = 1
    for node in nodes:
        now = time.clock()
        ret = requests.get(SHOCK_HOST + '/node/' + node + '/?download', headers=headers).text
        times.append(time.clock() - now)
        print count
        count += 1
    print 'N: ' + str(len(times))
    mean = sum(times) / float(len(times))
    print 'Mean: ' + str(mean)
    ss = sum((x - mean)**2 for x in times)
    print 'Stddev (sample): ' + str((ss/float(len(times) - 1))**0.5)

if __name__ == '__main__':
    main()


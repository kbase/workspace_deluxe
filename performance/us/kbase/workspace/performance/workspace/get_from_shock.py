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
    md5start = time.clock()
    for ver in db.workspaceObjVersions.find().sort([('ws', 1), ('id', 1)]):
        if ws == -1:
            ws = ver['ws']
        else:
            if ws != ver['ws']:
                raise ValueError('more than one workspace found')
        md5s.append(ver['chksum'])
    md5end = time.clock()
    nodes = []
    for md5 in md5s:
        rec = db.shock_nodeMap.find_one({'chksum': md5})
        nodes.append(rec['node'])
    nodesend = time.clock()
    headers = {'Authorization': 'OAuth ' + token}
    times = []
    count = 1
    for node in nodes:
        now = time.clock()
        ret = requests.get(SHOCK_HOST + '/node/' + node + '/?download', headers=headers)
        ret.text
        times.append(time.clock() - now)
        if ret.status_code != 200:
            raise ValueError('Non 200 return code')
        if count % 10000 == 0:
            print count
        count += 1
    print 'Time to pull md5s from workspace obj vers: ' + str(md5end - md5start)
    print 'Time to pull nodes from shock node map: ' + str(nodesend - md5end)
    print 'Time to pull records from shock: ' + str(time.clock() - nodesend)
    print 'N: ' + str(len(times))
    mean = sum(times) / float(len(times))
    print 'Mean: ' + str(mean)
    ss = sum((x - mean)**2 for x in times)
    print 'Stddev (sample): ' + str((ss/float(len(times) - 1))**0.5)

if __name__ == '__main__':
    main()


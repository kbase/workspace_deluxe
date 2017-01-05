#!/usr/bin/env python

import requests
import time
import sys
import os
from pymongo.mongo_client import MongoClient
import subprocess


MONGO_DB = 'ws_test'
SHOCK_HOST = 'http://localhost:7044'

def main():
    token = sys.argv[1]
    use_curl = sys.argv[2]
    if not token:
        raise ValueError('no token')
    if use_curl:
        print 'Pulling shock nodes with curl'
    else:
        print 'Pulling shock nodes with Python Requests'
    mcli = MongoClient()
    db = mcli[MONGO_DB]
    ws = -1
    md5s = []
    md5start = time.time()
    for ver in db.workspaceObjVersions.find().sort([('ws', 1), ('id', 1)]):
        if ws == -1:
            ws = ver['ws']
        else:
            if ws != ver['ws']:
                raise ValueError('more than one workspace found')
        md5s.append(ver['chksum'])
    md5end = time.time()
    nodes = []
    for md5 in md5s:
        rec = db.shock_nodeMap.find_one({'chksum': md5})
        nodes.append(rec['node'])
    nodesend = time.time()
    headers = {'Authorization': 'OAuth ' + token}
    times = []
    count = 1
    fnull = open(os.devnull, 'w')
    for node in nodes:
        url = SHOCK_HOST + '/node/' + node + '/?download'
        now = time.time()
        if use_curl:
            subprocess.check_call(['curl', '-H', 'Authorization: OAuth ' + token, url],
                                  stdout=fnull, stderr=subprocess.STDOUT)
        else:
            ret = requests.get(url, headers=headers)
            ret.text
            if ret.status_code != 200:
                raise ValueError('Non 200 return code')
        times.append(time.time() - now)
        if count % 10000 == 0:
            print count
        count += 1
    fnull.close()
    print 'Time to pull md5s from workspace obj vers: ' + str(md5end - md5start)
    print 'Time to pull nodes from shock node map: ' + str(nodesend - md5end)
    print 'Time to pull records from shock: ' + str(time.time() - nodesend)
    print 'N: ' + str(len(times))
    mean = sum(times) / float(len(times))
    print 'Mean: ' + str(mean)
    ss = sum((x - mean)**2 for x in times)
    print 'Stddev (sample): ' + str((ss/float(len(times) - 1))**0.5)

if __name__ == '__main__':
    main()


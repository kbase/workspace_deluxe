#!/usr/bin/env python
'''
Created on Aug 30, 2013

@author: gaprice@lbl.gov
'''
from configobj import ConfigObj
import os
import sys

ANT = 'ant'

CFG_SECTION = 'Workspacetest'

CONFIG_OPTS = ['test.user1',
               'test.pwd1',
               'test.user2',
               'test.pwd2',
               'test.user3',
               'test.pwd3',
               'test.shock.exe',
               'test.mongo.exe',
               'test.mongo.useWiredTiger',
               'test.mysql.exe',
               'test.mysql.install.exe',
               'test.plackup.exe',
               'test.handle.service.psgi',
               'test.handle.manager.psgi',
               'test.handle.PERL5LIB',
               'test.temp.dir',
               'test.temp.dir.keep',
               ]


def write_runner(out, ant_target):
    with open(out, 'w') as run:
        run.write('# Generated file - do not check into git\n')
#        run.write('cd ..\n')
        run.write(ANT + ' ' + ant_target)
        for o in CONFIG_OPTS:
            if o in testcfg:
                run.write(' -D' + o + '=' + testcfg[o])
        run.write('\n')
    os.chmod(out, 0755)
    print 'Writing test runner with target "' + ant_target + '" to: ' + out


if __name__ == '__main__':
    d, _ = os.path.split(os.path.abspath(__file__))
    fn = 'test.cfg'
    if len(sys.argv) > 1:
        fn = sys.argv[1]
    fn = os.path.join(d, fn)
    if not os.path.isfile(fn):
        print 'No such config file ' + fn + '. Halting.'
        sys.exit(1)
    print 'Using test config file ' + fn
    out_run_tests = os.path.join(d, 'run_tests.sh')
    out_run_script_tests = os.path.join(d, 'run_script_tests.sh')
    cfg = ConfigObj(fn)
    try:
        testcfg = cfg[CFG_SECTION]
    except KeyError as ke:
        print 'Test config file ' + fn + ' is missing section ' +\
            CFG_SECTION + '. Halting.'
        sys.exit(1)
    if (testcfg['test.user1'] == testcfg['test.user2'] or
        testcfg['test.user1'] == testcfg['test.user3'] or
        testcfg['test.user2'] == testcfg['test.user3']):
        print "At least two test users are identical. Halting."
        sys.exit(1)
    write_runner(out_run_tests, 'test')
    write_runner(out_run_script_tests, 'test-scripts')

    #create a copy of the cfg file in the test/scripts/files dir for script
    # tests -mike
    scriptcfgfile = os.path.join(d, 'scripts', 'files', 'test.cfg.copy')
    with open(scriptcfgfile, 'w') as copyfile:
        cfg.write(copyfile)

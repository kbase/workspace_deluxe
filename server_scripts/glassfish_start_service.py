#!/usr/bin/env python
'''
Created on Dec 6, 2013

@author: gaprice@lbl.gov
'''
from argparse import ArgumentParser
import subprocess


def _parseArgs():
    parser = ArgumentParser(description='script to start a Glassfish ' +
                            ' application.')
    parser.add_argument('-p', '--port', required=True, type=int,
                         help='the port where the application will run.')
    parser.add_argument('-w', '--war', required=True,
                         help='path to the application WAR file.')
    parser.add_argument('-d', '--domain', required=True,
                         help='name of the Glassfish domain where the ' +
                         'application will be installed.')
    parser.add_argument('-a', '--admin', required=True,
                         help='location of the Glassfish asadmin program.')
    parser.add_argument('-t', '--threads', type=int, default=20,
                         help='the number of threads for the application.')
    parser.add_argument('-s', '--Xms', type=int,
                         help='minimum memory for the domain in MB.')
    parser.add_argument('-x', '--Xmx', type=int,
                         help='maximum memory for the domain in MB.')
    return parser.parse_args()


class CommandGlassfishDomain(object):

    def __init__(self, asadminpath, domain):
        self.asadminpath = asadminpath
        self.domain = domain
        if self.exists():
            print "Domain " + self.domain + " exists, skipping creation"
        else:
            self._run_command('create-domain', '--nopassword=true',
                               self.domain)

    def start_domain(self):
        if self.is_running():
            print "Domain " + self.domain + " is already running"
        else:
            print self._run_command('start-domain', self.domain)

    def exists(self):
        return self.domain in self._list_domains()

    def is_running(self):
        return self.domain + " running" in self._list_domains()

    def set_min_max_memory(self, minm, maxm):
        # will restart the domain if changes are necessary
        print self._run_command('list-jvm-options').split('\n')

    def _list_domains(self):
        return self._run_command('list-domains')

    def _run_command(self, *cmd):
        return subprocess.check_output([self.asadminpath] + list(cmd))


if __name__ == '__main__':
    args = _parseArgs()
    gf = CommandGlassfishDomain(args.admin, args.domain)
    gf.start_domain()
    gf.set_min_max_memory(args.Xms, args.Xmx)
    print args

#!/usr/bin/env python
'''
Created on Dec 6, 2013

@author: gaprice@lbl.gov
'''
from __future__ import print_function
from argparse import ArgumentParser
import subprocess
import os
import xml.etree.ElementTree as ET


def _parseArgs():
    parser = ArgumentParser(description='script to administer a Glassfish ' +
                            ' application.')
    parser.add_argument('-w', '--war',
                         help='path to the application WAR file. If ' +
                         'omitted, the service at the port and domain is ' +
                         'stopped.')
    parser.add_argument('-a', '--admin', required=True,
                         help='location of the Glassfish asadmin program.')
    parser.add_argument('-d', '--domain', required=True,
                         help='name of the Glassfish domain where the ' +
                         'application is or will be installed.')
    parser.add_argument('-p', '--port', required=True, type=int,
                         help='the port where the application runs.')
    parser.add_argument('-t', '--threads', type=int, default=20,
                         help='the number of threads for the application.')
    parser.add_argument('-s', '--Xms', type=int,
                         help='minimum memory for the domain in MB. ' +
                         'This will cause a domain restart if changed.')
    parser.add_argument('-x', '--Xmx', type=int,
                         help='maximum memory for the domain in MB. ' +
                         'This will cause a domain restart if changed.')
    return parser.parse_args()


class CommandGlassfishDomain(object):

    def __init__(self, asadminpath, domain):
        self.asadminpath = asadminpath
        self.domain = domain
        if self.exists():
            print("Domain " + self.domain + " exists, skipping creation")
        else:
            self._run_local_command('create-domain', '--nopassword=true',
                               self.domain)
        self.adminport = self.get_admin_port()

    def get_admin_port(self):
        #the fact I have to do this is moronic
        bindir = os.path.dirname(self.asadminpath)
        glassfish = os.path.join(bindir, "..")
        domains = os.path.join(glassfish, "domains")
        domain = os.path.join(domains, self.domain)
        configfile = os.path.join(domain, "config/domain.xml")
        xml = ET.parse(configfile)
        root = xml.getroot()
        config = root.findall("./configs/config[@name='server-config']")[0]
        adminlist = config.findall(
            "./network-config/network-listeners/network-listener[@protocol=" +
            "'admin-listener']")[0]
        return adminlist.attrib['port']

    def start_domain(self):
        if self.is_running():
            print ("Domain " + self.domain + " is already running on port " +
                   self.adminport)
        else:
            print(self._run_local_command('start-domain', self.domain))
            print("Domain " + self.domain + " is now running on port " +
                   self.adminport)

    def restart_domain(self):
        if self.is_running():
            print("Restarting " + self.domain + ", please wait")
            print(self._run_local_command('restart-domain', self.domain))
        else:
            self.start_domain()

    def exists(self):
        return self.domain in self._list_domains()

    def is_running(self):
        return self.domain + " running" in self._list_domains()

    def set_min_max_memory(self, minm, maxm):
        # will restart the domain if changes are necessary
        xmx = []
        xms = []
        for o in self._run_remote_command('list-jvm-options').split('\n'):
            if o.startswith('-Xmx'):
                xmx.append(o)
            if o.startswith('-Xms'):
                xms.append(o)
        if (len(xms) > 1 and minm is None):
            print('WARNING: multiple Xms parameters set on service: ' +
                  str(xms))
        if (len(xmx) > 1 and maxm is None):
            print('WARNING: multiple Xmx parameters set on service: ' +
                  str(xmx))
        changed = self._set_memory(None if minm is None else '-Xms' +
                                   str(minm) + 'm', xms)
        changed2 = self._set_memory(None if maxm is None else '-Xmx'
                                    + str(maxm) + 'm', xmx)
        if changed or changed2:
            self.restart_domain()
        #TODO check if same, if not do nothing, resart if changed

    def _set_memory(self, memstr, memlist):
        if (memstr is not None and [memstr] != memlist):
            print("Removing options " + str(memlist))
            for o in memlist:
                self.remove_option(o)
            print("Setting option " + memstr)
            self._set_option(memstr)
            return True
        else:
            return False

    def _set_option(self, opt):
        self._run_remote_command('create-jvm-options', opt)

    def remove_option(self, opt):
        self._run_remote_command('delete-jvm-options', opt)

    def _list_domains(self):
        return self._run_local_command('list-domains')

    def _run_local_command(self, *cmd):
        return subprocess.check_output([self.asadminpath] + list(cmd))

    def _run_remote_command(self, *cmd):
        return subprocess.check_output([self.asadminpath, '-p', self.adminport]
                                       + list(cmd))


if __name__ == '__main__':
    args = _parseArgs()
    gf = CommandGlassfishDomain(args.admin, args.domain)
    #TODO if no WAR, shutdown
    gf.start_domain()
    gf.set_min_max_memory(args.Xms, args.Xmx)
    print(args)

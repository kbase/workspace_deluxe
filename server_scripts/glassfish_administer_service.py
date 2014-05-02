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
import urllib2
from subprocess import CalledProcessError
import sys

_PARALLEL_GC = "-XX:-UseParallelGC"
_PARALLEL_GC_ESC = "-XX\:-UseParallelGC"


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
    parser.add_argument('-l', '--domain-dir',
                         help='directory where the glassfish domain ' +
                         'information and logs will be stored. Defaults to ' +
                         'glassfish/domains.')
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
    parser.add_argument('-r', '--properties', nargs='*',
                         help='JVM system properties to add to the server.')
    parser.add_argument('-g', '--noparallelgc', action='store_true',
                         help='turn off the parallel garbage ' +
                         ' collector and use the standard gc.')
    return parser.parse_args()


class CommandGlassfishDomain(object):

    def __init__(self, asadminpath, domain, domainpath):
        self.asadminpath = asadminpath
        self.domain = domain
        self.path = None
        if (domainpath):
            domaindir = os.path.abspath(os.path.expanduser(domainpath))
            if not os.path.isdir(domaindir):
                if not os.path.exists(domaindir):
                    os.mkdir(domaindir)
                else:
                    print('Domain path ' + domainpath + ' must be a directory')
                    sys.exit(1)
            self.path = domaindir
        p = (' at ' + self.path) if(self.path) else ''
        if self.exists():
            print('Domain ' + self.domain + ' exists' + p +
                  ', skipping creation')
        else:
            print('Creating domain ' + self.domain + p)
            print(self._run_local_command('create-domain', '--nopassword=true',
                                          self.domain).rstrip())
        self.adminport = self.get_admin_port()
        self.start_domain()

    def get_admin_port(self):
        #the fact I have to do this is moronic
        if (self.path):
            domains = self.path
        else:
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
            print("Starting domain " + self.domain)
            print(self._run_local_command('start-domain', self.domain)
                  .rstrip())
            self.adminport = self.get_admin_port()

    def restart_domain(self):
        if self.is_running():
            print("Restarting " + self.domain + ", please wait")
            print(self._run_local_command('restart-domain', self.domain)
                  .rstrip())
        else:
            self.start_domain()

    def exists(self):
        return self.domain in self._list_domains()

    def is_running(self):
        return self.domain + " running" in self._list_domains()

    def start_service(self, war, port, threads):
        portstr = str(port)
        threadstr = str(threads)
        if 'server-' + portstr in self._run_remote_command(
            'list-virtual-servers'):
            print("Virtual server already exists")
        else:
            print(self._run_remote_command(
                'create-virtual-server', '--hosts',
                '${com.sun.aas.hostName}', 'server-' + portstr).rstrip())
        if 'thread-pool-' + portstr in self._run_remote_command(
            'list-threadpools', 'server'):
            print("Threadpool already exists")
        else:
            print(self._run_remote_command(
                'create-threadpool', '--maxthreadpoolsize=' + threadstr,
                '--minthreadpoolsize=' + threadstr, 'thread-pool-' + portstr)
                  .rstrip())
        if 'http-listener-' + portstr in self._run_remote_command(
            'list-http-listeners'):
            print('Http listener already exists')
        else:
            print(self._run_remote_command(
                'create-http-listener', '--listeneraddress', '0.0.0.0',
                '--listenerport', portstr,
                '--default-virtual-server', 'server-' + portstr,
                '--securityEnabled=false', '--acceptorthreads=' + threadstr,
                'http-listener-' + portstr).rstrip())
            print(self._run_remote_command(
                'set', 'server.network-config.network-listeners.' +
                'network-listener.http-listener-' + portstr +
                '.thread-pool=thread-pool-' + portstr).rstrip())
            print(self._run_remote_command(
                'set', 'server.network-config.protocols.protocol.' +
                'http-listener-' + portstr + '.http.timeout-seconds=1800')
                .rstrip())
        if 'app-' + portstr in self._run_remote_command('list-applications'):
            print(self._run_remote_command('undeploy', 'app-' + portstr)
                  .rstrip())
        print(self._run_remote_command(
            'deploy', '--virtualservers', 'server-' + portstr,
            '--contextroot', '/', '--name', 'app-' + portstr, war).rstrip())
        try:
            urllib2.urlopen('http://localhost:' + portstr)
        except urllib2.HTTPError as h:
            resp = h.read()
        else:
            print('Unexpected response from server - the server did not ' +
                  'start up successfully. Please check the glassfish logs.')
            return False
        if '32603' in resp:
            print('The server failed to start up successfully and is ' +
                  'running in protected mode. Please check the system and ' +
                  'glassfish logs.')
            return False
        elif '32300' in resp:
            print('The server started successfully.')
            return True
        else:
            print('The server failed to start up successfully and is not '
                  + 'running. Please check the system and glassfish logs.')
            return False

    def stop_service(self, port):
        portstr = str(port)
        if 'app-' + portstr in self._run_remote_command('list-applications'):
            print(self._run_remote_command('undeploy', 'app-' + portstr)
                  .rstrip())
        if 'http-listener-' + portstr in self._run_remote_command(
            'list-http-listeners'):
            print(self._run_remote_command(
                'delete-http-listener', 'http-listener-' + portstr).rstrip())
        if 'http-listener-' + portstr in self._run_remote_command(
            'list-protocols'):
            print(self._run_remote_command(
                'delete-protocol', 'http-listener-' + portstr).rstrip())
        if 'thread-pool-' + portstr in self._run_remote_command(
            'list-threadpools', 'server'):
            print(self._run_remote_command(
                'delete-threadpool', 'thread-pool-' + portstr).rstrip())
        if 'server-' + portstr in self._run_remote_command(
            'list-virtual-servers'):
            print(self._run_remote_command(
                'delete-virtual-server', 'server-' + portstr).rstrip())

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

    def reenable_parallel_gc(self):
        if self.parallel_gc_is_disabled():
            self.delete_jvm_option(_PARALLEL_GC_ESC)
            self.restart_domain()

    def parallel_gc_is_disabled(self):
        for o in self._run_remote_command('list-jvm-options').split('\n'):
            if o == _PARALLEL_GC:
                return True
        return False

    def stop_parallel_gc(self):
        if not self.parallel_gc_is_disabled():
            self.create_jvm_option(_PARALLEL_GC_ESC)
            self.restart_domain()

    def create_property(self, prop):
        print('Creating property ' + prop)
        print(self._run_remote_command('create-system-properties', prop)
              .rstrip())

    def create_jvm_option(self, prop):
        print('Creating jvm property ' + prop)
        print(self._run_remote_command('create-jvm-options', prop)
              .rstrip())

    def delete_jvm_option(self, prop):
        print('Removing jvm property ' + prop)
        print(self._run_remote_command('delete-jvm-options', prop).rstrip())

    def _set_memory(self, memstr, memlist):
        if (memstr is not None and [memstr] != memlist):
            print("Removing options " + str(memlist))
            for o in memlist:
                self._remove_option(o)
            print("Setting option " + memstr)
            self._set_option(memstr)
            return True
        else:
            return False

    def _set_option(self, opt):
        self._run_remote_command('create-jvm-options', opt)

    def _remove_option(self, opt):
        self._run_remote_command('delete-jvm-options', opt)

    def _list_domains(self):
        return self._run_local_command('list-domains')

    def _run_local_command(self, subcmd, *args):
        cmd = [self.asadminpath, subcmd]
        if (self.path):
            cmd.extend(['--domaindir', self.path])
        try:
            return subprocess.check_output(cmd + list(args))
        except CalledProcessError as cpe:
            print(cpe.output.rstrip())
            sys.exit(1)

    def _run_remote_command(self, *cmd):
        try:
            return subprocess.check_output([self.asadminpath, '-p',
                                            self.adminport] + list(cmd))
        except CalledProcessError as cpe:
            print(cpe.output.rstrip())
            sys.exit(1)


if __name__ == '__main__':
    args = _parseArgs()
    gf = CommandGlassfishDomain(args.admin, args.domain, args.domain_dir)
    if (args.war == None):
        gf.stop_service(args.port)
    else:
        if (args.noparallelgc):
            gf.stop_parallel_gc()
        else:
            gf.reenable_parallel_gc()
        gf.set_min_max_memory(args.Xms, args.Xmx)
        for p in args.properties:
            gf.create_property(p)
        success = gf.start_service(args.war, args.port, args.threads)
        if not success:
            sys.exit(1)

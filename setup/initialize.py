#!/usr/bin/env python

# script to initialize/modify a mongo database for the workspace service.

from __future__ import print_function
import os
import sys
from ConfigParser import ConfigParser
import configobj

try:  # py 2/3 compatibility
    input = raw_input
except NameError:
    pass

__AUTHOR__ = 'gaprice@lbl.gov'

configfile = 'deploy.cfg'
configheader = 'Workspace'

moduledir = 'workspace_deluxe'
setupdir = 'setup'
filename = 'initialize.py'


def printerr(*objs):
    print(*objs, file=sys.stderr)
    sys.exit(1)


def renamederr(err):
    printerr(err + ' Please use the {} program found in the {} folder.'.format(
                filename, os.path.join(moduledir, setupdir)))


def printcfg(cfg, header):
    for k, v in cfg.items(header):
        print(k + '=' + v)


def getinput(prompt, default=(), otheroptions={}, quitopt=False):
    opts = set()
    if default:
        defopt = default[0].lower()
        opts.add(defopt)
        prompt += ' [' + defopt + ' - ' + default[1] + ']'
    if otheroptions:
        for o, olong in otheroptions.iteritems():
            o = o.lower()
            opts.add(o)
            prompt += '/' + o + ' - ' + olong
    if quitopt:
        prompt += '/q - quit'
        opts.add('q')
    prompt += ': '
    while True:
        i = input(prompt)
        if not i:
            if default:
                return defopt
            else:
                print('Input required.')
        else:
            if i.lower() not in opts:
                print('Invalid input.')
            else:
                return i


if __name__ == '__main__':
    d, program = os.path.split(os.path.abspath(__file__))
    wd, setup = os.path.split(d)
    if os.path.basename(wd) != moduledir or setup != setupdir:
        renamederr('This program has been moved from its standard location.')
    if program != filename:
        renamederr('This program has been renamed.')
    cfgfile = os.path.join(wd, configfile)
    if os.path.isdir(cfgfile):
        printerr(cfgfile + ' is a directory!')
    cfg = ConfigParser()
    keepconfig = False
    if os.path.isfile(cfgfile):
        cfg.read(cfgfile)
        if not cfg.has_section(configheader):
            print('No {} section in config file {} - '.format(
                configheader, cfgfile) + 'will be created.')
            cfg.add_section(configheader)
        else:
            print('Current configuration:')
            printcfg(cfg, configheader)
            keepconfig = getinput('Keep this configuration?', ('y', 'keep'),
                                  {'n': 'discard'}) == 'y'
            if keepconfig:
                print('Keeping current configuration.')
            else:
                print('Discarding current configuration.')
    # use configobj instead
            
        
        
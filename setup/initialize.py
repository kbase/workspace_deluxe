#!/usr/bin/env python

# script to initialize/modify a mongo database for the workspace service.

from __future__ import print_function
import os
import sys
from configobj import ConfigObj
from pymongo import MongoClient

try:  # py 2/3 compatibility
    input = raw_input  # @ReservedAssignment
except NameError:
    pass

__AUTHOR__ = 'gaprice@lbl.gov'

CONFIGFILE = 'deploy.cfg'
CONFIGHEADER = 'Workspace'

MODULEDIR = 'workspace_deluxe'
SETUPDIR = 'setup'
FILENAME = 'initialize.py'

MONGO = 'mongodb-'

MODB = MONGO + 'database'
MOHOST = MONGO + 'host'
MOUSER = MONGO + 'user'
MOPWD = MONGO + 'pwd'


REQPARAMS = [MOHOST, MODB]
AUTHPARAMS = [MOUSER, MOPWD]


def printerr(*objs):
    print(*objs, file=sys.stderr)
    sys.exit(1)


def renamederr(err):
    printerr(err + ' Please use the {} program found in the {} folder.'.format(
                FILENAME, os.path.join(MODULEDIR, SETUPDIR)))


def printcfg(cfg, header):
    for k, v in cfg[header].iteritems():
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


def getparams(params, cfg, dropall):
    for key in params:
        if dropall or key not in cfg:
            cfg[key] = input("Please enter value for " + key + ": ")


if __name__ == '__main__':
    d, program = os.path.split(os.path.abspath(__file__))
    wd, setup = os.path.split(d)
    if os.path.basename(wd) != MODULEDIR or setup != SETUPDIR:
        renamederr('This program has been moved from its standard location.')
    if program != FILENAME:
        renamederr('This program has been renamed.')
    cfgfile = os.path.join(wd, CONFIGFILE)
    if os.path.isdir(cfgfile):
        printerr(cfgfile + ' is a directory!')
    cfg = ConfigObj()
    dropconfig = False
    if os.path.isfile(cfgfile):
        cfg = ConfigObj(cfgfile)
        if CONFIGHEADER not in cfg:
            print('No {} section in config file {} - '.format(
                CONFIGHEADER, cfgfile) + 'will be created.')
            cfg[CONFIGHEADER] = None
        else:
            print('Current configuration:')
            printcfg(cfg, CONFIGHEADER)
            dropconfig = getinput('Keep this configuration?', ('y', 'keep'),
                                  {'n': 'discard'}) == 'n'
            if dropconfig:
                print('Discarding current configuration.')
            else:
                print('Keeping current configuration.')
    wscfg = cfg[CONFIGHEADER]
    getparams(REQPARAMS, wscfg, dropconfig)
    authrqd = getinput('Does mongodb require authentication?', ('y', 'yes'),
                {'n': 'no'}) == 'y'
    if authrqd:
        getparams(AUTHPARAMS, wscfg, dropconfig)
    else:
        print('Ok, commenting out authorization information.')
        for key in AUTHPARAMS:
            if key in wscfg:
                wscfg['#' + key] = wscfg[key]
                del wscfg[key]
    print('Attempting to connect to mongodb database "' + wscfg[MODB] +
          '" at ' + wscfg[MOHOST] + '... ', end='')
    try:
        mongo = MongoClient(wscfg[MOHOST])
    except:
        printerr('\nCould not connect to mongodb at ' + wscfg[MOHOST])
    db = mongo[wscfg[MODB]]
    if authrqd:
        try:
            db.authenticate(wscfg[MOUSER], wscfg[MOPWD])
        except:
            printerr('\nUnable to authenticate to database "' + wscfg[MODB] +
                     '"')
    print('Connected.')
    print(db)
    
    
    print(cfg)
    
            
        
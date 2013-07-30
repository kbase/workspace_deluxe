#!/usr/bin/env python

# script to initialize/modify a mongo database for the workspace service.

from __future__ import print_function
import os
import sys
from configobj import ConfigObj
from pymongo import MongoClient
import urllib2
import json

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

SETTINGS = 'settings'
SHOCKURL = 'shock_location'


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


def setshockurlfromuser():
    shockurl = input('Please enter the url of the shock server: ')
    try:
        r = urllib2.urlopen(shockurl).read()
    except:
        printerr("Couldn't contact the shock server at " + shockurl)
    try:
        j = json.loads(r)
    except:
        printerr(shockurl + ' is not a shock server root url')
    if 'id' not in j or j['id'] != 'Shock':
        printerr(shockurl + ' is not a shock server root url')
    shockurl = j['url']
    db[SETTINGS].update({}, {'$set': {SHOCKURL: shockurl}}, upsert=True)
    shock = db[SETTINGS].find_one()
    print('Successfully set shock location to ' + shock[SHOCKURL])


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
            cfg[CONFIGHEADER] = {}
        else:
            print('Current configuration:')
            printcfg(cfg, CONFIGHEADER)
            dropconfig = getinput('\nKeep this configuration?', ('y', 'keep'),
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
    cfg.write()
    print('Configuration saved.')
    shock = None
    if SETTINGS in db.collection_names():
        shock = db[SETTINGS].find_one()
    if shock != None:
        shockurl = shock[SHOCKURL]
        print('The database is already initialized with the shock server ' +
              'at:\n' + shockurl)
        print(
'''You can change the shock url now, but if the workspace service has already
saved objects it will put the workspace and shock in inconsistent states,
all workspace objects will be irretrievable, and you will make a lot of people
really really mad.''')
        prompts = ['Do you want to change the shock url?',
                   'Are you absolutely sure you know what you are ' +
                   'doing?\nThe consequences are dire, dire!',
                   'Seriously? You are about to royally jack ' +
                   'things up if the workspace service\nhas ever ' +
                   'saved an object.'
                   ]
        for n, q in enumerate(prompts):
            if getinput(q, ('n', 'no'), {'y': 'yes'}) != 'y':
                break
            if n == len(prompts) - 1:
                print('Ok, on your head be it.')
                setshockurlfromuser()
    else:
        setshockurlfromuser()
    print('\nConfiguration complete.')

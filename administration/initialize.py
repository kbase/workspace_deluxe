#!/usr/bin/env python

# script to initialize/modify a mongo database for the workspace service.

from __future__ import print_function
import os
import sys
from configobj import ConfigObj
from pymongo import MongoClient
import urllib2
import json
import requests

try:  # py 2/3 compatibility
    input = raw_input  # @ReservedAssignment
except NameError:
    pass

__AUTHOR__ = 'gaprice@lbl.gov'

CONFIGFILE = 'deploy.cfg'
CONFIGHEADER = 'Workspace'

MODULEDIR = 'workspace_deluxe'
SETUPDIR = 'administration'
FILENAME = 'initialize.py'

MONGO = 'mongodb-'

MODB = MONGO + 'database'
MOHOST = MONGO + 'host'
MOUSER = MONGO + 'user'
MOPWD = MONGO + 'pwd'

AUTH_URL = 'auth-service-url'

SETTINGS = 'settings'
SHOCKURL = 'shock_location'
SHOCKUSER = 'shock_user'
BACKEND = 'backend'
TYPE_DB = 'type_db'
BACKENDTOKEN = 'backend-token'
SHOCK = 'shock'
GFS = 'gridFS'

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
            cfg[key] = input('Please enter value for ' + key + ': ')


def printDBsettings(settings):
    for s in [TYPE_DB, BACKEND, SHOCKURL, SHOCKUSER]:
        print(s + '=' + str(settings.get(s, None)))


def _get_user(token, authurl):
    if not authurl:
        print('No auth url found in deploy file, using default')
        authurl = 'https://kbase.us/services/auth/api/legacy/KBase/Sessions/Login'
    d = {'token': token, 'fields': 'user_id'}
    print('Validating token with auth server at ' + authurl)
    ret = requests.post(authurl, data=d)
    if not ret.ok:
        try:
            err = ret.json()
        except:
            ret.raise_for_status()
        raise ValueError('Error connecting to auth service: {} {}\n{}'
                         .format(ret.status_code, ret.reason,
                                 err['error_msg']))

    return ret.json()['user_id']


def configDB(wscfg, db):
    settings = {SHOCKURL: None, SHOCKUSER: None, BACKEND: None}
    typedb = None
    while not typedb:
        typedb = input('Please enter the name of the mongodb type database: ')
        if typedb == wscfg[MODB]:
            print('The type database name cannot be the same as the ' +
                  'workspace database name: ' + wscfg[MODB])
            typedb = None
    settings[TYPE_DB] = typedb
    backend = getinput('Choose a backend: ', ('s', SHOCK), {'g': GFS})
    if backend == 's':
        settings[BACKEND] = SHOCK
        shockurl = input('Please enter the url of the shock server: ')
        shocktoken = input('Please enter an authentication token for the ' +
                           'workspace shock user account: ')
        try:
            shockuser = _get_user(shocktoken, wscfg.get(AUTH_URL))
        except Exception as e:
            printerr('Token validation failed: ' + e.args[0])
        wscfg[BACKENDTOKEN] = shocktoken
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
        settings[SHOCKURL] = j['url']
        settings[SHOCKUSER] = shockuser
    else:
        settings[BACKEND] = GFS

    db[SETTINGS].update({}, settings, upsert=True)
    settings = db[SETTINGS].find_one()
    print('Successfully set DB configuration:')
    printDBsettings(settings)
    print()


def main():
    d, program = os.path.split(os.path.abspath(__file__))
    wd, setup = os.path.split(d)
    if os.path.basename(wd) != MODULEDIR or setup != SETUPDIR:
        renamederr('This program has been moved from its standard location.')
    if program != FILENAME:
        renamederr('This program has been renamed.')
    lclcfg = sys.argv[1] if len(sys.argv) > 1 else CONFIGFILE
    cfgfile = os.path.join(wd, lclcfg)
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
            print('Current configuration file:')
            printcfg(cfg, CONFIGHEADER)
            dropconfig = getinput('\nKeep this configuration?', ('y', 'keep'),
                                  {'n': 'discard'}) == 'n'
            if dropconfig:
                print('Discarding current local configuration.')
            else:
                print('Keeping current local configuration.')
    else:
        printerr('Cannot find configfile: ' + cfgfile)
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
    settings = None
    if SETTINGS in db.collection_names():
        settings = db[SETTINGS].find_one()
    if settings:
        print('The database is already initialized with the parameters:')
        printDBsettings(settings)
        print(
            '''\nYou can change the server configuration now, but if the workspaceservice
has already saved objects it could put the workspace and the backend store
(gridFS or shock) in an inconsistent state, in which case all workspace
objects will be irretrievable and you will make a lot of people really really
mad.''')
        prompts = ['Do you want to change the configuration?',
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
                configDB(wscfg, db)
    else:
        configDB(wscfg, db)
    print('Saving local configuration file:')
    printcfg(cfg, CONFIGHEADER)
    cfg.write()
    print('\nConfiguration saved.')

if __name__ == '__main__':
    main()

#!/usr/bin/env perl
#
# This method accepts one optional argument, and that is the endpoint
# URL to test. If not provided, it reverts to the WsTestConfig module
# method getLocalTestURL...
#
# This test is designed to follow a basic ws user session that runs
# through functionality of most of the commands.  Basic sanity checks
# are performed to ensure the scripts are functioning.
#
# WARNING: these tests are not fully implemented yet, and not yet
# invoked from make test!  Do not yet rely on this test script!
#
#  author:  msneddon
#  created: july 2014
use strict;
use warnings;

use lib "test/scripts";
use WsTestConfig qw(getLocalTestURL getDefaultURL getWsTestCfg);

use Test::More;
use Data::Dumper;
use File::Basename;
use Config::Simple;
use Cwd qw(abs_path);
use File::Path qw(make_path);
use JSON;

# set the config file location we would like to test - remove if it exists, then create
# using the Config::Simple module
make_path(dirname(abs_path($0)).'/scratch', {verbose => 1});
my $clientCfgFile = dirname(abs_path($0)).'/scratch/kbase_client_test_config';
$ENV{KB_CLIENT_CONFIG}=$clientCfgFile;
if(-e $clientCfgFile) { unlink([$clientCfgFile]); }

my $clientcfg = new Config::Simple(syntax=>'ini');
$clientcfg->param("madeupfortest.test","test");
# this config lib sucks!  we should switch.  I have to add an INI parameter or else the config
# library can't read it's own output even with explicitly telling it to use INI format!
$clientcfg->write($clientCfgFile);
$clientcfg->close();

# get the test config options we need
my $cfg = getWsTestCfg();
#print Dumper($cfg);
my $user1 = $cfg->param("Workspacetest.test.user1");
my $pwd1  = $cfg->param("Workspacetest.test.pwd1");
my $user2 = $cfg->param("Workspacetest.test.user2");
my $pwd2  = $cfg->param("Workspacetest.test.pwd2");

# if a URL is passed in, use it in testing
my $testurl = getLocalTestURL();
if(scalar(@ARGV)==1) {
    $testurl = $ARGV[0];
} elsif(scalar(@ARGV)>1) {
    die "Too many input arguments given.  Test accepts one optional argument, the endpoint url to test.";
}
my $defaulturl = getDefaultURL();

my $json_parser = JSON->new;
my ($cmd,$out,$outobj,$exit_code);

# SET THE URL FOR THE REST OF THE SCRIPTS

# USER1 logs in and sets the url
executeValidCommand("ws-url -n");

executeValidCommand("kbase-login $user1 -p $pwd1");
executeValidCommand("ws-url $testurl");

# Lists workspaces, creates a new workspace, and it should appear
executeValidCommand("ws-list");
executeValidCommand("ws-createws myWs");
$out = executeValidCommand("ws-list");

#list objects
executeValidCommand("ws-workspace myWs");
executeValidCommand("ws-listobj");

#save objects
executeInvalidCommand('ws-load NotAModule.NotAType no_type "{\"blah\":3}" 2> /dev/null');

# get object
executeInvalidCommand('ws-get mytype 2> /dev/null');
executeInvalidCommand('ws-get -m mytype 2> /dev/null');
executeInvalidCommand('ws-get --prov mytype 2> /dev/null');
executeInvalidCommand('ws-get -p mytype 2> /dev/null');

#list objects
executeValidCommand("ws-listobj");

#save new version, copy object
executeInvalidCommand('ws-load NotAModule.NotAType no_type "{\"blah\":3}" 2> /dev/null');
executeInvalidCommand('ws-copy 2> /dev/null');

# delete object, should be gone, undelete object
executeInvalidCommand('ws-delete 2> /dev/null');

#revert object, should get new version
executeInvalidCommand('ws-revert 2> /dev/null');

# hide/unhide object
executeInvalidCommand('ws-hide 2> /dev/null');

# rename object
executeInvalidCommand('ws-rename 2> /dev/null');

# get object again
executeInvalidCommand('ws-get mytype 2> /dev/null');
executeInvalidCommand('ws-get -m mytype 2> /dev/null');
executeInvalidCommand('ws-get --prov mytype 2> /dev/null');
executeInvalidCommand('ws-get -p mytype 2> /dev/null');

# view object history
executeInvalidCommand('ws-history mytype 2> /dev/null');

# get subfield of object
executeInvalidCommand('ws-getsubset 2> /dev/null');

#clone the workspace
executeInvalidCommand('ws-clone 2> /dev/null');

#delete the old workspace
executeInvalidCommand('ws-deletews 2> /dev/null');

# rename the new workspace
executeInvalidCommand('ws-renamews 2> /dev/null');

# share the workspace, logout, other user should be able to see ws
executeValidCommand('ws-share 2> /dev/null');

# logout
executeValidCommand("kbase-logout");


done_testing();



sub checkConfigVariableDefined {
    my $variable_name = shift;
    my $expected_value = shift;
    my $file = shift;
    my $cfg = new Config::Simple($file);
    my $value = $cfg->param($variable_name);
    ok(defined($value),"config var '$variable_name' is defined");
    if (!defined($value)) { $value = ''; }
    ok($value eq $expected_value, "config var '$variable_name' should be '$expected_value', was '$value'");
    $cfg->close();
}

sub checkConfigVariableNotDefined {
    my $variable_name = shift;
    my $file = shift;
    my $cfg = new Config::Simple($file);
    my $value = $cfg->param($variable_name);
    ok(!defined($value),"config var '$variable_name' is undefined");
    $cfg->close();
}

sub executeValidCommand {
    my $cmd = shift;
    $out = `$cmd`;
    $exit_code = ($? >> 8);
    ok($exit_code==0,"testing successful cmd:(>$cmd)");
    return $out;
}

sub executeInvalidCommand {
    my $cmd = shift;
    $out = `$cmd`;
    $exit_code = ($? >> 8);
    ok($exit_code!=0,"testing invalid cmd:(>$cmd) return code=$exit_code");
    return $out;
}

sub turnOnIrisEnv {
    $ENV{KB_RUNNING_IN_IRIS}=1;
}

sub turnOffIrisEnv {
    delete($ENV{KB_RUNNING_IN_IRIS});
}

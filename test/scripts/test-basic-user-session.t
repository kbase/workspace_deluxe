#!/usr/bin/env perl
#
# This method accepts one optional argument, and that is the endpoint
# URL to test. If not provided, it reverts to the WsTestConfig module
# method getLocalTestURL...
#
#  author:  msneddon
#  created: april 2014
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

my $out;

# USER1 logs in and sets the url
executeValidCommand("ws-url -n");
executeValidCommand("kbase-login $user1 -p $pwd1");
executeValidCommand("ws-url $testurl");

# Lists workspaces, creates a new workspace, and it should appear
executeValidCommand("ws-list");
executeValidCommand("ws-createws myWs");
$out = executeValidCommand("ws-list");

#list objects

#save objects

# get object

#list objects

#save new version, copy object

# delete object, should be gone, undelete object

#revert object, should get new version

# hide/unhide object

# get object again

# view object history

# view object provenance

# get subfield of object

#clone the workspace

#delete the old workspace

# rename the new workspace

# share the workspace, logout, other user should be able to see ws

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

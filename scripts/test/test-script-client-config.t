#!/usr/bin/env perl
#
# Test the workspace scripts that handle setting the client config
# in other words, we are testing ws-workspace, ws-url, kbase-login
# kbase-logout, and kbase-whoami
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

# attempt to login
my ($cmd,$out,$exit_code);
$out = `kbase-login madeupuser -p notapassword`;
$exit_code = ($? >> 8);
ok($out=~m/failed/,'testing bogus kbase-login, should not work and should state "failed"');

$out = executeValidCommand("kbase-login $user1 -p $pwd1");
checkConfigVariableDefined("authentication.user_id",$user1,$clientCfgFile);

$out = executeValidCommand("kbase-whoami");
ok($out eq "You are logged in as:\n$user1\n", "kbase-whomai returns what is expected");

# check setting URL config
$out = executeInvalidCommand("ws-url madeupurl 2> /dev/null");
checkConfigVariableDefined("workspace_deluxe.url","madeupurl",$clientCfgFile);

$out = executeValidCommand("ws-url -n madeupurl2");
checkConfigVariableDefined("workspace_deluxe.url","madeupurl2",$clientCfgFile);

$out = executeValidCommand("ws-url -n default");
checkConfigVariableDefined("workspace_deluxe.url",$defaulturl,$clientCfgFile);
$out = executeValidCommand("ws-url -n prod");
checkConfigVariableDefined("workspace_deluxe.url",$defaulturl,$clientCfgFile);

$out = executeValidCommand("ws-url $testurl");
checkConfigVariableDefined("workspace_deluxe.url",$testurl, $clientCfgFile);
ok($out =~ m/^Current URL is: \n$testurl\n/,"checking ws-url displays correct url");
$out = executeValidCommand("ws-url -n");
ok($out eq "Current URL is: \n$testurl\n","checking ws-url displays correct url");

turnOnIrisEnv();
$out = executeValidCommand("ws-url -n $testurl");
ok($out eq "Current URL is: \n$testurl\n", "checking ws-url displays correct url in IRIS env");
$out = executeValidCommand("ws-url -n");
ok($out eq "Current URL is: \n$testurl\n", "checking ws-url displays correct url in IRIS env");
turnOffIrisEnv();

# check setting the default workspace
$out = executeInvalidCommand("ws-workspace madeupfakews 2> /dev/null");
checkConfigVariableDefined("workspace_deluxe.$user1-current-workspace","madeupfakews",$clientCfgFile);

$out = executeValidCommand("ws-workspace -n madeupfakews2");
checkConfigVariableDefined("workspace_deluxe.$user1-current-workspace","madeupfakews2",$clientCfgFile);
$out = executeValidCommand("ws-workspace -n");
checkConfigVariableDefined("workspace_deluxe.$user1-current-workspace","madeupfakews2",$clientCfgFile);
ok($out eq "Current workspace set to:\nmadeupfakews2\n", "checking ws-workspace displays correct workspace");

turnOnIrisEnv();
$out = executeInvalidCommand("ws-workspace madeupfakews 2> /dev/null");
$out = executeValidCommand("ws-workspace -n madeupfakews2");
$out = executeValidCommand("ws-workspace -n");
checkConfigVariableDefined("workspace_deluxe.$user1-current-workspace","madeupfakews2",$clientCfgFile);
ok($out eq "Current workspace set to:\nmadeupfakews2\n", "checking ws-workspace displays correct workspace");
turnOffIrisEnv();


# check logging out
$out = executeValidCommand("kbase-logout");
checkConfigVariableNotDefined("authentication.user_id",$clientCfgFile);
checkConfigVariableNotDefined("authentication.token",$clientCfgFile);

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

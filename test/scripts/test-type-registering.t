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

# USER1 requests module ownership
executeValidCommand("kbase-login $user1 -p $pwd1");
executeValidCommand("ws-url $testurl");
executeValidCommand("ws-typespec-register --request KB");
executeInvalidCommand("ws-typespec-register -t test/scripts/files/KB.spec 2> /dev/null");
executeValidCommand("kbase-logout");

# USER2 checks runs some admin commands that all pass, and approves the module
executeValidCommand("kbase-login $user2 -p $pwd2");
$out = executeValidCommand("ws-typespec-register --admin '{\"command\":\"listAdmins\"}'");
$outobj=$json_parser->decode($out);
my $found; foreach my $usr (@$outobj) { if ($usr eq $user2) { $found = 1; } }
ok(defined($found),"user 2 named '$user2' should be on list of admins");

$out = executeValidCommand("ws-typespec-register --admin '{\"command\":\"listModRequests\"}'");
$outobj=$json_parser->decode($out);
ok($outobj->[0]->{moduleName} eq 'KB', "module KB should be first (and only) in list of requested modules");
ok($outobj->[0]->{ownerUserId} eq $user1, "module KB should be requested by $user1, was requested by ".$outobj->[0]->{ownerUserId});

$out = executeValidCommand("ws-typespec-register --admin '{\"command\":\"approveModRequest\",\"module\":\"KB\"}'");
$out = executeValidCommand("kbase-logout");

# USER1 logs back in, cannot request the module KB again, but can register the typespec, commit the module and add types
executeValidCommand("kbase-login $user1 -p $pwd1");
executeInvalidCommand("ws-typespec-register --request KB 2> /dev/null");
executeValidCommand("ws-typespec-register -t test/scripts/files/KB.spec");
executeValidCommand("ws-typespec-register --typespec test/scripts/files/KB.spec");
executeValidCommand("ws-typespec-register --typespec test/scripts/files/KB.spec --add 'Feature;FeatureGroup;Genome;RandomObject'");
executeValidCommand("ws-typespec-register --typespec test/scripts/files/KB.spec --add 'Feature;FeatureGroup;Genome;RandomObject' --commit");

# USER1 can see the new module, list the types in the module
$out = executeValidCommand("ws-typespec-list");
ok($out =~ m/KB/,"KB appears on the list of modules for $user1");

$out = executeValidCommand("ws-typespec-list KB");
ok($out =~ m/KB\.Feature/,"KB.Feature appears on the list of modules for $user1");
ok($out =~ m/KB\.FeatureGroup/,"KB.FeatureGroup appears on the list of modules for $user1");
ok($out =~ m/KB\.Genome/,"KB.Genome appears on the list of modules for $user1");
ok($out =~ m/KB\.RandomObject/,"KB.RandomObject appears on the list of modules for $user1");
$out = executeValidCommand("ws-typespec-list KB.Feature");
ok($out =~ m/KB\.Feature-0\.1/,"KB.Feature-0.1 info is displayed");
$out = executeValidCommand("ws-typespec-list KB.Feature-0.1");
ok($out =~ m/KB\.Feature-0\.1/,"KB.Feature-0.1 info is displayed");
$out = executeInvalidCommand("ws-typespec-list KB.Feature-2.0  2> /dev/null");
executeValidCommand("kbase-logout");

# USER2 can see the module, but cannot list types until it is released, but can get type info if
# version number is provided
executeValidCommand("kbase-login $user2 -p $pwd2");
$out = executeValidCommand("ws-typespec-list");
ok($out =~ m/KB/,"KB appears on the list of modules for $user2");
$out = executeInvalidCommand("ws-typespec-list KB  2> /dev/null");
$out = executeInvalidCommand("ws-typespec-list KB.Feature  2> /dev/null");
$out = executeValidCommand("ws-typespec-list KB.Feature-0.1");
ok($out =~ m/KB\.Feature-0\.1/,"KB.Feature-0.1 info is displayed");
executeValidCommand("kbase-logout");

# USER1 can release the module, version number bumped to 1.0
executeValidCommand("kbase-login $user1 -p $pwd1");
executeValidCommand("ws-typespec-register --release KB");
$out = executeValidCommand("ws-typespec-list KB.Feature");
ok($out =~ m/KB\.Feature-1\.0/,"KB.Feature-1.0 info is displayed");
executeValidCommand("kbase-logout");

# User that is not logged in can list the module now
$out = executeValidCommand("ws-typespec-list");
ok($out =~ m/KB/,"KB appears on the list of modules for users that are not logged in");

$out = executeValidCommand("ws-typespec-list KB");
ok($out =~ m/KB\.Feature/,"KB.Feature appears on the list of modules for users that are not logged in");
ok($out =~ m/KB\.FeatureGroup/,"KB.FeatureGroup appears on the list of modules for users that are not logged in");
ok($out =~ m/KB\.Genome/,"KB.Genome appears on the list of modules for users that are not logged in");
ok($out =~ m/KB\.RandomObject/,"KB.RandomObject appears on the list of modules for users that are not logged in");
$out = executeValidCommand("ws-typespec-list KB.Feature");
ok($out =~ m/KB\.Feature-1\.0/,"KB.Feature-1.0 info is displayed");
$out = executeValidCommand("ws-typespec-list KB.Feature-1.0");
ok($out =~ m/KB\.Feature-1\.0/,"KB.Feature-1.0 info is displayed");
executeInvalidCommand("ws-typespec-list KB.Feature-2.0  2> /dev/null");


# test that we can save and retrieve an object as user 2 without error
executeValidCommand("kbase-login $user2 -p $pwd2");
executeValidCommand("ws-createws typeRegTest");
executeValidCommand("ws-workspace typeRegTest");
executeValidCommand('ws-load KB.Feature myfeature \'{"id":"f1","name":"cheB","type":"something","length":150}\'');
executeValidCommand('ws-get myfeature');
executeValidCommand('ws-get --prov myfeature');
executeValidCommand('ws-get --prov-old-style myfeature');
executeValidCommand('ws-get --meta myfeature');
executeValidCommand("kbase-logout");


# TODO: add tests for removing types


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

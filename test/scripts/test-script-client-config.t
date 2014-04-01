#!/usr/bin/perl
# Test the workspace scripts that handle setting the client config
# in other words, we are testing ws-workspace, ws-url, kbase-login
# kbase-logout, and kbase-whoami
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
my $testurl = getLocalTestURL();
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



$out = executeInvalidCommand("ws-workspace madeupfakews 2> /dev/null");
checkConfigVariableDefined("workspace_deluxe.$user1",$user1,$clientCfgFile);

print $out;





done_testing();
exit 0;

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

my $url;

#######################################################
# [tree-get-tree] script tests
$out = `tree-get-tree -h --url $url`;
$exit_code = ($? >> 8);
ok($exit_code==0,'tree-get-tree with help returns error exit code 0');
ok($out,'tree-get-tree with help returns a message');

$out = `tree-get-tree madeUpID --url $url`;
$exit_code = ($? >> 8);
ok($exit_code==0,'tree-get-tree with bogus treeId still exits with error code 0');
ok($out eq '','tree-get-tree with bogus treeId returns nothing');

$out = `tree-get-tree 'kb|tree.0' --url $url`;
$exit_code = ($? >> 8);
ok($exit_code==0,'tree-get-tree with real treeId  exits with error code 0');
ok($out,'tree-get-tree with real treeId returns something');

$out = `tree-get-tree -m 'kb|tree.0' --url $url`;
$exit_code = ($? >> 8);
ok($exit_code==0,'tree-get-tree with real treeId and -m flag exits with error code 0');
ok($out=~m/\[leaf_count\]/g,'tree-get-tree with real treeId and -m flag returns with string indicating meta data was returned');


#######################################################
# [tree-get-leaf-nodes] script tests
$out = `echo '' | tree-get-leaf-nodes --url $url`;
$exit_code = ($? >> 8);
ok($exit_code!=0,'tree-get-leaf-nodes with no parameters returns error exit code that is not 0');
ok($out,'tree-get-tree with no parameters returns a message');

$out = `tree-get-leaf-nodes "(a,b,c,d,e,f,g)root;" --url $url`;
$exit_code = ($? >> 8);
ok($exit_code==0,'tree-get-leaf-nodes with a tree returned with error code 0');
ok($out eq "a\nb\nc\nd\ne\nf\ng\n",'tree-get-leaf-nodes with a tree returns proper output');

#######################################################
# [tree-find-tree-ids] script tests
$out = `tree-find-tree-ids --help --url $url`;
$exit_code = ($? >> 8);
ok($exit_code==0,'tree-find-tree-ids with long help flag returns exit code 0');
ok($out,'tree-find-tree-ids with long help flag returns some text');


#######################################################
# [tree-find-alignment-ids] script tests
$out = `tree-find-alignment-ids --help --url $url`;
$exit_code = ($? >> 8);
ok($exit_code==0,'tree-find-alignment-ids with long help flag returns exit code 0');
ok($out,'tree-find-alignment-ids with long help flag returns some text');


#######################################################
# [tree-relabel-node-names] script tests
$out = `tree-relabel-node-names --help --url $url`;
$exit_code = ($? >> 8);
ok($exit_code==0,'tree-relabel-node-names with long help flag returns exit code 0');
ok($out,'tree-relabel-node-names with long help flag returns some text');


#######################################################
# [tree-compute-abundance-profile] script tests
$out = `tree-compute-abundance-profile --help --url $url`;
$exit_code = ($? >> 8);
ok($exit_code==0,'tree-compute-abundance-profile with long help flag returns exit code 0');
ok($out,'tree-compute-abundance-profile with long help flag returns some text');


#######################################################
# [tree-filter-abundance-profile] script tests
$out = `tree-filter-abundance-profile-column --help`;
$exit_code = ($? >> 8);
ok($exit_code==0,'tree-filter-abundance-profile-column with long help flag returns exit code 0');
ok($out,'tree-filter-abundance-profile with long help flag returns some text');


#######################################################
# [tree-normalize-abundance-profile] script tests
$out = `tree-normalize-abundance-profile --help --url $url`;
$exit_code = ($? >> 8);
ok($exit_code==0,'tree-normalize-abundance-profile with long help flag returns exit code 0');
ok($out,'tree-normalize-abundance-profile with long help flag returns some text');



#######################################################
# [tree-remove-nodes] script tests
$out = `tree-remove-nodes --help --url $url`;
$exit_code = ($? >> 8);
ok($exit_code==0,'tree-remove-nodes with long help flag returns exit code 0');
ok($out,'tree-remove-nodes with long help flag returns some text');


#######################################################
# [tree-html-add-boxes] script tests
$out = `tree-html-add-boxes --help`;
$exit_code = ($? >> 8);
ok($exit_code==0,'tree-html-add-boxes with long help flag returns exit code 0');
ok($out,'tree-html-add-boxes with long help flag returns some text');


#######################################################
# [tree-html-relabel-leaves] script tests
$out = `tree-html-relabel-leaves --help --url $url`;
$exit_code = ($? >> 8);
ok($exit_code==0,'tree-html-relabel-leaves with long help flag returns exit code 0');
ok($out,'tree-html-relabel-leaves with long help flag returns some text');

done_testing();

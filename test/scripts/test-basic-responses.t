#!/usr/bin/env perl
# Very simple test to make sure that all WS scripts are on the path and return proper error code
#  author:  msneddon
#  created: jan 2014
use strict;
use warnings;

use Test::More;
use Data::Dumper;


#my $url = "http://localhost:7047";

# declare some variables we use over and over 
my ($out,$exit_code);

#######################################################

my $commandNames = [
    "ws-clone",
    "ws-copy",
    "ws-createws",
    "ws-delete",
    "ws-deletews",
    "ws-get",
    "ws-getsubset",
    "ws-history",
    "ws-list",
    "ws-listobj",
    "ws-load",
    "ws-rename",
    "ws-revert",
    "ws-share",
    "ws-typespec-list",
    "ws-typespec-register",
    "ws-url",
    "ws-workspace"
];

foreach my $command (@$commandNames) {
    $out = `$command --help`;
    $exit_code = ($? >> 8);
    ok($exit_code==0,"$command with long help flag returns exit code 0");
    ok($out,"$command with long help flag returns some text");
    
    $out = `$command -h`;
    $exit_code = ($? >> 8);
    ok($exit_code==0,"$command with short help flag returns exit code 0");
    ok($out,"$command with short help flag returns some text");
}


done_testing();

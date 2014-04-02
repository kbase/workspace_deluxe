#!/usr/bin/perl
#
#  This set of tests simply checks to see if the service is available and if we can access a single basic
#  RPC call, and get something back.  It does not check the validity of the response, nor the
#  auto-gen client libs normally used to retrieve the response.
#
#  author:  msneddon
#  created: 5/21/2012
use strict;
use warnings;
use Data::Dumper;
use lib "test/scripts";

use WsTestConfig qw(getLocalTestURL getDefaultURL);

use Test::More tests => 6;

#  MAKE SURE WE LOCALLY HAVE JSON RPC LIBS (TEST 1)
use_ok("JSON::RPC::Client");
my $client = new JSON::RPC::Client;

# DETERMINE THE URL TO USE BASED ON THE CONFIG MODULE
my $url=getLocalTestURL();

# MAKE A VALID RPC CALL (TEST 2,3,4)
my $callobj = {
    method  => 'Workspace.ver',
    params  => [ ],
};
my $res = $client->call($url, $callobj);
ok($client->status_line =~ m/^200/,"test a valid rpc call to $url");
if (!($client->status_line =~ m/^200/)) {
    die "Workspace server is not up at the test URL.  Stopping tests."
}

if(!($client->status_line =~ m/^200/)) {
    print "SERVER RESPONSE: '".$client->status_line."'\n";
    print "SERVICE MSSG: '".$client->error_message."\n";
}
ok($res,"test that a valid RPC call returned something");
if($res) { ok(!$res->is_error,"test that valid RPC call returned a json ($res)" ); }
else {ok(0,"test that a valid RPC call returned a json"); }
#print  $res->result."\n";

# MAKE A BOGUS RPC CALL (TEST 5,6)
$callobj = {
    method  => 'made_up_call_which_does_not_exist!',
    params  => [ '123fakeParameter' ],
};
$res = $client->call($url, $callobj);
ok($client->status_line =~ m/^500/,"test invalid rpc call");
ok(!$res,"test invalid rpc call returned nothing");

done_testing();

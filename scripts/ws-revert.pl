#!/usr/bin/env perl
########################################################################
# adpated for WS 0.1.0+ by Michael Sneddon, LBL
# Original authors: Christopher Henry, Scott Devoid, Paul Frybarger
# Contact email: chenry@mcs.anl.gov
# Development location: Mathematics and Computer Science Division, Argonne National Lab
########################################################################
use strict;
use warnings;
use Getopt::Long::Descriptive;
use Text::Table;
use JSON -support_by_pp;
use Bio::KBase::workspace::ScriptHelpers qw(get_ws_client workspace parseObjectMeta parseWorkspaceMeta);

my $serv = get_ws_client();
#Defining globals describing behavior
my $primaryArgs = ["Object ID or Name","Version to revert to"];
my $servercommand = "revert_object";
my $translation = {
	"Object ID or Name" => "id",
	workspace => "workspace",
	"Version to revert to" => "version"
};
#Defining usage and options
my ($opt, $usage) = describe_options(
    'ws-revert <'.join("> <",@{$primaryArgs}).'> %o',
    [ 'workspace|w:s', 'Workspace name or ID', {"default" => workspace()} ],
    [ 'showerror|e', 'Show full stack trace of any errors in execution',{"default"=>0}],
    [ 'help|h|?', 'Print this usage information' ]
);
$usage = "\nNAME\n  ws-revert -- revert an object to an old version\n\nSYNOPSIS\n  ".$usage;
$usage .= "\n";
if (defined($opt->{help})) {
	print $usage;
	exit;
}
#Processing primary arguments
foreach my $arg (@{$primaryArgs}) {
	$opt->{$arg} = shift @ARGV;
	if (!defined($opt->{$arg})) {
		print $usage;
		exit;
	}
}
#Instantiating parameters
my $versionRaw = $opt->{"Version to revert to"};
my $versionString='';
if (defined($opt->{"Version to revert to"})) {
	$versionString="/".$opt->{"Version to revert to"};
}

my $params = {
	      ref => $opt->{workspace} ."/".$opt->{"Object ID or Name"} .$versionString,
	      };

#Calling the server
my $output;
if ($opt->{showerror} == 0) {
	eval { $output = $serv->$servercommand($params); };
	if($@) {
		print "Cannot revert object!\n";
		print STDERR $@->{message}."\n";
		if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
		print STDERR "\n";
		exit 1;
	}
} else {
    $output = $serv->$servercommand($params);
}

#Checking output and report results
print "Object successfully reverted to version " . $opt->{"Version to revert to"} . ".\n";
exit 0;

#!/usr/bin/env perl
########################################################################
# adpated for WS 0.1.0+ by Michael Sneddon, LBL
# Original authors: Christopher Henry, Scott Devoid, Paul Frybarger
# Contact email: mwsneddon@lbl.gov or chenry@mcs.anl.gov
########################################################################
use strict;
use warnings;
use Getopt::Long::Descriptive;
use Text::Table;
use JSON -support_by_pp;
use Bio::KBase::workspace::ScriptHelpers qw(get_ws_client workspace getObjectRef parseObjectMeta parseWorkspaceMeta);

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
if (scalar(@ARGV) > scalar(@{$primaryArgs})) {
	print STDERR "Too many input arguments given.  Run with -h or --help for usage information\n";
	exit 1;
}
foreach my $arg (@{$primaryArgs}) {
	$opt->{$arg} = shift @ARGV;
	if (!defined($opt->{$arg})) {
		print STDERR "Not enough input arguments provided.  Run with -h or --help for usage information\n";
		exit 1;
	}
}
#Instantiating parameters
my $versionRaw = $opt->{"Version to revert to"};



my @tokens = split(/\//, $opt->{"Object ID or Name"});
if (scalar(@tokens)>=3) {
	print STDERR "Malformed Object ID or Name - you cannot specify a version of an object to revert.\n";
	exit 1;
}
	

my $params = {
	      ref => getObjectRef($opt->{workspace}, $opt->{"Object ID or Name"},undef)."/".$opt->{"Version to revert to"},
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

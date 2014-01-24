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
use Bio::KBase::workspace::ScriptHelpers qw(get_ws_client workspace parseWorkspaceInfo);

my $serv = get_ws_client();
#Defining globals describing behavior
my $primaryArgs = ["Destination workspace name"];
my $servercommand = "clone_workspace";
my $translation = {
	"globalread"=>"globalread",
	"description"=>"description"
};
#Defining usage and options
my ($opt, $usage) = describe_options(
    'ws-clone <'.join("> <",@{$primaryArgs}).'> %o',
    [ 'workspace|w:s', 'Name of the workspace to clone', {"default" => workspace()} ],
    [ 'description|d=s', 'New workspace description (1000 characters max)',{"default"=>''}],
    [ 'globalread|g=s', 'Set global read permissions (r=read,n=none)',{"default"=>'n'}],
    [ 'showerror|e', 'Show full stack trace of any errors in execution',{"default"=>0}],
    [ 'help|h|?', 'Print this usage information' ]
);
$usage = "\nNAME\n  ws-clone -- create an exact cloned copy of an existing workspace\n\nSYNOPSIS\n  ".$usage;
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
my $wsi = {};
if ($opt->{workspace} =~ /^\d+$/ ) { #is ID
	$wsi->{id}=$opt->{workspace}+0;
} else { #is name
	$wsi->{workspace}=$opt->{workspace};
}
#Instantiating parameters
my $params = {
	      wsi => $wsi,
	      workspace => $opt->{"Destination workspace name"}
	      };
foreach my $key (keys(%{$translation})) {
	if (defined($opt->{$key})) {
		$params->{$translation->{$key}} = $opt->{$key};
	}
}
#Calling the server
my $output;
if ($opt->{showerror} == 0){
	eval { $output = $serv->$servercommand($params); };
	if($@) {
		print "Workspace could not be cloned!\n";
		print STDERR $@->{message}."\n";
		if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
		print STDERR "\n";
		exit 1;
	}
    
} else {
	$output = $serv->$servercommand($params);
}

my $obj = parseWorkspaceInfo($output);
print "Workspace with ".$obj->{objects}." objects cloned into: ".$opt->{"Destination workspace name"}." with id: ".$obj->{id}."\n";

exit 0;
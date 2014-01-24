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
use Bio::KBase::workspace::ScriptHelpers qw(get_ws_client parseWorkspaceInfo);

my $serv = get_ws_client();
#Defining globals describing behavior
my $primaryArgs = ["Workspace Name"];
my $translation = {
	"Workspace Name" => "workspace",
};
#Defining usage and options
my ($opt, $usage) = describe_options(
    'ws-deletews <'.join("> <",@{$primaryArgs}).'> %o',
    [ 'restore', 'Restore the specified workspace', {"default" => 0} ],
    [ 'showerror|e', 'Show full stack trace of any errors in execution',{"default"=>0}],
    [ 'help|h|?', 'Print this usage information' ],
);
$usage = "\nNAME\n  ws-deletews -- delete/undelete a workspace and all contained objects\n\nSYNOPSIS\n  ".$usage;
$usage .= "\n";
if (defined($opt->{help})) {
	print $usage;
	exit 0;
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
my $params = { };
foreach my $key (keys(%{$translation})) {
	if (defined($opt->{$key})) {
		$params->{$translation->{$key}} = $opt->{$key};
	}
}

if ($params->{workspace} =~ /^\d+$/ ) { #is ID
	$params->{id} = $params->{workspace}+0;
	delete($params->{workspace});
}

#Instantiating parameter
#Calling the server
my $output;
if ($opt->{restore}) {
	if ($opt->{showerror} == 0){
		eval { $serv->undelete_workspace($params); };
		if($@) {
			print "Cannot restore workspace!\n";
			print STDERR $@->{message}."\n";
			if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
			print STDERR "\n";
			exit 1;
		}
	} else {
		$serv->undelete_workspace($params);
	}
	print "Workspace and all objects successfully restored.\n";
} else {
	
	if ($opt->{showerror} == 0){
		eval { $serv->delete_workspace($params); };
		if($@) {
			print "Cannot delete workspace!\n";
			print STDERR $@->{message}."\n";
			if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
			print STDERR "\n";
			exit 1;
		}
	} else {
		$serv->delete_workspace($params);
	}
	print "Workspace and all contained objects successfully deleted.\n";
}
exit 0;

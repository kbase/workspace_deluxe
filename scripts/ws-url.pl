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
use Bio::KBase::workspace::ScriptHelpers qw(workspaceURL);
#Defining globals describing behavior
my $primaryArgs = ["New server URL"];
#Defining usage and options
my ($opt, $usage) = describe_options(
    'ws-url <'.join("> <",@{$primaryArgs}).'> %o',
    [ 'help|h|?', 'Print this usage information' ],
);
$usage = "\nNAME\n  ws-url -- view/set the URL of the workspace service used by WS commands\n\nSYNOPSIS\n  ".$usage;
$usage .= "\nDESCRIPTION\n";
$usage .= "    The Workspace commands connect to a remote Workspace service.  This command sets the\n";
$usage .= "    URL endpoint used by the other Workspace commands.  To reset to the default production\n";
$usage .= "    url, run the command: \"ws-url default\"\n";
$usage .= "\n";
if (defined($opt->{help})) {
	print $usage;
    exit;
}
if (scalar(@ARGV) > scalar(@{$primaryArgs})) {
	print STDERR "Too many input arguments given.  Run with -h or --help for usage information.\n";
	exit 1;
}

print "Current URL is: \n".workspaceURL($ARGV[0])."\n";

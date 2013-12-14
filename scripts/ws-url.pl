#!/usr/bin/env perl
########################################################################
# Authors: Christopher Henry, Scott Devoid, Paul Frybarger
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
    'kbws-url <'.join("> <",@{$primaryArgs}).'> %o',
    [ 'help|h|?', 'Print this usage information' ],
);
if (defined($opt->{help})) {
	print $usage;
    exit;
}
print "Current URL is: \n".workspaceURL($ARGV[0])."\n";

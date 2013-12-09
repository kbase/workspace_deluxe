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
use Bio::KBase::workspace::ScriptHelpers qw(workspace get_ws_client);
#Defining globals describing behavior
my $primaryArgs = ["New workspace"];
#Defining usage and options
my ($opt, $usage) = describe_options(
    'kbws-workspace <'.join("> <",@{$primaryArgs}).'> %o',
    [ 'help|h|?', 'Print this usage information' ],
);
if (defined($opt->{help})) {
	print $usage;
    exit;
}

my $workspace = workspace($ARGV[0]);

print "Current workspace set to:\n".$workspace."\n";

# check that the workspace actually exists
my $serv = get_ws_client();

my $wsinfo;
eval { $wsinfo = $serv->get_workspace_info({workspace=>$workspace}); };
if($@) {
	print "Cannot confirm that the workspace exists!\n";
	print STDERR $@->{message}."\n";
	if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
	print STDERR "\n";
	exit 1;
}

my $table = Text::Table->new(
    'Id', 'Name', 'Owner', 'Last_Modified', 'Size', 'Permission', 'GlobalAccess'
    );
my @infoList; push @infoList, $wsinfo;
$table->load(@infoList);
print $table;


exit 0;
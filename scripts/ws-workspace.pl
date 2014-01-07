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
use Bio::KBase::workspace::ScriptHelpers qw(workspace get_ws_client);
#Defining globals describing behavior
my $primaryArgs = ["Workspace Name"];
#Defining usage and options
my ($opt, $usage) = describe_options(
    'ws-workspace <'.join("> <",@{$primaryArgs}).'> %o',
    [ 'help|h|?', 'Print this usage information' ],
);
$usage = "\nNAME\n  ws-workspace -- view/set the default workspace used by WS commands\n\nSYNOPSIS\n  ".$usage;
$usage .= "\nDESCRIPTION\n";
$usage .= "    Workspace commands will attempt to perform operations in the Workspace with the name\n";
$usage .= "    set by this command.  Most Workspace commands allow you to override this default by\n";
$usage .= "    setting a \"--workspace\" flag.\n";
$usage .= "\n";
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
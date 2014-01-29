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
use Bio::KBase::workspace::ScriptHelpers qw(get_ws_client);

my $serv = get_ws_client();
#Defining globals describing behavior
my $primaryArgs = [];
my $servercommand = "list_workspace_info";
my $translation = {
};
#Defining usage and options
my ($opt, $usage) = describe_options(
    'ws-list %o',
    [ 'column|c:i','Sort by this column number (first column = 1)' ],
    [ 'deleted|d', 'Include deleted workspaces',{"default"=>0}],
    [ 'global|g', 'Include globally readable workspaces',{"default"=>0}],
    [ 'showerror|e', 'Show full stack trace of any errors in execution',{"default"=>0}],
    [ 'help|h|?', 'Print this usage information' ]
);
$usage = "\nNAME\n  ws-list -- list the workspaces to which you have access\n\nSYNOPSIS\n  ".$usage;
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
if (defined($opt->{column})) {
	if ($opt->{column} <= 0 || $opt->{column} >7) {
		print STDERR "Invalid column number given.  Valid column numbers for sorting are:\n";
		print STDERR "    1 = Workspace Id\n";
		print STDERR "    2 = Workspace Name\n";
		print STDERR "    3 = Owner\n";
		print STDERR "    4 = Last Modified Date\n";
		print STDERR "    5 = Size of Workspace (number of objects)\n";
		print STDERR "    6 = Your permission (r=read,w=read/write,a=admin)\n";
		print STDERR "    7 = Global access permission (n=none,r=read)\n";
		exit 1;
	}
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
if (defined($opt->{global})) {
	if ($opt->{global}) {
		$params->{"excludeGlobal"} = 0;
	} else {
		$params->{"excludeGlobal"} = 1;
	}
}
if (defined($opt->{deleted})) {
	$params->{"showDeleted"} = $opt->{deleted};
}

#Calling the server
my $output;
if ($opt->{showerror} == 0){
	eval {
	    $output = $serv->$servercommand($params);
	};
	if($@) {
		print "Cannot list workspaces! Run with -e for full stack trace.\n";
		print STDERR $@->{message}."\n";
		if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
		print STDERR "\n";
		exit 1;
	}
}else{
    $output = $serv->$servercommand($params);
}

#errors are now handled above ...

#print results
my $table = Text::Table->new(
    'Id', 'WsName', 'Owner', 'Last_Modified', 'Size', 'Permission', 'GlobalAccess'
    );
my @sorted_tbl = @$output;
if (defined($opt->{column})) {
	if ($opt->{column}==5) {
		#size is numeric, so sort numerically, largest first
		@sorted_tbl = sort { $b->[$opt->{column}-1] <=> $a->[$opt->{column}-1] } @sorted_tbl;
	} elsif ( $opt->{column}==1) {
		#id is numeric, so sort numerically, largest last
		@sorted_tbl = sort { $a->[$opt->{column}-1] <=> $b->[$opt->{column}-1] } @sorted_tbl;
	} else {
		@sorted_tbl = sort { $a->[$opt->{column}-1] cmp $b->[$opt->{column}-1] } @sorted_tbl;
	}
}
$table->load(@sorted_tbl);
print $table;
exit 0;

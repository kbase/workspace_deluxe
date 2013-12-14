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
use Bio::KBase::workspace::ScriptHelpers qw(get_ws_client);

my $serv = get_ws_client();
#Defining globals describing behavior
my $primaryArgs = [];
my $servercommand = "list_workspace_info";
my $translation = {
};
#Defining usage and options
my ($opt, $usage) = describe_options(
    'kbws-list %o',
    [ 'deleted|d', 'List deleted workspaces',{"default"=>0}],
    [ 'global|g', 'List globally readable workspaces',{"default"=>0}],
    [ 'showerror|e', 'Show any errors in execution',{"default"=>0}],
    [ 'help|h|?', 'Print this usage information' ]
);
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
$table->load(@$output);
print $table;
exit 0;

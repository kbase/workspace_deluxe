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
use Bio::KBase::workspace::ScriptHelpers qw(get_ws_client workspace parseObjectMeta parseWorkspaceMeta);

my $serv = get_ws_client();
#Defining globals describing behavior
my $primaryArgs = ["Original Object ID or Name","New Name"];
my $servercommand = "rename_object";
my $translation = {};
#Defining usage and options
my ($opt, $usage) = describe_options(
    'kbws-copy <'.join("> <",@{$primaryArgs}).'> %o',
    [ 'workspace|s:s', 'ID or Name of workspace containing object to rename', {"default" => workspace()} ],
    [ 'showerror|e', 'Show any errors in execution',{"default"=>0}],
    [ 'help|h|?', 'Print this usage information' ]
);
if (defined($opt->{help})) {
	print $usage;
	exit 1;
}
#Processing primary arguments
foreach my $arg (@{$primaryArgs}) {
	$opt->{$arg} = shift @ARGV;
	if (!defined($opt->{$arg})) {
		print $usage;
		exit 1;
	}
}
#Instantiating parameters
my $params = {
	obj => { ref => $opt->{workspace}."/".$opt->{"Original Object ID or Name"} },
	new_name   => $opt->{"New Name"}
};

#Calling the server
my $output;
if ($opt->{showerror} == 0){
	eval { $output = $serv->$servercommand($params); };
	if($@) {
		print "Object could not be renamed!\n";
		print STDERR $@->{message}."\n";
		if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
		print STDERR "\n";
		exit 1;
	}
} else {
	$output = $serv->$servercommand($params);
}

print "Object renamed successfully.\n";

exit 0;
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
my $primaryArgs = ["Original object ID","New object ID"];
my $servercommand = "copy_object";
my $translation = {
	"New object ID" => "new_id",
	"Original object ID" => "source_id"
};
#Defining usage and options
my ($opt, $usage) = describe_options(
    'kbws-copy <'.join("> <",@{$primaryArgs}).'> %o',
    [ 'workspace|s:s', 'ID or Name of workspace to copy from', {"default" => workspace()} ],
    [ 'newworkspace|n:s', 'ID or Name of workspace to copy to', {"default" => workspace()} ],
    [ 'version|v=i', 'Version of the object to copy' ],
    [ 'showerror|e', 'Set as 1 to show any errors in execution',{"default"=>0}],
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
		exit 1;
	}
}
#Instantiating parameters
my $versionString='';
if (defined($opt->{version})) {
	$versionString="/".$opt->{version};
}
my $params = {
	from => { ref => $opt->{workspace} ."/".$opt->{"Original object ID"}.$versionString },
	to   => { ref => $opt->{newworkspace} ."/".$opt->{"New object ID"} }
};

#Calling the server
my $output;
if ($opt->{showerror} == 0){
	eval { $output = $serv->$servercommand($params); };
	if($@) {
		print "Object could not be copied!\n";
		print STDERR $@->{message}."\n";
		if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
		print STDERR "\n";
		exit 1;
	}
} else {
	$output = $serv->$servercommand($params);
}
#Checking output and report results
if (!defined($output)) {
	print "Object could not be copied!\n";
} else {
	print "Object copied successfully.\n";
}

exit 0;
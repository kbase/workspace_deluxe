#!/usr/bin/env perl
########################################################################
# adpated for WS 0.1.0+ by Michael Sneddon, LBL
# Original authors: Christopher Henry, Scott Devoid, Paul Frybarger
# Contact email: mwsneddon@lbl.gov or chenry@mcs.anl.gov
########################################################################
use strict;
use warnings;
use Getopt::Long::Descriptive;
use Text::Table;
use Bio::KBase::workspace::ScriptHelpers qw(get_ws_client workspace getObjectRef parseObjectMeta parseWorkspaceMeta);

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
    'ws-copy <'.join("> <",@{$primaryArgs}).'> %o',
    [ 'workspace|s:s', 'ID or Name of workspace to copy from', {"default" => workspace()} ],
    [ 'newworkspace|n:s', 'ID or Name of workspace to copy to', {"default" => workspace()} ],
    [ 'version|v=i', 'Version of the object to copy' ],
    [ 'showerror|e', 'Show full stack trace of any errors in execution',{"default"=>0}],
    [ 'help|h|?', 'Print this usage information' ]
);
$usage = "\nNAME\n  ws-copy -- copy an object\n\nSYNOPSIS\n  ".$usage;
$usage .= "\nDESCRIPTION\n";
$usage .= "    Create a copy of an existing object. If you do not specify a version \n";
$usage .= "    and the object is copied to a new name, the entire version history of \n";
$usage .= "    the object is copied. If the version is specified, or if an object by \n";
$usage .= "    the new name already exists, only the version specified (or the latest \n";
$usage .= "    version) is copied.\n\n";

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
#Instantiating parameters
my $params = {
	from => { ref => getObjectRef($opt->{workspace},$opt->{"Original object ID"},$opt->{version}) },
	to   => { ref => getObjectRef($opt->{newworkspace},$opt->{"New object ID"},undef) }
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
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
use JSON -support_by_pp;
use Bio::KBase::workspace::ScriptHelpers qw(get_ws_client workspace getObjectRef parseObjectMeta parseWorkspaceMeta);

my $serv = get_ws_client();
#Defining globals describing behavior
my $primaryArgs = ["Object ID or Name", "Subset path"];
my $servercommand = "get_object_subset";
my $translation = {
	"Object ID or Name" => "id",
	workspace => "workspace",
	version => "version"
};
#Defining usage and options
my ($opt, $usage) = describe_options(
    'ws-getsubset <'.join("> <",@{$primaryArgs}).'> %o',
    [ 'workspace|w:s', 'Workspace name or ID', {"default" => workspace()} ],
    [ 'version|v:i', 'Get object with this version number' ],
    [ 'pretty|p', 'Pretty print the JSON object' ],
    [ 'showerror|e', 'Show full stack trace of any errors in execution',{"default"=>0}],
    [ 'help|h|?', 'Print this usage information' ]
);

$usage = "\nNAME\n  ws-getsubset -- get a subset of a data object\n\nSYNOPSIS\n  ".$usage;
$usage .= "\nDESCRIPTION\n";
$usage .= "  Get an object with only a subset of the object populated.  The subset is\n";
$usage .= "  specified by providing the path in the object to the sub data requested.\n";
$usage .= "\n  Syntax of subset path:\n";
$usage .= "    Identify a sub portion of an object by providing the path, delimited by\n";
$usage .= "    a slash (/), to that portion of the object. Thus the path may not have\n";
$usage .= "    slashes in the structure or mapping keys. For this command only, multple\n";
$usage .= "    paths may be given if delimited by a semicolon (;). Examples:\n";
$usage .= "       /foo/bar/3 - specifies the bar key of the foo mapping and the 3rd\n";
$usage .= "                    entry of the array if bar maps to an array or the value mapped to\n";
$usage .= "                    the string \"3\" if bar maps to a map.\n";
$usage .= "      /foo/bar/[*]/baz - specifies the baz field of all the objects in the\n";
$usage .= "                         list mapped by the bar key in the map foo.\n";
$usage .= "      /foo/*/baz - specifies the baz field of all the objects in the\n";
$usage .= "                   values of the foo mapping.\n\n";

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

# split the path list by semicolon
my $path = $opt->{"Subset path"};
my @paths = split(/;/,$path);

my $params = [{
	      ref => getObjectRef($opt->{workspace},$opt->{"Object ID or Name"},$opt->{version}),
	      included => \@paths
	      }];

#Calling the server
my $output;
if ($opt->{showerror} == 0) {
	eval { $output = $serv->$servercommand($params); };
	if($@) {
		print "Cannot get object!\n";
		print STDERR $@->{message}."\n";
		if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
		print STDERR "\n";
		exit 1;
	}
} else {
    $output = $serv->$servercommand($params);
}

#Checking output and report results
if (scalar(@$output)>0) {
	foreach my $object (@$output) {
		if (defined($object->{data})) {
			if (ref($object->{data})) {
				print to_json( $object->{data}, { utf8 => 1, pretty => $opt->{pretty} } )."\n";
			} else {
				print $object->{data}."\n";
			}
		} else {
			print "No data retrieved!\n";
		}
	}
} else {
	print "No data retrieved!\n";
}
exit 0;

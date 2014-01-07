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
use Bio::KBase::workspace::ScriptHelpers qw(get_ws_client workspace parseObjectMeta parseWorkspaceMeta printObjectInfo);

my $serv = get_ws_client();
#Defining globals describing behavior
my $primaryArgs = ["Object ID or Name"];
my $servercommand = "get_objects";
my $translation = {
	"Object ID or Name" => "id",
	workspace => "workspace",
	version => "version"
};
#Defining usage and options
my ($opt, $usage) = describe_options(
    'ws-get <'.join("> <",@{$primaryArgs}).'> %o',
    [ 'workspace|w:s', 'Workspace name or ID', {"default" => workspace()} ],
    [ 'version|v:i', 'Get object with this version number' ],
    [ 'pretty|p', 'Pretty print the JSON object' ],
    [ 'prov', 'Only output provenance data instead (also given as a JSON object)' ],
    [ 'meta|m', 'Get object meta data instead of actual data' ],
    [ 'showerror|e', 'Show full stack trace of any errors in execution',{"default"=>0}],
    [ 'help|h|?', 'Print this usage information' ]
);
$usage = "\nNAME\n  ws-get -- get an object or its provenance/meta info\n\nSYNOPSIS\n  ".$usage;
$usage .= "\n";
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
my $versionString='';
if (defined($opt->{version})) {
	$versionString="/".$opt->{version};
}

my $params = [{
	      ref => $opt->{workspace} ."/".$opt->{"Object ID or Name"} .$versionString,
	      }];

#Calling the server
if (defined($opt->{meta})) {
	# just get the meta data
	my $output;
	my $getMeta = 1;
	if ($opt->{showerror} == 0) {
		eval { $output = $serv->get_object_info($params,$getMeta); };
		if($@) {
			print "Cannot get object information!\n";
			print STDERR $@->{message}."\n";
			if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
			print STDERR "\n";
			exit 1;
		}
	} else {
	    $output = $serv->get_object_info($params,$getMeta);
	}
	
	if (scalar(@$output)>0) {
		foreach my $object_info (@$output) {
			printObjectInfo($object_info);
		}
	} else {
		print "No data retrieved!\n";
	}
} else {
	# actually get the data
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
			if (defined($opt->{"prov"})) {
				print to_json( {"provenance"=>$object->{provenance}}, { utf8 => 1, pretty => $opt->{pretty} } )."\n";
			} else {
				if (defined($object->{data})) {
					print to_json( $object->{data}, { utf8 => 1, pretty => $opt->{pretty} } )."\n";
				} else {
					print "No data retrieved!\n";
				}
			}
		}
	} else {
		print "No data retrieved!\n";
	}
}
exit 0;

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
use Bio::KBase::workspace::ScriptHelpers qw(get_ws_client workspace printObjectInfo);

my $fullCommand = "ws-load ";
foreach my $arg (@ARGV) {
	$fullCommand .= " ".$arg;
}

my $serv = get_ws_client();
#Defining globals describing behavior
my $primaryArgs = ["Object type","Object ID","Filename or data"];
my $servercommand = "save_objects";
my $translation = {
	"Object ID" => "id",
	"Object type" => "type",
	workspace => "workspace",
	command => "command"
};
#Defining usage and options
my ($opt, $usage) = describe_options(
    'ws-load <'.join("> <",@{$primaryArgs}).'> %o',
    [ 'workspace|w=s', 'Name of workspace', {"default" => workspace()} ],
    [ 'metadata|m:s', 'Filename with metadata to associate with object' ],
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
    	exit;
	}
}
#Instantiating parameters
my $params = {
};
foreach my $key (keys(%{$translation})) {
	if (defined($opt->{$key})) {
		$params->{$translation->{$key}} = $opt->{$key};
	}
}
#Handling data
#if (!defined($opt->{stringdata}) || $opt->{stringdata} != 1) {
#	$params->{json} = 1;
#}
if (-e $opt->{"Filename or data"}) {
	open(my $fh, "<", $opt->{"Filename or data"}) || return;
   	$params->{data} = "";
    while (my $line = <$fh>) {
    	$params->{data} .= $line;
    }
    close($fh);
} else {
	$params->{data} = $opt->{"Filename or data"};
}

# parse object as json
my $json_parser = JSON->new->allow_nonref->pretty;
eval {
	$params->{data} = $json_parser->decode($params->{data});
};
if($@) {
	print "Object could not be saved!  Data was not a valid JSON document!\n";
	print STDERR $@."\n";
	exit 1;
}

if (defined($opt->{metadata})) {
	if (-e $opt->{metadata}) {
		open(my $fh, "<", $opt->{metadata}) || return;
	   	$params->{metadata} = "";
	    while (my $line = <$fh>) {
	    	$params->{metadata} .= $line;
	    }
	    close($fh);
	} else {
		$params->{metadata} = $opt->{metadata};
	}
	eval {
		$params->{metadata} = $json_parser->decode($params->{metadata});
	};
	if($@) {
		print "Object could not be saved!  Meta data was not a valid JSON document!\n";
		print STDERR $@."\n";
		exit 1;
	}
}

# set provenance info
my $PA = {
		"service"=>"Workspace",
		"service_ver"=>"0.1.0",
		"script"=>"ws-load",
		"script_ver"=>"0.1.0",
		"script_command_line"=>$fullCommand
	  };
$params->{provenance} = [ $PA ];


# setup the new save_objects parameters
my $saveObjectsParams = {
		"workspace" => $params->{workspace},
		"objects" => [
			   {
				"data"  => $params->{data},
				"name"  => $params->{id},
				"type"  => $params->{type},
				"meta"  => $params->{metadata},
				"provenance" => $params->{provenance}
			   }
			]
	};

#Calling the server
my $output;
if ($opt->{showerror} == 0){
	eval { $output = $serv->$servercommand($saveObjectsParams); };
	if($@) {
		print "Object could not be saved!\n";
		print STDERR $@->{message}."\n";
		if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
		print STDERR "\n";
		exit 1;
	}
} else{
	$output = $serv->$servercommand($saveObjectsParams);
}

#Report the results
print "Object saved.  Details:\n";
if (scalar(@$output)>0) {
	foreach my $object_info (@$output) {
		printObjectInfo($object_info);
	}
} else {
	print "No details returned!\n";
}
print "\n";

exit 0;
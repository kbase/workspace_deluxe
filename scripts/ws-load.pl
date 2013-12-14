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
use Bio::KBase::workspace::ScriptHelpers qw(get_ws_client workspace parseObjectMeta printObjectMeta parseWorkspaceMeta);

my $serv = get_ws_client();
#Defining globals describing behavior
my $primaryArgs = ["Object type","Object ID","Filename or data"];
my $servercommand = "save_object";
my $translation = {
	"Object ID" => "id",
	"Object type" => "type",
    compressed => "compressed",
    workspace => "workspace",
    command => "command"
};
#Defining usage and options
my ($opt, $usage) = describe_options(
    'kbws-load <'.join("> <",@{$primaryArgs}).'> %o',
    [ 'workspace|w=s', 'ID for workspace', {"default" => workspace()} ],
    [ 'metadata|m:s', 'Filename with metadata to associate with object' ],
    [ 'compressed|c', 'Uploaded data will be compressed' , {"default" => 0} ],
    [ 'showerror|e', 'Set as 1 to show any errors in execution',{"default"=>0}],
    [ 'help|h|?', 'Print this usage information' ]
);
if (defined($opt->{help})) {
	print $usage;
    exit;
}
$opt->{command} = "kb_load";
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
	#if ($opt->{"Filename, data, or URL"} =~ /^http\:/) {
	#	$params->{retrieveFromURL} = 1;
	#}
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
}
#Calling the server
my $output;
if ($opt->{showerror} == 0){
	eval { $output = $serv->$servercommand($params); };
	if($@) {
		print "Object could not be saved!\n";
		print STDERR $@->{message}."\n";
		if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
		print STDERR "\n";
		exit 1;
	}
}else{
    $output = $serv->$servercommand($params);
}
#Checking output and report results
if (!defined($output)) {
	print "Object could not be saved!\n";
} else {
	print "Object saved.  Details:\n";
	printObjectMeta($output);
	print "\n";
}

exit 0;
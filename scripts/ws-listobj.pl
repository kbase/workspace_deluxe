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
my $primaryArgs = [];
my $servercommand = "list_objects";
my $translation = {
    showdeleted=>"showDeleted",
    showhidden=>"showHidden",
    showversions=>"showAllVersions",
    type => "type",
};
#Defining usage and options
my ($opt, $usage) = describe_options(
    'kbws-listobj %o',
    [ 'workspace|w=s', 'Name of the workspace to search', {"default" => workspace()} ],
    [ 'type|t:s','Specify that only objects of the given type should be listed'],
    [ 'showversions|v', 'show all versions of the objects',{"default"=>0}],
    [ 'showhidden|a','show all hidden objects', {"default" =>0} ],
    [ 'showdeleted|s','show all objects that have been deleted', {"default" =>0} ],
    [ 'showerror|e', 'show any errors in execution',{"default"=>0}],
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
	workspaces=>[$opt->{workspace}],
	
};
foreach my $key (keys(%{$translation})) {
	if (defined($opt->{$key})) {
		$params->{$translation->{$key}} = $opt->{$key};
	}
}

#Calling the server
my $output;
if ($opt->{showerror} == 0){
	eval { $output = $serv->$servercommand($params); };
	if($@) {
		print "Cannot list objects!\n";
		print STDERR $@->{message}."\n";
		if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
		print STDERR "\n";
		exit 1;
	}
} else {
	#use Data::Dumper; print STDERR "parameters to $servercommand :\n".Dumper($params)."\n";
	$output = $serv->$servercommand($params);
}

#Checking output and report results
if (!defined($output)) {
	print "Cannot list objects!\n";
} else {
	#tuple<0obj_id objid, 1obj_name name, 2type_string type,
	#	3timestamp save_date, 4int version, 5username saved_by,
	#	6ws_id wsid, 7ws_name workspace, 8string chsum, 9int size, 10usermeta meta>
	#	object_info;
	my $tbl = [];
	for (my $i=0; $i < @{$output};$i++) {
	    my $r = $output->[$i];
	    push(@{$tbl},[$r->[0],$r->[1],$r->[4],$r->[2],$r->[7],$r->[5],$r->[3],$r->[9]]);
	}
	my $table = Text::Table->new(
		'ID', 'ObjName', 'Vers', 'Type', 'WS','Last_modby','Moddate','Size(bytes)'
		);
	$table->load(@$tbl);
	print $table;
}

exit 0;
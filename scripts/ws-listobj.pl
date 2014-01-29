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
    'ws-listobj %o',
    [ 'workspace|w=s', 'Name of the workspace to search', {"default" => workspace()} ],
    [ 'type|t=s','Specify that only objects of the given type should be listed'],
    [ 'column|c:i','Sort by this column number (first column = 1)' ],
    [ 'megabytes|m','Report size in MB (bytes/1024^2)' ],
    [ 'showversions|v', 'Include all versions of the objects',{"default"=>0}],
    [ 'showhidden|a','Include hidden objects', {"default" =>0} ],
    [ 'showdeleted|s','Include objects that have been deleted', {"default" =>0} ],
    [ 'showerror|e', 'Show full stack trace of any errors in execution',{"default"=>0}],
    [ 'help|h|?', 'Print this usage information' ]
);
$usage = "\nNAME\n  ws-listobj -- list the objects in a workspace\n\nSYNOPSIS\n  ".$usage;
$usage .= "\n";
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

if (defined($opt->{column})) {
	if ($opt->{column} <= 0 || $opt->{column} >8) {
		print STDERR "Invalid column number given.  Valid column numbers for sorting are:\n";
		print STDERR "    1 = Object Id\n";
		print STDERR "    2 = Object Name\n";
		print STDERR "    3 = Version Number\n";
		print STDERR "    4 = Object Type\n";
		print STDERR "    5 = Containing Workspace\n";
		print STDERR "    6 = Last Modified By\n";
		print STDERR "    7 = Last Modified Date\n";
		print STDERR "    8 = Size (in bytes or MB)\n";
		exit 1;
	}
}


#Instantiating parameters
#typedef structure {
#		list<ws_name> workspaces;
#		list<ws_id> ids;
#		type_string type;
#		permission perm;
#		list<username> savedby;
#		usermeta meta;
#		boolean showDeleted;
#		boolean showOnlyDeleted;
#		boolean showHidden;
#		boolean showAllVersions;
#		boolean includeMetadata;
#	} ListObjectsParams;


my $params = {};
if ($opt->{workspace} =~ /^\d+$/ ) { #is ID
	$params->{ids}=[$opt->{workspace}+0];
} else { #is name
	$params->{workspaces}=[$opt->{workspace}];
}

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
	    my $size = $r->[9]+0;
	    if (defined($opt->{megabytes})) {
		$size = int(($size/1048576)*1000+0.5)/1000; # convert to MB, rounded to three decimals
	    }
	    push(@{$tbl},[$r->[0],$r->[1],$r->[4],$r->[2],$r->[7],$r->[5],$r->[3],$size]);
	}
	my $sizeHeader = 'Size(bytes)';
	if (defined($opt->{megabytes})) {
		$sizeHeader = 'Size(MB)';
	}
	my $table = Text::Table->new(
		'ID', 'ObjName', 'Vers', 'Type','WS','Last_modby','Moddate',$sizeHeader
		);
	my @sorted_tbl = @$tbl;
	if (defined($opt->{column})) {
		if ($opt->{column}==8) {
			#size is numeric, so sort numerically, largest first
			@sorted_tbl = sort { $b->[$opt->{column}-1] <=> $a->[$opt->{column}-1] } @sorted_tbl;
		} elsif ( $opt->{column}==1 || $opt->{column}==3) {
			#id and version numbers are numeric, so sort numerically, largest last
			@sorted_tbl = sort { $a->[$opt->{column}-1] <=> $b->[$opt->{column}-1] } @sorted_tbl;
		} else {
			@sorted_tbl = sort { $a->[$opt->{column}-1] cmp $b->[$opt->{column}-1] } @sorted_tbl;
		}
	}
	$table->load(@sorted_tbl);
	print $table;
}

exit 0;
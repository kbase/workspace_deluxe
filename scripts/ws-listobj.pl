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
use Bio::KBase::workspace::ScriptHelpers qw(get_ws_client workspace parseObjectMeta parseWorkspaceMeta parseNiceDateTime);

my $serv = get_ws_client();
#Defining globals describing behavior
my $primaryArgs = ["Workspace Names / IDs ..."];
my $servercommand = "list_objects";
my $translation = {
    showdeleted=>"showDeleted",
    showhidden=>"showHidden",
    showversions=>"showAllVersions",
    type => "type",
    skip => "skip",
};
#Defining usage and options
my ($opt, $usage) = describe_options(
    'ws-listobj %o',
    [ 'workspace|w=s', 'Name of a workspace to search (can also be provided directly as an argument without this flag; if none is given your default workspace is assumed)' ],
    [ 'type|t=s','Specify that only objects of the given type should be listed; type names should be the fully qualified with the module name (e.g. KBaseGenomes.Genome)'],
    [ 'limit|l=i','Limit the number of objects displayed to this number (after sorting)' ],
    [ 'column|c=i','Sort by this column number (first column = 1)' ],
    [ 'megabytes|m','Report size in MB (bytes/1024^2)' ],
    [ 'timestamp|p','Display absolute timestamp of last modified date instead of relative/local time' ],
    [ 'showversions|v', 'Include all versions of the objects',{"default"=>0}],
    [ 'showhidden|a','Include hidden objects', {"default" =>0} ],
    [ 'showdeleted|s','Include objects that have been deleted', {"default" =>0} ],
    [ 'skip=i','Specify that the first N objects found (before sorting) are skipped', {} ],
    [ 'showerror|e', 'Show full stack trace of any errors in execution',{"default"=>0}],
    [ 'help|h|?', 'Print this usage information' ]
);
$usage = "\nNAME\n  ws-listobj -- list the objects in a workspace\n\nSYNOPSIS\n  ".$usage;
$usage .= "\nDESCRIPTION\n";
$usage .= "    List objects in one or more workspaces.  Note that the Workspace service will\n";
$usage .= "    limit the number of objects returned to 10,000.  If you need to iterate over\n";
$usage .= "    all items, you should use the --skip [N] option to skip over records you have\n";
$usage .= "    seen to get the set of 10,000 objects starting at object [N].  Note that the\n";
$usage .= "    limit flag of this script is applied after the object list is recieved and\n";
$usage .= "    does not directly map to the limit parameter in the list_objects API call.\n";
$usage .= "\nKNOWN BUG WARNING:\n";
$usage .= "    When filtering the object list in the backend Workspace service, objects that\n";
$usage .= "    are deleted, hidden, or are an old version are filtered *after* the 10000\n";
$usage .= "    record limit is applied. This means that fewer objects than the 10000 limit\n";
$usage .= "    may be returned, and in extreme cases many hidden, deleted, or lower version\n";
$usage .= "    objects are found no objects may be returned.\n";
$usage .= "\n";
if (defined($opt->{help})) {
	print $usage;
	exit 0;
}

#process arguments
my @workspacesToSearch;
if (defined($opt->{workspace})) {
	push(@workspacesToSearch,$opt->{workspace});
}
foreach my $arg (@ARGV) {
	push(@workspacesToSearch,$arg);
}
if (scalar(@workspacesToSearch)==0) {
	push(@workspacesToSearch,workspace());
}

if (defined($opt->{column})) {
	if ($opt->{column} <= 0 || $opt->{column} >9) {
		print STDERR "Invalid column number given.  Valid column numbers for sorting are:\n";
		print STDERR "    1 = Object Id\n";
		print STDERR "    2 = Object Name\n";
		print STDERR "    3 = Version Number\n";
		print STDERR "    4 = Object Type\n";
		print STDERR "    5 = Containing Workspace ID\n";
		print STDERR "    6 = Containing Workspace Name\n";
		print STDERR "    7 = Last Modified By\n";
		print STDERR "    8 = Last Modified Date\n";
		print STDERR "    9 = Size (in bytes or MB)\n";
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


my $params = {ids=>[],workspaces=>[]};
foreach my $w (@workspacesToSearch) {
	if ($w =~ /^\d+$/ ) { #is ID
		push(@{$params->{ids}},$w+0);
	} else { #is name
		push(@{$params->{workspaces}},$w);
	}
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
	my @localtime = localtime();
	
	for (my $i=0; $i < @{$output};$i++) {
	    my $r = $output->[$i];
	    my $size = $r->[9]+0;
	    if (defined($opt->{megabytes})) {
		$size = int(($size/1048576)*1000+0.5)/1000; # convert to MB, rounded to three decimals
	    }
	    my $moddate = $r->[3];
	    if (!defined($opt->{timestamp})) {
		$moddate = parseNiceDateTime($r->[3], $localtime[5], $localtime[4], $localtime[3], $localtime[2], $localtime[1], $localtime[0]);
	    }
	    push(@{$tbl},[$r->[0],$r->[1],$r->[4],$r->[2],$r->[6],$r->[7],$r->[5],$moddate,$size, $r->[3] ]);
	}
	my $sizeHeader = 'Size(B)';
	if (defined($opt->{megabytes})) {
		$sizeHeader = 'Size(MB)';
	}
	my $table = Text::Table->new(
		'ID', 'ObjName', 'Vers', 'Type','WSID','WS','ModBy','ModDate',$sizeHeader
		);
	my @sorted_tbl = @$tbl;
	if (defined($opt->{column})) {
		if ($opt->{column}==9) {
			#size is numeric, so sort numerically, largest first
			@sorted_tbl = sort { $b->[$opt->{column}-1] <=> $a->[$opt->{column}-1] } @sorted_tbl;
		} elsif ( $opt->{column}==1 || $opt->{column}==3 || $opt->{column}==5) {
			#ids and version numbers are numeric, so sort numerically, largest last
			@sorted_tbl = sort { $a->[$opt->{column}-1] <=> $b->[$opt->{column}-1] } @sorted_tbl;
		} elsif ( $opt->{column}==8 ) {
			#time should be sorted not based on the nice name, but on the time stamp in pos 9
			@sorted_tbl = sort { $b->[9] cmp $a->[9] } @sorted_tbl;
		} else {
			@sorted_tbl = sort { $a->[$opt->{column}-1] cmp $b->[$opt->{column}-1] } @sorted_tbl;
		}
	}
	else {
		# default sort? it should probably be based on time?
		@sorted_tbl = sort { $b->[9] cmp $a->[9] } @sorted_tbl;
	}
	# splice out the first n if limit is set
	if (defined($opt->{limit})) {
		@sorted_tbl=splice(@sorted_tbl,0,$opt->{limit});
	}
	
	$table->load(@sorted_tbl);
	print $table;
}

exit 0;
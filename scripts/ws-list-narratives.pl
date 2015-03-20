#!/usr/bin/env perl
########################################################################
# adpated for WS 0.1.0+ by Michael Sneddon, LBL
# Contact email: mwsneddon@lbl.gov
########################################################################
use strict;
use warnings;
use Getopt::Long::Descriptive;
use Text::Table;
use Bio::KBase::workspace::ScriptHelpers qw(get_ws_client parseNiceDateTime);

my $serv = get_ws_client();
#Defining globals describing behavior
my $primaryArgs = [];
my $servercommand = "list_workspace_info";
my $translation = {
};
#Defining usage and options
my ($opt, $usage) = describe_options(
    'ws-list-narratives %o',
    [ 'column|c:i','Sort by this column number (first column = 1)' ],
    [ 'deleted|d', 'Include deleted narratives in deleted workspaces',{"default"=>0}],
    [ 'unsaved|u', 'Include unsaved narratives',{"default"=>0}],
    [ 'global|g', 'Include globally readable workspaces',{"default"=>0}],
    [ 'timestamp|p','Display absolute timestamp of last modified date instead of relative/local time' ],
    [ 'json|j', 'Instead of a list, print as JSON (useful for debugging)',{"default"=>0}],
    [ 'nowarn|w', 'Hide warning messages if any',{"default"=>0}],
    [ 'showerror|e', 'Show full stack trace of any errors in execution',{"default"=>0}],
    [ 'help|h|?', 'Print this usage information' ]
);
$usage = "\nNAME\n  ws-list-narratives -- list the narratives to which you have access\n\nSYNOPSIS\n  ".$usage;
$usage .= "\n";
if (defined($opt->{help})) {
	print $usage;
    exit;
}
#Processing primary arguments
if (scalar(@ARGV) > scalar(@{$primaryArgs})) {
	print STDERR "Too many input arguments given.  Run with -h or --help for usage information\n";
	exit 1;
}
if (defined($opt->{column})) {
	if ($opt->{column} <= 0 || $opt->{column} >8) {
		print STDERR "Invalid column number given.  Valid column numbers for sorting are:\n";
		print STDERR "    1 = Workspace Id\n";
		print STDERR "    2 = Narrative Object Id\n";
		print STDERR "    3 = Narrative Name\n";
		print STDERR "    4 = Owner\n";
		print STDERR "    5 = Last Modified Date\n";
		print STDERR "    6 = Size of Workspace (number of objects)\n";
		print STDERR "    7 = Your permission (r=read,w=read/write,a=admin)\n";
		print STDERR "    8 = Global access permission (n=none,r=read)\n";
		exit 1;
	}
}
foreach my $arg (@{$primaryArgs}) {
	$opt->{$arg} = shift @ARGV;
	if (!defined($opt->{$arg})) {
		print STDERR "Not enough input arguments provided.  Run with -h or --help for usage information\n";
		exit 1;
	}
}
#Instantiating parameters
my $params = { };
foreach my $key (keys(%{$translation})) {
	if (defined($opt->{$key})) {
		$params->{$translation->{$key}} = $opt->{$key};
	}
}
if (defined($opt->{global})) {
	if ($opt->{global}) {
		$params->{"excludeGlobal"} = 0;
	} else {
		$params->{"excludeGlobal"} = 1;
	}
}
if (defined($opt->{deleted})) {
	$params->{"showDeleted"} = $opt->{deleted};
}

#Calling the server
my $output;
if ($opt->{showerror} == 0){
	eval {
	    $output = $serv->$servercommand($params);
	};
	if($@) {
		print "Cannot list workspaces! Run with -e for full stack trace.\n";
		print STDERR $@->{message}."\n";
		if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
		print STDERR "\n";
		exit 1;
	}
}else{
    $output = $serv->$servercommand($params);
}

#errors are now handled above ...

#print results
my $table = Text::Table->new(
    'WsId', 'ObjId', 'Narrative Name', 'Owner', 'Last_Modified', 'Size', 'Permission', 'GlobalAccess'
    );

my @localtime = localtime();
my @narratives; my $warnings = '';
for (my $i=0; $i < @{$output};$i++) {

	my $moddate = $output->[$i]->[3];
	if (!defined($opt->{timestamp})) {
		$moddate = parseNiceDateTime($output->[$i]->[3], $localtime[5], $localtime[4], $localtime[3], $localtime[2], $localtime[1], $localtime[0]);
	}
	push @{$output->[$i]}, $output->[$i]->[3];
	$output->[$i]->[3] = $moddate;
	
	my $metadata = $output->[$i]->[8];
	if(defined($metadata->{narrative}) && defined($metadata->{is_temporary})) {

		if ( $metadata->{narrative} =~ /^\d+$/ ) {
			if($metadata->{is_temporary} eq 'true') {
				next if(!$opt->{unsaved});
				$output->[$i]->[1] =  "[unsaved draft]";
			}
			if($metadata->{narrative_nice_name}) {
				$output->[$i]->[1] = $metadata->{narrative_nice_name};
			}

	        # add the narrative id to the beginning, then swap with the wsid
			unshift @{$output->[$i]}, $metadata->{narrative};
			$output->[$i]->[0] = $output->[$i]->[1];
			$output->[$i]->[1] = $metadata->{narrative};

			push @narratives, $output->[$i];
		} else {
			$warnings .= 'Narrative in WS '.$output->[$i]->[0]." is corrupt:\n";
			$warnings .= '   -metadata field "narrative" is set to '.$metadata->{narrative}.", but must be an object ID number.\n";
		}
	}
}


my @sorted_tbl = @narratives;
if (defined($opt->{column})) {
	if ($opt->{column}==6) {
		#size is numeric, so sort numerically, largest first
		@sorted_tbl = sort { $b->[$opt->{column}-1] <=> $a->[$opt->{column}-1] } @sorted_tbl;
	} elsif ( $opt->{column}==1 || $opt->{column}==2) {
		#id is numeric, so sort numerically, largest last
		@sorted_tbl = sort { $a->[$opt->{column}-1] <=> $b->[$opt->{column}-1] } @sorted_tbl;
	} elsif ( $opt->{column}==5 ) {
		#time should be sorted not based on the nice name, but on the time stamp in pos 9
		@sorted_tbl = sort { $b->[10] cmp $a->[10] } @sorted_tbl;
	} else {
		@sorted_tbl = sort { $a->[$opt->{column}-1] cmp $b->[$opt->{column}-1] } @sorted_tbl;
	}
} else {
	#default sort is on last update time
	@sorted_tbl = sort { $b->[10] cmp $a->[10] } @sorted_tbl;
} 

if($opt->{json}) {
	use JSON;
	my $json = JSON->new->allow_nonref;
 	print $json->pretty->encode( \@sorted_tbl );
} else {
	$table->load(@sorted_tbl);
	print $table;
	if($warnings && !$opt->{nowarn}) {
		print STDERR "\nWARNING!!\n$warnings\n";
	}
}
exit 0;

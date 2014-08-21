#!/usr/bin/env perl
########################################################################
# adpated for WS 0.1.0+ by Michael Sneddon, LBL
# Contact email: mwsneddon@lbl.gov or chenry@mcs.anl.gov
########################################################################
use strict;
use warnings;
use Getopt::Long::Descriptive;
use Text::Table;
use JSON -support_by_pp;
use Bio::KBase::workspace::ScriptHelpers qw(get_ws_client workspace getObjectRef parseObjectMeta);

my $ws = get_ws_client();

#Defining globals describing behavior
my $primaryArgs = ["Obj1 Name/Reference", "Obj2 Name/Reference"];


#Defining usage and options
my ($opt, $usage) = describe_options(
    'ws-diff <'.join("> <",@{$primaryArgs}).'> %o',
    [ 'workspace|w:s', 'Workspace name or ID', {"default" => workspace()} ],
    [ 'showerror|e', 'Show full stack trace of any errors in execution',{"default"=>0}],
    [ 'help|h|?', 'Print this usage information' ]
);
$usage = "\nNAME\n  ws-diff -- compare two workspace data objects\n\nSYNOPSIS\n  ".$usage;
$usage .= "\nDESCRIPTION\n";
$usage .= "    Compare the structure and content of two workspace objects.  You can\n";
$usage .= "    specify the objects by object name in the current or specified workspace,\n";
$usage .= "    or by object reference in the form wsName/objName/version.  For example:\n";
$usage .= "       > ws-diff Genome1 Genome2 -w MyWorkspace\n";
$usage .= "       > ws-diff MyWorkspace/Genome1/5 MyWorkspace/Genome1/3\n";
$usage .= "       > ws-diff 2245/123/1 2245/125\n";
$usage .= "\n";
if (defined($opt->{help})) {
	print $usage;
	exit;
}

my $objId1 = {};
my $objId2 = {};

if( scalar(@ARGV)==2 ) {
	$objId1 = {ref=>getObjectRef($opt->{workspace},$ARGV[0],'')};
	$objId2 = {ref=>getObjectRef($opt->{workspace},$ARGV[1],'')};
}
elsif(scalar(@ARGV)<2) {
	print STDERR "Not enough input arguments provided.  Run with -h or --help for usage information\n";
	exit 1;
} else {
	print STDERR "Too many input arguments given.  Run with -h or --help for usage information\n";
	exit 1;
}


#Instantiating parameters
my $getObjParams = [$objId1, $objId2];

# actually get the data
my $output;
if ($opt->{showerror} == 0) {
	eval { $output = $ws->get_objects($getObjParams); };
	if($@) {
		print "Cannot fetch objects!\n";
		print STDERR $@->{message}."\n";
		if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
		print STDERR "\n";
		exit 1;
	}
} else {
	$output = $ws->get_objects($getObjParams);
}

my $path = [];
printDiff($output->[0]->{"data"}, $output->[1]->{"data"}, $path, $ARGV[0], $ARGV[1],0);

exit 0;



sub printDiff {
	my ($objA, $objB, $path, $objAname, $objBname, $diffCount) = @_;
	#print "at: ".getPathStr($path)."\n";
	if (ref($objA) eq "HASH" && ref($objB) eq "HASH") {
		my @keysA = sort(keys(%$objA));
		my @keysB = sort(keys(%$objB));
		my $found = {};
		for my $k (@keysA) {
			push(@$path,$k); $found->{$k}=1;
			#print("looking at ".getPathStr($path)."\n");
			if (exists($objB->{$k})) {
				$diffCount = printDiff($objA->{$k}, $objB->{$k}, $path, $objAname, $objBname,$diffCount);
			} else {
				$diffCount++;
				print "--- d".$diffCount." --- at: ".getPathStr($path)."\n";
				print "  ".$objAname.": ".getDataStr($objA->{$k})."\n";
				print "  ".$objBname.": \n"
			}
			pop(@$path);
		}
		for my $k (@keysB) {
			next if (exists($found->{$k}));
			push(@$path,$k); $found->{$k}=1;
			$diffCount++;
			print "--- d".$diffCount." --- at: ".getPathStr($path)."\n";
			print "  ".$objAname.": \n";
			print "  ".$objBname.": ".getDataStr($objB->{$k})."\n";
			pop(@$path);
		}
	} elsif(ref($objA) eq "ARRAY" && ref($objB) eq "ARRAY") {
		my $maxValue = scalar(@$objA);
		if (scalar(@$objB)>scalar(@$objA)) { $maxValue = scalar(@$objB) }
		for(my $i=0; $i<$maxValue; $i++) {
			push(@$path,$i);
			if ($i<scalar(@$objA) && $i<scalar(@$objB)) {
				$diffCount = printDiff(@$objA[$i], @$objB[$i], $path, $objAname, $objBname, $diffCount);
			} elsif ($i>=scalar(@$objA)) {
				$diffCount++;
				print "--- d".$diffCount." --- at: ".getPathStr($path)."\n";
				print "  ".$objAname.": \n";
				print "  ".$objBname.": ".getDataStr(@$objB[$i])."\n";
			} elsif ($i>=scalar(@$objB)) {
				$diffCount++;
				print "--- d".$diffCount." --- at: ".getPathStr($path)."\n";
				print "  ".$objAname.": ".getDataStr(@$objA[$i])."\n";
				print "  ".$objBname.": \n";
			}
			pop(@$path);
		}
	} else {
		if ($objA ne $objB) {
			$diffCount++;
			print "--- d".$diffCount." --- at: ".getPathStr($path)."\n";
			print "  ".$objAname.": ".$objA."\n";
			print "  ".$objBname.": ".$objB."\n";
		}
	}
	return $diffCount;
}

sub getDataStr {
	my ($data) = @_;
	if (ref($data) eq "HASH") {
		return "{ structure or mapping with ".scalar(keys(%$data))." fields/keys }";
	} elsif (ref($data) eq "ARRAY") {
		return " [ list or tuple with ".scalar(@$data)." elements ]";
	} elsif (ref($data) eq "SCALAR") {
		return '"'.$data.'"';
	}
	return $data;
	
}

sub getPathStr {
	my ($path) = @_;
	return '/'.join('/',@$path);
}


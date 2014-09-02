#!/usr/bin/env perl
use strict;
use warnings;
use Getopt::Long;
use File::Slurp;
use Data::Dumper;
use File::Basename;
use File::Path qw(make_path);

use Bio::KBase::workspace::Client;
use Bio::KBase::workspace::ScriptHelpers qw(workspaceURL get_ws_client);

my $DESCRIPTION =
"
NAME
      ws-typespec-download -- developer utility for downloading typespecs

SYNOPSIS
      ws-typespec-download [OPTIONS]

DESCRIPTION
      
      -o [PATH], --out [PATH]	         (required) set the download output location
      -i [MODULES], --include [MODULES]  (optional) only download these modules, multiple
                                            modules can be delimited by ','
      -e [MODULES], --exclude [MODULES]  (optional) download everything except for these modules,
			                    multiple modules can be delimited by ','
      
      -u, --url          (optional) use this url to contact the Workspace, if not, uses the url
			 set by the 'ws-url' command.
      -h, --help         display this help message, ignore all arguments
      
";
      
# first parse options
my $outLocation;
my $includedModules;
my $excludedModules;
my $url;
my $help;
my $opt = GetOptions (
        "out|o=s" => \$outLocation,
        "include|i=s" => \$includedModules,
        "exclude|e=s" => \$excludedModules,
        "url|u=s" => \$url,
        "help|h" => \$help,
        );

# print help if requested, check for options
if(defined($help)) {
     print $DESCRIPTION;
     exit 0;
}
if (!defined($outLocation)) {
     print STDERR "No output location specified.  Set the location with '-o [PATH]' or '--out [PATH]'.\n";
     print STDERR "Rerun with --help for full usage info.\n";
     exit 1;
}
my $includedModulesLookup;
if (defined($includedModules)) {
	$includedModulesLookup = {};
	my @mods = split(',',$includedModules);
	for my $m (@mods) { $includedModulesLookup->{$m}=1; }
}
my $excludedModulesLookup;
if (defined($excludedModules)) {
	$excludedModulesLookup = {};
	my @mods = split(',',$excludedModules);
	for my $m (@mods) { $excludedModulesLookup->{$m}=1; }
}

# get the WS client
my $ws;
if($url) { $ws = get_ws_client($url);
} else { $ws = get_ws_client(); }

# turn on autoflush = what crazy perl syntax!!!
$| = 1;

my $moduleList;
my $listOptions={};
eval { $moduleList = $ws->list_modules($listOptions); };
if($@) {
        print STDERR "Error in fetching module list:\n";
        print STDERR $@->{message}."\n";
        if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
        print STDERR "\n";
        exit 1;
}
my @modlist;
foreach my $moduleName (@$moduleList) {
	my $useThis = 1;
	if(defined($includedModulesLookup)) {
		if (!exists($includedModulesLookup->{$moduleName})) {
			$useThis=0;
		}
	}
	if (defined($excludedModulesLookup)) {
		if (exists($excludedModulesLookup->{$moduleName})) {
			$useThis=0;
		}
	}
	# ok, process this spec
	if ($useThis) { push(@modlist,$moduleName) }
}

if (scalar(@modlist)==0) {
	print STDOUT "No modules were included in download.\n";
	exit 0;
}


print STDOUT "downloading latest versions...\n [";
for( my $i=0; $i<scalar(@modlist); $i++) {
	print STDOUT "-";
}
print STDOUT "]\n [";

# check for or make the output dir
my $specLocation = $outLocation."/spec";
make_path($specLocation);



my $downloadList = {};


my $ctime = localtime();
my $log = '=== ws-typespec-download log === '.$ctime."\n";
# first get all the latest files
for my $mod (@modlist) {
	my $modInfo;
	eval {  $modInfo = $ws->get_module_info({mod=>$mod}); };
	if($@) {
		if ($@->{message} =~ /uploaded/) {
			#error is likely that spec was not uploaded / released for this module...
			print STDOUT "*";
			next;
		} else {
			print STDERR "\nError in fetching module info for '$mod':\n";
			print STDERR $@->{message}."\n";
			if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
			print STDERR "\n";
			exit 1;
		}
	}
	
	my $niceDate = localtime(substr($modInfo->{ver},0,-3));
	$log .= "-> $mod ($modInfo->{ver} - $niceDate)\n";
	
	# check if this file has already been downloaded or is up-to-date
	my $isUpToDateLocation = isUpToDate($downloadList,$mod,$modInfo->{ver});
	if ($isUpToDateLocation) {
		$log .= "    + up to date\n";
		print STDOUT "*";
		next;
	}
	
	# get dependency info
	if (scalar(keys(%{$modInfo->{included_spec_version}}))==0) {
		$log .= "    + no dependencies\n";
	} else {
		while(my($dmod, $dver) = each(%{$modInfo->{included_spec_version}})) {
			my $niceDepDate = localtime(substr($dver,0,-3));
			$log .= "    + dependent on: $dmod ($dver - $niceDepDate)\n";
		}
	}
	
	# write the file
	my $filename = "$mod.spec";
	my $filepath = "$specLocation/$filename";
	open(my $fh, '>', $filepath) or die "Could not write to file '$filepath' $!";
	print $fh $modInfo->{spec}."\n";
	close $fh;
	$log .= "    + wrote to $filepath\n";
	$downloadList->{$mod} = {$modInfo->{ver} => {location=>$filepath, name=>$filename, deps=>$modInfo->{included_spec_version}}};
	
	print STDOUT "*";
}
print STDOUT "]\n";

print STDOUT "checking dependencies...\n";
$log .= "=== checking dependencies ===\n";
my $updatesToPaths = {};
my $needsDependencies = 0;
my $dependencyList = {};

while(my($mod, $versions) = each(%{$downloadList})) {
	while(my($ver, $info) = each(%{$versions})) {
		while(my($depMod, $depVer) = each(%{$info->{deps}})) {
			my $depFilename = isUpToDate($downloadList,$depMod,$depVer);
			if ($depFilename) {
				$log .= "->".$info->{location}." dpendency on ".$depFilename." is up to date\n";
				if(exists($updatesToPaths->{$info->{location}})) {
					$updatesToPaths->{$info->{location}}->{$depMod}=$depFilename;
				} else {
					$updatesToPaths->{$info->{location}} = {$depMod=>$depFilename};
				}
			} else {
				$needsDependencies = 1;
				if(exists($dependencyList->{$depMod})) {
					$dependencyList->{$depMod}->{$depVer}=1;
				} else {
					$dependencyList->{$depMod}= {$depVer=>1};
				}
				
				$log .= "->".$info->{location}." dependency on $depMod ($depVer) needs to be updated\n";
			}
		}
		
	}
}

if (!$needsDependencies) {
	print STDOUT "all dependent module files have been downloaded.\n";
}


while ($needsDependencies) {
	$needsDependencies = 0;
	my $ndeps = scalar(keys(%{$dependencyList}));
	print STDOUT "downloading $ndeps additional modules...\n";
	while(my($mod, $versions) = each(%{$dependencyList})) {
		while(my($ver, $info) = each(%{$versions})) {
			my $modInfo;
			eval {  $modInfo = $ws->get_module_info({mod=>$mod,ver=>$ver}); };
			if($@) {
				print STDERR "\nError in fetching module info for '$mod'('.$ver.'):\n";
				print STDERR $@->{message}."\n";
				if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
				print STDERR "\n";
				exit 1;
			}
		
			my $niceDate = localtime(substr($modInfo->{ver},0,-3));
			$log .= "->(dependency) $mod ($modInfo->{ver} - $niceDate)\n";
		
			# check if this file has already been downloaded or is up-to-date
			my $isUpToDateLocation = isUpToDate($downloadList,$mod,$modInfo->{ver});
			if ($isUpToDateLocation) {
				$log .= "    + up to date\n";
				print STDOUT "*";
				next;
			}
		
			# get dependency info
			if (scalar(keys(%{$modInfo->{included_spec_version}}))==0) {
				$log .= "    + no dependencies\n";
			} else {
				while(my($dmod, $dver) = each(%{$modInfo->{included_spec_version}})) {
					my $niceDepDate = localtime(substr($dver,0,-3));
					$log .= "    + dependent on: $dmod ($dver - $niceDepDate)\n";
				}
			}
		
			# write the file
			my $filename = "$mod.spec.".$modInfo->{ver};
			my $filepath = "$specLocation/$filename";
			open(my $fh, '>', $filepath) or die "Could not write to file '$filepath' $!";
			print $fh $modInfo->{spec}."\n";
			close $fh;
			$log .= "    + wrote to $filepath\n";
			$downloadList->{$mod} = {$modInfo->{ver} => {location=>$filepath, name=>$filename, deps=>$modInfo->{included_spec_version}}};
		}
	}
	#clear dependency list
	$dependencyList = {};
	while(my($mod, $versions) = each(%{$downloadList})) {
		while(my($ver, $info) = each(%{$versions})) {
			while(my($depMod, $depVer) = each(%{$info->{deps}})) {
				my $depFilename = isUpToDate($downloadList,$depMod,$depVer);
				$log .= "->".$info->{location}." dpendency on ".$depFilename." is up to date\n";
				if ($depFilename) {
					if(exists($updatesToPaths->{$info->{location}})) {
						$updatesToPaths->{$info->{location}}->{$depMod}=$depFilename;
					} else {
						$updatesToPaths->{$info->{location}} = {$depMod=>$depFilename};
					}
				} else {
					$needsDependencies = 1;
					if(exists($dependencyList->{$depMod})) {
						$dependencyList->{$depMod}->{$depVer}=1;
					} else {
						$dependencyList->{$depMod}= {$depVer=>1};
					}
					$log .= "->".$info->{location}." dependency on $depMod ($depVer) needs to be updated\n";
				}
			}
			
		}
	}
}

use Data::Dumper;
#print STDOUT Dumper($updatesToPaths);
#print STDOUT Dumper($dependencyList);

while( my($file, $updates) = each(%{$updatesToPaths})) {
	my $newversion = "";
	open(my $rawspec, "<", $file) || return;
	while (my $line = <$rawspec>) {
		if($line =~ /^#include /) {
			$line =~ s/^#include //; #drop the #include directive token
			$line =~ s/\s+$//; #trim trailing whitespace
			$line =~ s/\s*+;$//; #drop trailing semicolon if it was added
			# split on '<' should produce exactly two tokens, first of which we can throw away
			my $include_name = ''; my $include_version = '';
			my @post_tokens = split /</,$line;
			# now split on '>', which should also give exactly one token 
			my @pre_tokens = split />/,$post_tokens[1];
			if(scalar(@pre_tokens)==1) {
				$include_name = $pre_tokens[0];
			}
			my @depmoduleTokens = split(/\./,basename($include_name));
			#print Dumper(@depmoduleTokens)."\n";
			$newversion .= "#include <".$updates->{$depmoduleTokens[0]}.">\n";
		} else {
			$newversion .= $line;
		}
	}
	close($rawspec);
	
	open(my $fh, '>', $file) or die "Could not write to file '$file' $!";
	print $fh $newversion."\n";
	close $fh;
}

$log .= "=== done. ===\n";
#print STDOUT $log;
open(my $fh, '>>', $outLocation."/download.log") or die "Could not write to file '$specLocation' $!";
print $fh $log;
close $fh;

print "done.\n";
exit 0;


sub isUpToDate {
	my ($downloadList, $module, $version) = @_;
	if (exists($downloadList->{$module})) {
		if (exists($downloadList->{$module}->{$version})) {
			return $downloadList->{$module}->{$version}->{name};
		}
	}
	return 0;
}




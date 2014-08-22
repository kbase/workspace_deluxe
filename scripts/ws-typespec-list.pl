#!/usr/bin/env perl
use strict;
use warnings;
use Getopt::Long;
use Term::ReadKey;
use File::Slurp;
use Data::Dumper;
use File::Basename;
use Bio::KBase::workspace::Client;
use Bio::KBase::workspace::ScriptHelpers qw(workspaceURL get_ws_client);

my $DESCRIPTION =
"
NAME
      ws-typespec-list -- list available modules and types

SYNOPSIS
      ws-typespec-list [OPTIONS] [MODULE/TYPE NAME]

DESCRIPTION
      
      List modules or types registered with the workspace.  Use 'ws-url' to set the workspace url.
      
      If no module/type name is given, this will list the set of available modules.
      
      If a module name is given,this will list the set of released types of that
      module with the latested version of each type.
      
      If a type name is given, the full type name and version is provided with a description
      of the type if a description exists. The type name must be fully qualified as:
        ModuleName.TypeName
      
      
      -s, --spec         if set and a module/type name is given, the registered typespec
                         for the module/type is returned
                         
      -j, --jsonschema   if set and a type name is given, the json schema representation
                         of the type is given
      
      -v, --versions     if set, list all versions of the given module or type instead
      
      -h, --help         display this help message, ignore all arguments
      
";
      
# first parse options; only one here is help
my $filetype;
my $downloader;
my $outdir;
my $force;
my $longname;
my $module;

my $user;
my $password;

my $listtypes;

my $returnSpec;
my $returnJsonSchema;
my $returnVersions;
my $owner;
my $all;

my $help;

my $opt = GetOptions (
        "spec|s" => \$returnSpec,
        "jsonschema|j" => \$returnJsonSchema,
        "versions|v" => \$returnVersions,
        "all" => \$all,
        "owner|u=s" => \$owner,
        "user|u=s" => \$user,
        "password|p=s" => \$password,
        "help|h" => \$help,
        );

# print help if requested
if(defined($help)) {
     print $DESCRIPTION;
     exit 0;
}

my $ws;
if (defined($user)) {
     if (!defined($password)) { $password = get_pass(); }
     $ws = Bio::KBase::workspace::Client->new(workspaceURL(),user_id=>$user,password=>$password);
} else {
     $ws = get_ws_client();
}


my $n_args = $#ARGV+1;
if($n_args==0) {
     my $listOptions = {};
     if (defined($owner)) { $listOptions->{owner}=$owner; }
     
     my $moduleList;
     eval { $moduleList = $ws->list_modules($listOptions); };
     if($@) {
          print STDERR "Error in listing modules:\n";
          print STDERR $@->{message}."\n";
          if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
          print STDERR "\n";
          exit 1;
     }
     foreach my $moduleName (@$moduleList) {
          print STDOUT $moduleName."\n";
     }
} elsif($n_args==1) {
     my $name = $ARGV[0];
     my @tokens=split(/\./,$name,2);
     if (scalar(@tokens)==1) {
          # it is a module name ...
          my $options = {"mod"=>$name};
          if ($returnVersions) {
               my $moduleVersions;
               eval { $moduleVersions = $ws->list_module_versions($options); };
               if($@) {
                    print STDERR "Error in listing types for module '$name':\n";
                    print STDERR $@->{message}."\n";
                    if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
                    print STDERR "\n";
                    exit 1;
               }
               my $versions = $moduleVersions->{"vers"};
               foreach my $ver (@$versions) {
                    print STDOUT $ver."\n";
               }
          } else {
               my $moduleInfo;
               eval { $moduleInfo = $ws->get_module_info($options); };
               if($@) {
                    print STDERR "Error in listing types for module '$name':\n";
                    print STDERR $@->{message}."\n";
                    if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
                    print STDERR "\n";
                    exit 1;
               }
               if (defined($all)) {
                    print Dumper($moduleInfo)."\n";
               } elsif ($returnSpec) {
                    print STDOUT $moduleInfo->{"spec"}."\n";
               } else {
                    my $typeMap = $moduleInfo->{"types"};
                    my @typeList = sort(keys(%$typeMap));
                    foreach my $typeName (@typeList) {
                         print STDOUT $typeName."\n";
                    }
               }
          }
          
          
     } elsif (scalar(@tokens)==2) {
          # it is a type name ...
          if ($returnJsonSchema) {
               my $jsonschema;
               eval { $jsonschema = $ws->get_jsonschema($name); };
               if($@) {
                    print STDERR "Error in getting json schema for type '$name':\n";
                    print STDERR $@->{message}."\n";
                    if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
                    print STDERR "\n";
                    exit 1;
               }
               print STDOUT $jsonschema."\n";
          } else {
               my $typeInfo;
               eval { $typeInfo = $ws->get_type_info($name); };
               if($@) {
                    print STDERR "Error in getting info for type '$name':\n";
                    print STDERR $@->{message}."\n";
                    if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
                    print STDERR "\n";
                    exit 1;
               }
               if (defined($all)) {
                    print Dumper($typeInfo)."\n";
               } elsif ($returnSpec) {
                    print STDOUT $typeInfo->{"spec_def"}."\n";
               } elsif($returnVersions) {
                    my $versions = $typeInfo->{"type_vers"};
                    foreach my $ver (@$versions) {
                         print STDOUT $ver."\n";
                    }
               } else {
                    my $fullTypeName = $typeInfo->{"type_def"};
                    my $description = $typeInfo->{"description"};
                    print STDOUT "LATEST VERSION: ".$fullTypeName."\nDESCRIPTION:\n".$description."\n\n";
               }
          }
     }
     
} else {
     print STDERR "Too many input arguments.  Rerun with --help for usage.\n";
     exit 1;
}
exit 0;

  



# copied from kbase-login...
sub get_pass {
    my $key  = 0;
    my $pass = ""; 
    print "Password: ";
    ReadMode(4);
    while ( ord($key = ReadKey(0)) != 10 ) {
        # While Enter has not been pressed
        if (ord($key) == 127 || ord($key) == 8) {
            chop $pass;
            print "\b \b";
        } elsif (ord($key) < 32) {
            # Do nothing with control chars
        } else {
            $pass .= $key;
            print "*";
        }
    }
    ReadMode(0);
    print "\n";
    return $pass;
}

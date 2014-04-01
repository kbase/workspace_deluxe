#!/usr/bin/env perl
use strict;
use warnings;
use Getopt::Long;
use Config::Simple;

my $DESCRIPTION =
"
usage:
      get_deploy_cfg [Options] [VariableName]
      
      Retrieve the value of a variable in a KBase 'deploy.cfg' file (essentially a simple
      INI file) by variable name.  The value is printed to standard out.
      
      -d, --deploy-cfg [FILE]
            (optional) the location of the deploy.cfg file to use; if not provided, the
            script assumes the file is named 'deploy.cfg' in your current working directory
                        
      -h, --help
            diplay this help message, ignore all arguments
";

my $help = '';
my $deploy_cfg_file = 'deploy.cfg';
my $opt = GetOptions (
        "help|h" => \$help,
        "deploy-cfg|d=s" => \$deploy_cfg_file
        );
if($help) {
    print $DESCRIPTION;
    exit 0;
}

#process args
my $n_args = $#ARGV+1;
if ($n_args != 1) {
    print STDERR "ERROR: Incorrect number of arguments- must specify a variable name.\n";
    print STDERR $DESCRIPTION;
    exit 1;
}
my $varname = $ARGV[0];

#read the cfg file
if (!-e $deploy_cfg_file) {
    print STDERR "ERROR: Cannot find deploy.cfg (looking for '$deploy_cfg_file').\n";
    print STDERR "Rerun with --help option for usage.\n";
    exit 1;
}
my $cfg_lookup={};
Config::Simple->import_from($deploy_cfg_file, $cfg_lookup);

if (defined $cfg_lookup->{$varname}) {
    print STDOUT $cfg_lookup->{$varname};
} else {
    print STDERR "ERROR: Variable '$varname' not defined in config file '$deploy_cfg_file').\n";
    print STDERR "    Available variables are: \n";
    if (scalar(keys(%$cfg_lookup))==0) {
        print STDERR "        --no variables were found--\n";
    }
    else {
        foreach my $key (keys %$cfg_lookup) {
            print STDERR "        $key\n";
        }
    }
}

exit 0;





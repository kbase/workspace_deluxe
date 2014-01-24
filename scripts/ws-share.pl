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
use Data::Dumper;
use Bio::KBase::workspace::ScriptHelpers qw(get_ws_client workspace);

my $serv = get_ws_client();
#Defining globals describing behavior
my $primaryArgs = [];
my $translation = {
	workspace => "workspace",
	globalread => "globalread",
	users => "users",
	perm => "perm"
};
#Defining usage and options
my ($opt, $usage) = describe_options(
    'ws-share %o',
    [ 'workspace|w=s', 'Name of workspace',{"default" => workspace()}],
    [ 'globalread|g=s', 'Set global read permissions (r=read,n=none)',{"default"=>''}],
    [ 'users|u=s', "Set permissions for these users (';' delimited)",{"default"=>''}],
    [ 'perm|p=s', "The permission to set for the given users, default if not set is read only (a=admin,w=write/read,r=read,n=none)",{"default"=>'r'}],
    [ 'showerror|e', 'Show full stack trace of any errors in execution',{"default"=>0}],
    [ 'help|h|?', 'Print this usage information' ]
);
$usage = "\nNAME\n  ws-share -- view/manage permissions to a workspace\n\nSYNOPSIS\n  ".$usage;
$usage .= "\nDESCRIPTION\n";
$usage .= "    Run without options, this command displays permissions for a workspace.\n";
$usage .= "    With options, you can share a workspace or change permissions if you have\n";
$usage .= "    administrator rights to that workspace.\n";
$usage .= "\n";
if (defined($opt->{help})) {
	print $usage;
    exit;
}
#Processing primary arguments
if (scalar(@ARGV) > scalar(@{$primaryArgs})) {
	print STDERR "Too many input arguments given.  Run with -h or --help for usage information.\n";
	exit 1;
}
foreach my $arg (@{$primaryArgs}) {
	$opt->{$arg} = shift @ARGV;
	if (!defined($opt->{$arg})) {
		print STDERR "Not enough input arguments provided.  Run with -h or --help for usage information.\n";
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

#Set the global read permission if globalread is set
if (defined($opt->{globalread})) {
	if ($opt->{globalread} ne '') {
		if ($opt->{showerror} == 0){
			eval {
				if ($params->{workspace} =~ /^\d+$/ ) { #is ID
					$serv->set_global_permission(
						{id => $params->{workspace},
						new_permission => $opt->{globalread}});
				} else { #is name
					$serv->set_global_permission(
						{workspace => $params->{workspace},
						new_permission => $opt->{globalread}});
				}
			};
			if($@) {
				print "Cannot set global permission for workspace $params->{workspace}! Run with -e for full stack trace.\n";
				print STDERR $@->{message}."\n";
				if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
				print STDERR "\n";
				exit 1;
			}
		} else {
			if ($params->{workspace} =~ /^\d+$/ ) { #is ID
				$serv->set_global_permission(
					{id => $params->{workspace},
					new_permission => $opt->{globalread}});
			} else { #is name
				$serv->set_global_permission(
					{workspace => $params->{workspace},
					new_permission => $opt->{globalread}});
			}
		}
		# if we get here, setting global read worked
		print "Successfully set global read permission to: $opt->{globalread}\n";
	}
}
if (defined($opt->{users})) {
	my @userList = split /;/, $opt->{users};
	if (scalar(@userList)>0) {
		if ($opt->{showerror} == 0){
			eval {
				if ($params->{workspace} =~ /^\d+$/ ) { #is ID
					$serv->set_permissions(
						{id => $params->{workspace},
						 new_permission => $opt->{perm},
						 users => \@userList });
				} else { #is name
					$serv->set_permissions(
						{workspace => $params->{workspace},
						 new_permission => $opt->{perm},
						 users => \@userList });
				}
			};
			if($@) {
				print "Cannot set user permissions for workspace $params->{workspace}! Run with -e for full stack trace.\n";
				print STDERR $@->{message}."\n";
				if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
				print STDERR "\n";
				exit 1;
			}
		} else {
			if ($params->{workspace} =~ /^\d+$/ ) { #is ID
				$serv->set_permissions(
					{id => $params->{workspace},
					 new_permission => $opt->{perm},
					 users => \@userList });
			} else { #is name
				$serv->set_permissions(
					{workspace => $params->{workspace},
					 new_permission => $opt->{perm},
					 users => \@userList });
			}
		}
		print "Successfully set user permissions to: $opt->{perm}\n";
	}
}


#View permissions for the workspace
my $wsinfo;
my $userPerm;
if ($opt->{showerror} == 0){
	eval {
		if ($params->{workspace} =~ /^\d+$/ ) { #is ID
			$wsinfo = $serv->get_workspace_info({id => $params->{workspace}});
			$userPerm = $serv->get_permissions({id => $params->{workspace}});
		} else { #is name
			$wsinfo = $serv->get_workspace_info({workspace => $params->{workspace}});
			$userPerm = $serv->get_permissions({workspace => $params->{workspace}});
		}
	};
	if($@) {
		print "Cannot view permissions for workspace $params->{workspace}! Run with -e for full stack trace.\n";
		print STDERR $@->{message}."\n";
		if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
		print STDERR "\n";
		exit 1;
	}
} else {
	if ($params->{workspace} =~ /^\d+$/ ) { #is ID
		$wsinfo = $serv->get_workspace_info({id => $params->{workspace}});
		$userPerm = $serv->get_permissions({id => $params->{workspace}});
	} else { #is name
		$wsinfo = $serv->get_workspace_info({workspace => $params->{workspace}});
		$userPerm = $serv->get_permissions({workspace => $params->{workspace}});
	}
}

#print results
my $table = Text::Table->new(
    'Id', 'Name', 'Owner', 'Last_Modified', 'Size', 'Permission', 'GlobalAccess'
    );
my @infoList; push @infoList, $wsinfo;
$table->load(@infoList);
print "\n";
print $table;
print "\n";

my $userPermTable = Text::Table->new(
    'UserName', 'Permission'
    );
my @userPermList;
foreach my $u (keys %$userPerm) {
	push(@userPermList, [$u, $userPerm->{$u}]);
}
$userPermTable->load(@userPermList);
print $userPermTable;
print "\n";

exit 0;

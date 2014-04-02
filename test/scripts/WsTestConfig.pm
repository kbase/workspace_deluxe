#  Simple module that returns the WS URL to use during script testing. This
#  is set as the localhost URL as setup by the makefile during a 'make' in
#  the ScriptConfig package.  To test against the expected production or
#  script default URL, use getDefaultURL.
#
#  This module also lets you fetch the test.cfg file parsed into a hash based
#  on 
#

package WsTestConfig;

use strict;
use warnings;
require Exporter;
our @ISA = qw(Exporter);
our @EXPORT_OK = qw(getLocalTestURL getDefaultURL getWsTestCfg);

use Bio::KBase::workspace::ScriptConfig;

use File::Basename;
use Config::Simple;
use Cwd qw(abs_path);


# CHANGE THE HOST AND PORT CONFIGURATION HERE

sub getLocalTestURL   { return $Bio::KBase::workspace::ScriptConfig::localhostURL; }
#sub getLocalTestURL   { return $Bio::KBase::workspace::ScriptConfig::devURL; }
sub getDefaultURL   { return $Bio::KBase::workspace::ScriptConfig::defaultURL; }

sub getWsTestCfg {
    my $testConfigFilePath = dirname(abs_path($0)).'/files/test.cfg.copy';
    my $cfg = new Config::Simple(filename=>$testConfigFilePath) or die Config::Simple->error();
    return $cfg;
}


1;

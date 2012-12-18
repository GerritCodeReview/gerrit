#!/usr/bin/env perl

# Fake LDAP server for Gerrit
# Author: Olivier Croquette <ocroquette@free.fr>
# Last change: 2012-11-12
#
# Abstract:
# ====================================================================
#
# Gerrit currently supports several authentication schemes, but
# unfortunately not the most basic one, e.g. local accounts with
# local passwords.
#
# As a workaround, this script implements a minimal LDAP server
# that can be used to authenticate against Gerrit. The information
# required by Gerrit relative to users (user ID, password, display
# name, email) is stored in a text file similar to /etc/passwd
#
#
# Usage (see below for the setup)
# ====================================================================
#
# To create a new file to store the user information:
#   fake-ldap edituser --datafile /path/datafile --username maxpower \
#     --displayname "Max Power" --email max.power@provider.com
#
# To modify an existing user (for instance the email):
#   fake-ldap edituser --datafile /path/datafile --username ocroquette \
#     --email max.power@provider2.com
#
# To set a new password for an existing user:
#   fake-ldap edituser --datafile /path/datafile --username ocroquette \
#     --password ""
#
# To start the server:
#   fake-ldap start --datafile /path/datafile
#
# The server reads the user data file on each new connection. It's not
# scalable but it should not be a problem for the intended usage
# (small teams, testing,...)
#
#
# Setup
# ===================================================================
#
# Install the dependencies
#
#   Install the Perl module dependencies. On Debian and MacPorts,
#   all modules are available as packages, except Net::LDAP::Server.
#
#   Debian: apt-get install libterm-readkey-perl
#
#   Since Net::LDAP::Server consists only of one file, you can put it
#   along the script in Net/LDAP/Server.pm
#
# Create the data file with the first user (see above)
#
# Start as the script a server ("start" command, see above)
#
# Configure Gerrit with the following options:
#
#   gerrit.canonicalWebUrl = ... (workaround for a known Gerrit bug)
#   auth.type = LDAP_BIND
#   ldap.server = ldap://localhost:10389
#   ldap.accountBase = ou=People,dc=nodomain
#   ldap.groupBase = ou=Group,dc=nodomain
#
# Start Gerrit
#
# Log on in the Web interface
#
# If you want the fake LDAP server to start at boot time, add it to
# /etc/inittab, with a line like:
#
# ld1:6:respawn:su someuser /path/fake-ldap start --datafile /path/datafile
#
# ===================================================================

use strict;

# Global var containing the options passed on the command line:
my %cmdLineOptions;

# Global var containing the user data read from the data file:
my %userData;

my $defaultport = 10389;

package MyServer;

use Data::Dumper;
use Net::LDAP::Server;
use Net::LDAP::Constant qw(LDAP_SUCCESS LDAP_INVALID_CREDENTIALS LDAP_OPERATIONS_ERROR);
use IO::Socket;
use IO::Select;
use Term::ReadKey;

use Getopt::Long;

use base 'Net::LDAP::Server';

sub bind {
  my $self = shift;
  my ($reqData, $fullRequest) = @_;

  print "bind called\n" if $cmdLineOptions{verbose} >= 1;
  print Dumper(\@_) if $cmdLineOptions{verbose} >= 2;
  my $sha1 = undef;
  my $uid = undef;
  eval{
    $uid = $reqData->{name};
    $sha1 = main::encryptpwd($uid, $reqData->{authentication}->{simple})
  };
  if ($@) {
    warn $@;
    return({
        'matchedDN' => '',
        'errorMessage' => $@,
        'resultCode' => LDAP_OPERATIONS_ERROR
    });
  }

  print $sha1 . "\n" if $cmdLineOptions{verbose} >= 2;
  print Dumper($userData{$uid}) . "\n" if $cmdLineOptions{verbose} >= 2;

  if ( defined($sha1) && $sha1 && $userData{$uid} && ( $sha1 eq $userData{$uid}->{password} ) ) {
    print "authentication of $uid succeeded\n" if $cmdLineOptions{verbose} >= 1;
    return({
      'matchedDN' => "dn=$uid,ou=People,dc=nodomain",
      'errorMessage' => '',
      'resultCode' => LDAP_SUCCESS
    });
  }
  else {
    print "authentication of $uid failed\n" if $cmdLineOptions{verbose} >= 1;
    return({
      'matchedDN' => '',
      'errorMessage' => '',
      'resultCode' => LDAP_INVALID_CREDENTIALS
    });
  }
}

sub search {
    my $self = shift;
    my ($reqData, $fullRequest) = @_;
    print "search called\n" if $cmdLineOptions{verbose} >= 1;
    print Dumper($reqData)  if $cmdLineOptions{verbose} >= 2;
    my @entries;
    if ( $reqData->{baseObject} eq 'ou=People,dc=nodomain' ) {
        my $uid = $reqData->{filter}->{equalityMatch}->{assertionValue};
        push @entries, Net::LDAP::Entry->new ( "dn=$uid,ou=People,dc=nodomain",
       , 'objectName'=>"dn=uid,ou=People,dc=nodomain", 'uid'=>$uid, 'mail'=>$userData{$uid}->{email}, 'displayName'=>$userData{$uid}->{displayName});
   }
   elsif ( $reqData->{baseObject} eq 'ou=Group,dc=nodomain'  ) {
        push @entries, Net::LDAP::Entry->new ( 'dn=Users,ou=Group,dc=nodomain',
       , 'objectName'=>'dn=Users,ou=Group,dc=nodomain');
   }

    return {
        'matchedDN' => '',
        'errorMessage' => '',
        'resultCode' => LDAP_SUCCESS
    }, @entries;
}


package main;

use Digest::SHA1  qw(sha1 sha1_hex sha1_base64);

sub exitWithError {
  my $msg = shift;
  print STDERR $msg . "\n";
  exit(1);
}

sub encryptpwd {
  my ($uid, $passwd) = @_;
  # Use the user id to compute the hash, to avoid rainbox table attacks
  return sha1_hex($uid.$passwd);
}

my $result = Getopt::Long::GetOptions (
  "port=i"        => \$cmdLineOptions{port},
  "datafile=s"    => \$cmdLineOptions{datafile},
  "email=s"       => \$cmdLineOptions{email},
  "displayname=s" => \$cmdLineOptions{displayName},
  "username=s"    => \$cmdLineOptions{userName},
  "password=s"    => \$cmdLineOptions{password},
  "verbose=i"     => \$cmdLineOptions{verbose},
);
exitWithError("Failed to parse command line arguments") if ! $result;
exitWithError("Please provide a valid path for the datafile") if ! $cmdLineOptions{datafile};

my @commands = qw(start edituser);
if ( @ARGV != 1 || ! grep {$_ eq $ARGV[0]} @commands ) {
  exitWithError("Please provide a valid command among: " . join(",", @commands));
}

my $command = $ARGV[0];
if ( $command eq "start") {
  startServer();
}
elsif ( $command eq "edituser") {
  editUser();
}


sub startServer() {

  my $port = $cmdLineOptions{port} || $defaultport;

  print "starting on port $port\n" if $cmdLineOptions{verbose} >= 1;

  my $sock = IO::Socket::INET->new(
    Listen => 5,
    Proto => 'tcp',
    Reuse => 1,
    LocalAddr => "localhost", # Comment this line if Gerrit doesn't run on this host
    LocalPort => $port
  );

  my $sel = IO::Select->new($sock);
  my %Handlers;
  while (my @ready = $sel->can_read) {
    foreach my $fh (@ready) {
      if ($fh == $sock) {
        # Make sure the data is up to date on new every connection
        readUserData();

        # let's create a new socket
        my $psock = $sock->accept;
        $sel->add($psock);
        $Handlers{*$psock} = MyServer->new($psock);
      } else {
        my $result = $Handlers{*$fh}->handle;
        if ($result) {
          # we have finished with the socket
          $sel->remove($fh);
          $fh->close;
          delete $Handlers{*$fh};
        }
      }
    }
  }
}

sub readUserData {
  %userData = ();
  open (MYFILE, "<$cmdLineOptions{datafile}") || exitWithError("Could not open \"$cmdLineOptions{datafile}\" for reading");
  while (<MYFILE>) {
    chomp;
    my @fields = split(/:/, $_);
    $userData{$fields[0]} = { password=>$fields[1], displayName=>$fields[2], email=>$fields[3] };
  }
  close (MYFILE);
}

sub writeUserData {
  open (MYFILE, ">$cmdLineOptions{datafile}") || exitWithError("Could not open \"$cmdLineOptions{datafile}\" for writing");
  foreach my $userid (sort(keys(%userData))) {
    my $userInfo = $userData{$userid};
    print MYFILE join(":",
      $userid,
      $userInfo->{password},
      $userInfo->{displayName},
      $userInfo->{email}
      ). "\n";
  }
  close (MYFILE);
}

sub readPassword {
  Term::ReadKey::ReadMode('noecho');
  my $password = Term::ReadKey::ReadLine(0);
  Term::ReadKey::ReadMode('normal');
  print "\n";
  return $password;
}

sub readAndConfirmPassword {
  print "Please enter the password: ";
  my $pwd = readPassword();
  print "Please re-enter the password: ";
  my $pwdCheck = readPassword();
  exitWithError("The passwords are different") if $pwd ne $pwdCheck;
  return $pwd;
}

sub editUser {
  exitWithError("Please provide a valid user name") if ! $cmdLineOptions{userName};
  my $userName = $cmdLineOptions{userName};

  readUserData() if -r $cmdLineOptions{datafile};

  my $encryptedPassword = undef;
  if ( ! defined($userData{$userName}) ) {
    # New user

    exitWithError("Please provide a valid display name") if ! $cmdLineOptions{displayName};
    exitWithError("Please provide a valid email") if ! $cmdLineOptions{email};

    $userData{$userName} = { };

    if ( ! defined($cmdLineOptions{password}) ) {
      # No password provided on the command line. Force reading from terminal.
      $cmdLineOptions{password} = "";
    }
  }

  if ( defined($cmdLineOptions{password}) && ! $cmdLineOptions{password} ) {
    $cmdLineOptions{password} = readAndConfirmPassword();
    exitWithError("Please provide a non empty password") if ! $cmdLineOptions{password};
  }


  if ( $cmdLineOptions{password} ) {
    $encryptedPassword = encryptpwd($userName, $cmdLineOptions{password});
  }


  $userData{$userName}->{password}    = $encryptedPassword if $encryptedPassword;
  $userData{$userName}->{displayName} = $cmdLineOptions{displayName} if $cmdLineOptions{displayName};
  $userData{$userName}->{email}       = $cmdLineOptions{email} if $cmdLineOptions{email};
  # print Data::Dumper::Dumper(\%userData);

  print "New user data for $cmdLineOptions{userName}:\n";
  foreach ( sort(keys(%{$userData{$userName}}))) {
    printf "  %-15s : %s\n", $_, $userData{$userName}->{$_}
  }
  writeUserData();
}
---
title: " Permission denied (publickey)"
sidebar: errors_sidebar
permalink: error-permission-denied.html
---
With this error message an SSH command to Gerrit is rejected if the SSH
authentication is not successful.

The [SSH](http://en.wikipedia.org/wiki/Secure_Shell) protocol can use
[Public-key
Cryptography](http://en.wikipedia.org/wiki/Public-key_cryptography) for
authentication. In general configurations, Gerrit will authenticate you
by the public keys known to you. Optionally, it can be configured by the
administrator to allow for
[kerberos](config-gerrit.html#sshd.kerberosKeytab) authentication
instead.

In any case, verify that you are using the correct username for the SSH
command and that it is typed correctly (case sensitive). You can look up
your username in the Gerrit Web UI under *Settings* → *Profile*.

If you are facing this problem and using an SSH keypair, do the
following:

1.  Verify that you have uploaded your public SSH key for your Gerrit
    account. To do this go in the Gerrit Web UI to *Settings* → *SSH
    Public Keys* and check that your public SSH key is there. If your
    public SSH key is not there you have to upload it.

2.  Verify that you are using the correct private SSH key. To find out
    which private SSH key is used test the SSH authentication as
    described below. From the trace you should see which private SSH key
    is used.

Debugging kerberos issues can be quite hard given the complexity of the
protocol. In case you are using kerberos authentication, do the
following:

1.  Verify that you have acquired a valid initial ticket. On a Linux
    machine, you can acquire one using the `kinit` command. List all
    your tickets using the `klist` command. It should list all
    principals for which you have acquired a ticket and include a
    principal name corresponding to your Gerrit server, for example
    `HOST/gerrit.mydomain.tld@MYDOMAIN.TLD`. Note that tickets can
    expire and require you to re-run `kinit` periodically.

2.  Verify that your SSH client is using kerberos authentication. For
    OpenSSH clients this can be controlled using the
    `GSSAPIAuthentication` setting. For more information see [SSH
    kerberos configuration](user-upload.html#configure_ssh_kerberos).

## Test SSH authentication

To test the SSH authentication you can run the following SSH command.
This command will print out a detailed trace which is helpful to analyze
problems with the SSH authentication:

``` 
  $ ssh -vv -p 29418 john.doe@git.example.com
```

If the SSH authentication is successful you should find the following
lines in the output:

``` 
  ...

  debug1: Authentication succeeded (publickey).

  ...

  ****    Welcome to Gerrit Code Review    ****

  Hi John Doe, you have successfully connected over SSH.

  Unfortunately, interactive shells are disabled.
  To clone a hosted Git repository, use:

  git clone ssh://john.doe@git.example.com:29418/REPOSITORY_NAME.git

  ...
```

## GERRIT

Part of [Gerrit Error Messages](error-messages.html)

## SEARCHBOX


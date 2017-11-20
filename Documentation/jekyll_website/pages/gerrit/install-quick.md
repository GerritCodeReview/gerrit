---
title: " Gerrit Code Review - Quick get started guide"
sidebar: gerritdoc_sidebar
permalink: install-quick.html
---
This guide was made with the impatient in mind, ready to try out Gerrit
on their own server but not prepared to make the full installation
procedure yet.

Explanation is sparse and you should not use a server installed this way
in a live setup, this is made with proof of concept activities in mind.

It is presumed you install it on a Unix based server such as any of the
Linux flavors or BSD.

It’s also presumed that you have access to an OpenID enabled email
address. Examples of OpenID enable email providers are Gmail, Yahoo\!
Mail and Hotmail. It’s also possible to register a custom email address
with OpenID, but that is outside the scope of this quick installation
guide. For testing purposes one of the above providers should be fine.
Please note that network access to the OpenID provider you choose is
necessary for both you and your Gerrit instance.

## Requirements

Most distributions come with Java today. Do you already have Java
installed?

``` 
  $ java -version
  openjdk version "1.8.0_72"
  OpenJDK Runtime Environment (build 1.8.0_72-b15)
  OpenJDK 64-Bit Server VM (build 25.72-b15, mixed mode)
```

If Java isn’t installed, get it:

  - JRE, minimum version 1.8
    [Download](http://www.oracle.com/technetwork/java/javase/downloads/index.html)

## Create a user to host the Gerrit service

We will run the service as a non-privileged user on your system. First
create the user and then become the user:

``` 
  $ sudo adduser gerrit
  $ sudo su gerrit
```

If you don’t have root privileges you could skip this step and run
Gerrit as your own user as well.

## Download Gerrit

It’s time to download the archive that contains the Gerrit web and ssh
service.

You can choose from different versions to download from here:

  - [A list of releases
    available](https://www.gerritcodereview.com/download/index.html)

This tutorial is based on version 2.2.2, and you can download that from
this link

  - [Link to the 2.2.2 war
    archive](https://www.gerritcodereview.com/download/gerrit-2.2.2.war)

## Initialize the Site

It’s time to run the initialization, and with the batch switch enabled,
we don’t have to answer any questions at all:

``` 
  gerrit@host:~$ java -jar gerrit.war init --batch -d ~/gerrit_testsite
  Generating SSH host key ... rsa(simple)... done
  Initialized /home/gerrit/gerrit_testsite
  Executing /home/gerrit/gerrit_testsite/bin/gerrit.sh start
  Starting Gerrit Code Review: OK
  gerrit@host:~$
```

When the init is complete, you can review your settings in the file
`'$site_path/etc/gerrit.config'`.

Note that initialization also starts the server. If any settings changes
are made, the server must be restarted before they will take effect.

``` 
  gerrit@host:~$ ~/gerrit_testsite/bin/gerrit.sh restart
  Stopping Gerrit Code Review: OK
  Starting Gerrit Code Review: OK
  gerrit@host:~$
```

The server can be also stopped and started by passing the `stop` and
`start` commands to gerrit.sh.

``` 
  gerrit@host:~$ ~/gerrit_testsite/bin/gerrit.sh stop
  Stopping Gerrit Code Review: OK
  gerrit@host:~$
  gerrit@host:~$ ~/gerrit_testsite/bin/gerrit.sh start
  Starting Gerrit Code Review: OK
  gerrit@host:~$
```

## Initial Login

It’s time to exit the gerrit account as you now have Gerrit running on
your host and setup your first workspace.

Start a shell with the credentials of the account you will perform
development under.

Check whether there are any ssh keys already. You’re looking for two
files, id\_rsa and id\_rsa.pub.

``` 
  user@host:~$ ls .ssh
  authorized_keys  config  id_rsa  id_rsa.pub  known_hosts
  user@host:~$
```

If you have the files, you may skip the key generating step.

If you don’t see the files in your listing, your will have to generate
rsa keys for your ssh sessions:

### SSH key generation

**Please don’t generate new keys if you already have a valid keypair\!**
**They will be overwritten\!**

``` 
  user@host:~$ ssh-keygen -t rsa
  Generating public/private rsa key pair.
  Enter file in which to save the key (/home/user/.ssh/id_rsa):
  Created directory '/home/user/.ssh'.
  Enter passphrase (empty for no passphrase):
  Enter same passphrase again:
  Your identification has been saved in /home/user/.ssh/id_rsa.
  Your public key has been saved in /home/user/.ssh/id_rsa.pub.
  The key fingerprint is:
  00:11:22:00:11:22:00:11:44:00:11:22:00:11:22:99 user@host
  The key's randomart image is:
  +--[ RSA 2048]----+
  |     ..+.*=+oo.*E|
  |      u.OoB.. . +|
  |       ..*.      |
  |       o         |
  |      . S ..     |
  |                 |
  |                 |
  |          ..     |
  |                 |
  +-----------------+
  user@host:~$
```

### Registering your key in Gerrit

Open a browser and enter the canonical url of your Gerrit server. You
can find the url in the settings
file.

``` 
  gerrit@host:~$ git config -f ~/gerrit_testsite/etc/gerrit.config gerrit.canonicalWebUrl
  http://localhost:8080/
  gerrit@host:~$
```

Register a new account in Gerrit through the web interface with the
email address of your choice.

The default authentication type is OpenID. If your Gerrit server is
behind a proxy, and you are using an external OpenID provider, you will
need to add the proxy settings in the configuration
file.

``` 
  gerrit@host:~$ git config -f ~/gerrit_testsite/etc/gerrit.config --add http.proxy http://proxy:8080
  gerrit@host:~$ git config -f ~/gerrit_testsite/etc/gerrit.config --add http.proxyUsername username
  gerrit@host:~$ git config -f ~/gerrit_testsite/etc/gerrit.config --add http.proxyPassword password
```

Refer to the Gerrit configuration guide for more detailed information
about [authentication](config-gerrit.html#auth) and
[proxy](config-gerrit.html#http.proxy) settings.

The first user to sign-in and register an account will be automatically
placed into the fully privileged Administrators group, permitting server
management over the web and over SSH. Subsequent users will be
automatically registered as unprivileged users.

Once signed in as your user, you find a little wizard to get you
started. The wizard helps you fill out:

  - Real name (visible name in Gerrit)

  - Register your email (it must be confirmed later)

  - Select a username with which to communicate with Gerrit over
    ssh+git. Note that once saved, the username cannot be changed.

  - The server will ask you for an RSA public key. That’s the key we
    generated above, and it’s time to make sure that Gerrit knows about
    our new key and can identify us by it.

<!-- end list -->

``` 
  user@host:~$ cat .ssh/id_rsa.pub
  ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAQEA1bidOd8LAp7Vp95M1b9z+LGO96OEWzdAgBPfZPq05jUh
  jw0mIdUuvg5lhwswnNsvmnFhGbsUoXZui6jdXj7xPUWOD8feX2NNEjTAEeX7DXOhnozNAkk/Z98WUV2B
  xUBqhRi8vhVmaCM8E+JkHzAc+7/HVYBTuPUS7lYPby5w95gs3zVxrX8d1++IXg/u/F/47zUxhdaELMw2
  deD8XLhrNPx2FQ83FxrjnVvEKQJyD2OoqxbC2KcUGYJ/3fhiupn/YpnZsl5+6mfQuZRJEoZ/FH2n4DEH
  wzgBBBagBr0ZZCEkl74s4KFZp6JJw/ZSjMRXsXXXWvwcTpaUEDii708HGw== John Doe@MACHINE
  user@host:~$
```

> **Important**
> 
> Please take note of the extra line-breaks introduced in the key above
> for formatting purposes. Please be sure to copy and paste your key
> without line-breaks.

Copy the string starting with ssh-rsa to your clipboard and then paste
it into the box for RSA keys. Make **absolutely sure** no extra spaces
or line feeds are entered in the middle of the RSA string.

Verify that the ssh connection works for you.

``` 
  user@host:~$ ssh user@localhost -p 29418
  The authenticity of host '[localhost]:29418 ([127.0.0.1]:29418)' can't be established.
  RSA key fingerprint is db:07:3d:c2:94:25:b5:8d:ac:bc:b5:9e:2f:95:5f:4a.
  Are you sure you want to continue connecting (yes/no)? yes
  Warning: Permanently added '[localhost]:29418' (RSA) to the list of known hosts.

  ****    Welcome to Gerrit Code Review    ****

  Hi user, you have successfully connected over SSH.

  Unfortunately, interactive shells are disabled.
  To clone a hosted Git repository, use:

  git clone ssh://user@localhost:29418/REPOSITORY_NAME.git

  user@host:~$
```

## Project creation

Your base Gerrit server is now running and you have a user that’s ready
to interact with it. You now have two options, either you create a new
test project to work with or you already have a git with history that
you would like to import into Gerrit and try out code review on.

### New project from scratch

If you choose to create a new repository from scratch, it’s easier for
you to create a project with an initial commit in it. That way first
time setup between client and server is easier.

This is done via the SSH
port:

``` 
  user@host:~$ ssh -p 29418 user@localhost gerrit create-project demo-project --empty-commit
  user@host:~$
```

This will create a repository that you can clone to work with.

### Already existing project

The other alternative is if you already have a git project that you want
to try out Gerrit on. First you have to create the project. This is done
via the SSH
port:

``` 
  user@host:~$ ssh -p 29418 user@localhost gerrit create-project demo-project
  user@host:~$
```

You need to make sure that at least initially your account is granted
"Create Reference" privileges for the refs/heads/\* reference. This is
done via the web interface in the Admin/Projects/Access page that
correspond to your project.

After that it’s time to upload the previous history to the
server:

``` 
  user@host:~/my-project$ git push ssh://user@localhost:29418/demo-project *:*
  Counting objects: 2011, done.
  Writing objects: 100% (2011/2011), 456293 bytes, done.
  Total 2011 (delta 0), reused 0 (delta 0)
  To ssh://user@localhost:29418/demo-project
   * [new branch]      master -> master
  user@host:~/my-project$
```

This will create a repository that you can clone to work with.

## My first change

Download a local clone of the repository and move into it

``` 
  user@host:~$ git clone ssh://user@localhost:29418/demo-project
  Cloning into demo-project...
  remote: Counting objects: 2, done
  remote: Finding sources: 100% (2/2)
  remote: Total 2 (delta 0), reused 0 (delta 0)
  user@host:~$ cd demo-project
  user@host:~/demo-project$
```

Then make a change to it and upload it as a reviewable change in Gerrit.

``` 
  user@host:~/demo-project$ date > testfile.txt
  user@host:~/demo-project$ git add testfile.txt
  user@host:~/demo-project$ git commit -m "My pretty test commit"
  [master ff643a5] My pretty test commit
   1 files changed, 1 insertions(+), 0 deletions(-)
   create mode 100644 testfile.txt
  user@host:~/demo-project$
```

Usually when you push to a remote git, you push to the reference
`'/refs/heads/branch'`, but when working with Gerrit you have to push to
a virtual branch representing "code review before submission to branch".
This virtual name space is known as /refs/for/\<branch\>

``` 
  user@host:~/demo-project$ git push origin HEAD:refs/for/master
  Counting objects: 4, done.
  Writing objects: 100% (3/3), 293 bytes, done.
  Total 3 (delta 0), reused 0 (delta 0)
  remote:
  remote: New Changes:
  remote:   http://localhost:8080/1
  remote:
  To ssh://user@localhost:29418/demo-project
   * [new branch]      HEAD -> refs/for/master
  user@host:~/demo-project$
```

You should now be able to access your change by browsing to the http URL
suggested above, <http://localhost:8080/1>

## Quick Installation Complete

This covers the scope of getting Gerrit started and your first change
uploaded. It doesn’t give any clue as to how the review workflow works,
please read [Default
Workflow](http://source.android.com/source/life-of-a-patch) to learn
more about the workflow of Gerrit.

To read more on the installation of Gerrit please see [the detailed
installation page](install.html).

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX


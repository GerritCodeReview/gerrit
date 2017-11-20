---
title: " gerrit show-caches"
sidebar: cmd_sidebar
permalink: cmd-show-caches.html
---
## NAME

gerrit show-caches - Display current cache statistics

## SYNOPSIS

> 
> 
>     ssh -p <port> <host> gerrit show-caches
>       [--gc]
>       [--show-jvm]

## DESCRIPTION

Display statistics about the size and hit ratio of in-memory caches.

## OPTIONS

  - \--gc  
    Request Java garbage collection before displaying information about
    the Java memory heap.

  - \--show-jvm  
    List the name and version of the Java virtual machine, host
    operating system, and other details about the environment that
    Gerrit Code Review is running in.

  - \--show-threads  
    Show detailed counts for Gerrit specific threads.

  - \--width; -w  
    Width of the output table.

## ACCESS

The caller must be a member of a group that is granted one of the
following capabilities:

  - [View Caches](access-control.html#capability_viewCaches)

  - [Maintain Server](access-control.html#capability_maintainServer)

  - [Administrate
    Server](access-control.html#capability_administrateServer)

The summary information about SSH, threads, tasks, memory and JVM are
only printed out if the caller is a member of a group that is granted
the [Administrate
Server](access-control.html#capability_administrateServer) or [Maintain
Server](access-control.html#capability_maintainServer) capability.

## SCRIPTING

Intended for interactive use only.

## EXAMPLES

``` 
  $ ssh -p 29418 review.example.com gerrit show-caches
  Gerrit Code Review        2.9                       now   11:14:13   CEST
                                                   uptime    6 days 20 hrs
    Name                          |Entries              |  AvgGet |Hit Ratio|
                                  |   Mem   Disk   Space|         |Mem  Disk|
  --------------------------------+---------------------+---------+---------+
    accounts                      |  4096               |   3.4ms | 99%     |
    adv_bases                     |                     |         |         |
    changes                       |                     |  27.1ms |  0%     |
    groups                        |  5646               |  11.8ms | 97%     |
    groups_bymember               |                     |         |         |
    groups_byname                 |                     |         |         |
    groups_bysubgroup             |   230               |   2.4ms | 62%     |
    groups_byuuid                 |  5612               |  29.2ms | 99%     |
    groups_external               |     1               |   1.5s  | 98%     |
    ldap_group_existence          |                     |         |         |
    ldap_groups                   |   650               | 680.5ms | 99%     |
    ldap_groups_byinclude         |  1024               |         | 83%     |
    ldap_usernames                |   390               |   3.8ms | 81%     |
    permission_sort               | 16384               |         | 99%     |
    plugin_resources              |                     |         |         |
    project_list                  |     1               |   3.8s  | 99%     |
    projects                      |  6477               |   2.9ms | 99%     |
    sshkeys                       |  2048               |  12.5ms | 99%     |
  D diff                          |  1299  62033 132.36m|  22.0ms | 85%  99%|
  D diff_intraline                | 12777 218651 128.45m| 171.1ms | 31%  96%|
  D git_tags                      |     3      6  11.85k|         |  0% 100%|
  D web_sessions                  |  1024 151714  59.10m|         | 99%  57%|

  SSH:    385  users, oldest session started    6 days 20 hrs ago
  Tasks:   10  total =    6 running +      0 ready +    4 sleeping
  Mem:  14.94g total =   3.04g used +  11.89g free +  10.00m buffers
        28.44g max
           107 open files

  Threads: 4 CPUs available, 371 threads
```

## SEE ALSO

  - [gerrit flush-caches](cmd-flush-caches.html)

  - [Cache Configuration](config-gerrit.html#cache)

  - [Standard Caches](config-gerrit.html#cache_names)

## GERRIT

Part of [Gerrit Code Review](index.html)

## SEARCHBOX


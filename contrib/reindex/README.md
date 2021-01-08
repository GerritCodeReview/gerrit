# Incremental reindexing during upgrade of large gerrit site

In order to shorten the downtime needed to reindex changes during a
Gerrit upgrade the following strategy can be used:

- index preparation
  - create a full consistent backup
  - note down the timestamp when the backup was created (backup-time)
  - create a complete copy of the production system from the backup
  - upgrade this copy to the new Gerrit version
  - online reindex this copy
- upgrade of the production system
  - make system unavailable so that users can't reach it anymore
    e.g. by changing port numbers (downtime starts)
  - take a full backup
  - run

    ``` bash
    ./reindex.py -u gerrit-url -s backup-time
    ```

    to write the list of changes which have been created or modified
    since the backup for the index preparation was created to a file
    "changes-to-reindex.list"
  - upgrade the production system to the new gerrit version skipping
    reindexing
  - copy the bulk of the new index from the copy system to the
    production system
  - run

    ``` bash
    ./reindex.py -u gerrit-url
    ```

    this reindexes all changes which have been created or modified after
    the backup was taken reading these changes from the file
    "changes-to-reindex.list"
  - smoketest the system
  - make the production system available to the users again
    (downtime ends)

## Online help

For help on all available options run

``` bash
./reindex -h
```

## Python environment

Prerequisites:

- python 3.9
- pipenv

Install virtual python environment and run the script

``` bash
pipenv sync
pipenv shell
./reindex <options>
```

# Gerrit Maintenance

This package provides a set of tools that can be used to maintain a Gerrit site.
Some tools will also work with git repositories in general.

The following tools are available:

- [Extended Git GarbageCollection](#extended-git-garbagecollection)

## Dependencies

- Python > 3.12

For development, some additional python libraries are required. These are managed
with pipenv. To install them, run:

```sh
pipenv sync --dev
```

## Development

### Code Style

This package is formatted using `black`. To automatically format all python files,
run:

```sh
pipenv run black .
```

`flake8` is being used to identify code style issues. To run it, use:

```sh
pipenv run flake8 .
```

### Tests

To execute tests, run:

```sh
pipenv run pytest
```

## Usage

The gerrit-maintenance CLI provides a toolbox to run scripts for performing
maintenance tasks on a Gerrit site. The CLI uses a nested command structure. The
available commands will be described in the following sections.

To start the CLI, run:

```sh
pipenv run python ./gerrit-maintenance.py -d $SITE -h
```

At this level, the path to the Gerrit site has to be provided.

The next layer deals with the different aspects of a Gerrit site:

### Projects

This set of subcommands deals with maintaining the projects/repositories in
the Gerrit site. To get an overview of available commands, run:

```sh
pipenv run python ./gerrit-maintenance.py -d $SITE projects -h
```

By default the selected subcommand will run on all projects in the site, but the
list can be filtered by either selecting projects specifically

```sh
pipenv run python ./gerrit-maintenance.py \
  -d $SITE \
  projects \
  --project All-Users \
  --project All-Projects \
  $CMD
```

or by skipping some projects

```sh
pipenv run python ./gerrit-maintenance.py \
  -d $SITE \
  projects \
  --skip All-Users \
  --skip All-Projects \
  $CMD
```

The maintenance scripts available for projects are:

#### Git Garbage Collection

To run Git GC as part of the gerrit-maintenance CLI, run:

```sh
pipenv run python ./gerrit-maintenance.py \
  -d $SITE \
  projects \
  gc
```

You may run it as well as a [standalone git extension](#extended-git-garbagecollection).

You can provide git configuration options to git gc using the `-c` option:

```sh
pipenv run python ./gerrit-maintenance.py \
  -d $SITE \
  projects \
  gc \
  -c repack.writebitmaps=false
```

As with the standalone git extension, all arguments provided in addition to the
ones known by the CLI will be forwarded to the `git gc` command, e.g. the following
command will suppress all progress reports logged by git:

```sh
pipenv run python ./gerrit-maintenance.py \
  -d $SITE \
  projects \
  gc \
  --quiet
```

The CLI also includes all extended features mentioned in [this section](#extended-features).

## Extended Git GarbageCollection

Git provides a GarbageCollection command (`git gc`) to clean up repositories.
Unfortunately, this command misses some cleanup steps that help improving
the performance of a repository.

The python script provided here wraps `git gc` and adds additional options and
cleanup steps.

### Dependencies

Refer to [general dependencies](#dependencies)

No non-standard libraries are being used to keep running this tool simple.

### Installation

Put this directory somewhere convenient and ensure that the `git-gcplus`
executable is present in the `PATH` environment variable, e.g. by symlinking it
to `/usr/local/bin`.

### Usage

The extended git gc can be called like any other git-command:

```sh
git gcplus
```

This will run the extended gc in the current working directory (if it is a
repository).

A specific repository can be set as usual using `-C`:

```sh
git -C "/var/gerrit/git/All-Users.git" gcplus
```

The repository configuration can also be overridden as usual:

```sh
git -c repack.writebitmaps=false gcplus
```

The script will further forward all [options](https://git-scm.com/docs/git-gc#_options)
provided by the `git gc` command to the included `git gc` run, e.g. the following
command will suppress all progress reports written by git:

```sh
git gcplus --quiet
```

The extended git gc script also adds a few more options:

- `--pack-all-refs` / `-r`

### Extended features

#### Packing all refs

Enabled by: `--pack-all-refs` / `-r`

Git gc by default only packs refs that are already packed. That potentially
leaves a lot of loose refs in large projects, some of which are not actively
being used anymore.

Enabling this feature conveniently runs `git pack-refs --all`, if there are more
than 10 loose refs after the `git-gc` run.

#### Preserving packs

Enabled by configuring `gc.preserveoldpacks = true`

As part of git gc packs are rewritten, which includes the change of the pack names.
If a long running request accesses a pack that is being recreated in this way
while the request is running, the request can fail, because the server tries
and fails to access the now deleted old pack. This can lead to a significant
amount of failing requests on large repositories and greatly inconvenience users.

Jgit provides a feature to prevent the above described scenario by allowing to
preserve packs. This is done by hardlinking them before the gc and falling back
to the preserved pack in case a request fails to find a pack. Unfortunately, this
is not supported by native git.

This extended gc script adds support for the following options added by jgit:

- `gc.preserveoldpacks`: Whether to preserve packs before running `git gc`.
- `gc.prunepreserved`: Whether to prune preserved packs created by previous runs.

Setting those options will prevent failures as described above, if the server uses
jgit (e.g. Gerrit), at a cost of using more storage.

#### Lock handling

Enabled: Always

Git guards gc by locking a lock file "gc.pid" before starting execution.
The lock file contains the pid and hostname of the process holding the
lock. Git tries to kill the process holding that lock if the lock file
wasn't modified in the last 12 hours and was started from the same host.

This does not work in a scenario where git gc is running in an ephemeral
environment like Kubernetes, where the host might actually always be different,
e.g. if git gc is running in a Kubernetes CronJob on a repository in a shared
filesystem.

The extended git gc will always delete the lock, if it hasn't been modified for
at least 12 h. This matches the behavior of jgit.

#### Deletion of empty ref directories

Enabled: Always

Git gc might leave empty directories after packing refs. This happens if all refs
in a namespace have been packed. This potentially leaves thousands of empty
directories, especially with Gerrit's NoteDB. This can cause significant performance
issues on slow filesystems like NFS.

The extended gc will delete empty ref directories older than 1h.

#### Deletion of stale incoming packs

Enabled: Always

If a git server crashes while still serving push requests the temporary incoming
pack file will never be cleaned up, unnecessarily cluttering the repository.

The extended gc will consider incoming packs not modified for 1 day to be stale
and delete them.

#### Using a marker file to enable aggressive gc

Enabled by creating a file named `gc-aggressive` or `gc-aggressive-once` in the
repository's `.git` directory.

In some use cases an aggressive GC should be run for a while as part of a scheduled
git gc. In that case it is not always convenient to change the calling script.

The extended gc will check for the existence of the following files:

- `gc-aggressive`
- `gc-aggressive-once`

In the latter case, the file will be deleted, effectively causing an aggressive
gc just once.

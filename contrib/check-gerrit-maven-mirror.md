# Gerrit Maven mirror coverage checker

`check-gerrit-maven-mirror.py` checks whether every artifact recorded in
`external_deps.lock.json` is available from Gerrit's Maven mirror:

```sh
./contrib/check-gerrit-maven-mirror.py --workers=16
```

The script derives the Maven repository path for each locked artifact and
classifier from the rules_jvm_external lock file, then sends parallel `HEAD`
requests to `https://gerrit-maven.storage.googleapis.com`.

By default it prints only missing artifacts:

```text
404    javax.servlet:javax.servlet-api:jar    https://...
404    javax.servlet:javax.servlet-api:sources    https://...
```

The summary is printed to stderr:

```text
checked=298 mirrored=288 missing=10
```

The command exits with:

* `0` when all locked artifacts are mirrored;
* `1` when one or more artifacts are missing;
* `2` when the lock file cannot be read.

Use `--include-ok` to print mirrored artifacts too, `--mirror` to check another
Maven repository, and `--lock-file` to check a different RJE lock file.

This is intended as a maintainer diagnostic before making Gerrit CI prefer the
mirror over Maven Central, and after dependency updates or manual mirror
uploads.

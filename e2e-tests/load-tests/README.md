## Gerrit - performance test suite

### Prerequisites

* [Scala 2.12][scala]

[scala]: https://www.scala-lang.org/download/

### How to build

```bash
sbt compile
```

### Setup

If you are running SSH commands the private keys of the users used for testing need to go in `/tmp/ssh-keys`.
The keys need to be generated this way (JSch won't validate them [otherwise](https://stackoverflow.com/questions/53134212/invalid-privatekey-when-using-jsch):

```bash
ssh-keygen -m PEM -t rsa -C "test@mail.com" -f /tmp/ssh-keys/id_rsa
```

NOTE: Don't forget to add the public keys for the testing user(s) to your git server

#### Input

The ReplayRecordsScenario is expecting the [src/test/resources/data/requests.json](/src/test/resources/data/requests.json) file.
Here below an example:

```json
[
  {
    "url": "ssh://admin@localhost:29418/loadtest-repo.git",
    "cmd": "clone"
  },
  {
    "url": "http://localhost:8080/loadtest-repo.git",
    "cmd": "fetch"
  }
]
```

Valid commands are:
* fetch
* pull
* push
* clone

### How to run the tests

All tests:
```
sbt "gatling:test"
```

Single test:
```
sbt "gatling:testOnly com.google.gerrit.scenarios.ReplayRecordsFromFeederScenario"
```

Generate report:
```
sbt "gatling:lastReport"
```
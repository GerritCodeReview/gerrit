# How to build the Docker image

```$shell
docker build . -t e2e-tests
```

# How to run a test

```$shell
docker run -it e2e-tests -s com.google.gerrit.scenarios.ReplayRecordsFromFeederScenario
```

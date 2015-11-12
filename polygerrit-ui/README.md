# PolyGerrit

For local testing against production data...

```sh
rm -rf bower_components && \
buck build //lib/js:polygerrit_components && \
unzip ../buck-out/gen/lib/js/polygerrit_components/polygerrit_components.bower_components.zip && \
go run server.go
```

Then visit http://localhost:8081

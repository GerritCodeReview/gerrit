bazel build release && java -jar bazel-bin/release.war init --dev --install-all-plugins --no-auto-start -d ../gerrit-site -b

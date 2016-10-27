package com.google.gerrit.httpd.raw;

import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;

import java.io.IOException;
import java.nio.file.Path;

public class BazelUtils implements BuildSystem {
  Path sourceRoot;

  public BazelUtils(Path sourceRoot) {
    this.sourceRoot = sourceRoot;
  }

  @Override
  public void build(Label l) throws IOException, BuildFailureException {
    throw new IOException("not implemented");
  }
  @Override
  public Path targetPath(Label l) {
    return sourceRoot.resolve("bazel-bin").resolve(l.pkg).resolve(l.artifact);
  }
}

// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.ssh;

import org.spearce.jgit.transport.UploadPack;

import java.io.IOException;

/** Publishes Git repositories over SSH using the Git upload-pack protocol. */
class Upload extends AbstractGitCommand {
  @Override
  protected void runImpl() throws IOException {
    closeDb();

    final UploadPack up = new UploadPack(repo);
    up.upload(in, out, err);
  }

  @Override
  protected String parseCommandLine(final String[] args) throws Failure {
    if (args.length != 1)
      throw new Failure(1, "usage: " + getName() + " '/project.git'");
    return args[0];
  }
}

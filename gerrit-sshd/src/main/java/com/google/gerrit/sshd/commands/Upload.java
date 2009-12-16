// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.sshd.commands;

import org.eclipse.jgit.transport.UploadPack;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

/** Publishes Git repositories over SSH using the Git upload-pack protocol. */
final class Upload extends AbstractGitCommand {

  /**
   * Copies the data from JGit into MINA's output stream. This is done
   * in a separate thread in an attempt to offload the MINA encrytion code
   * and the JGit code on separate CPUs.
   */
  private final class CopyRunnable implements CommandRunnable {
     private final InputStream input;
     private final OutputStream output;

     CopyRunnable(InputStream input, OutputStream output) {
       this.input = input;
       this.output = output;
     }

     @Override
     public void run() throws Exception {
       byte[] buff = new byte[32768];
       int read = input.read(buff);
       while (read > 0) {
         output.write(buff, 0, read);
         output.flush();
         read = input.read(buff);
       }
     }
  }

  @Override
  protected void runImpl() throws IOException {
    final UploadPack up = new UploadPack(repo);
    PipedOutputStream source = new PipedOutputStream();
    PipedInputStream sink = new PipedInputStream(source, 128 * 1024 * 1024);
    CopyRunnable copy = new CopyRunnable(sink, out);
    startThread(copy);
    up.upload(in, source, err);
  }
}

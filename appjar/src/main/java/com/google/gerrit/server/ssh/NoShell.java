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

import org.apache.sshd.server.ShellFactory;
import org.spearce.jgit.lib.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * Dummy shell which prints a message and terminates.
 * <p>
 * This implementation is used by {@link SshServlet} to ensure clients who try
 * to SSH directly to this server without supplying a command will get a
 * reasonable error message, but cannot continue further.
 */
class NoShell implements ShellFactory {
  public Shell createShell() {
    return new Shell() {
      private InputStream in;
      private OutputStream out;
      private OutputStream err;
      private ExitCallback exit;

      public void setInputStream(final InputStream in) {
        this.in = in;
      }

      public void setOutputStream(final OutputStream out) {
        this.out = out;
      }

      public void setErrorStream(final OutputStream err) {
        this.err = err;
      }

      public void setExitCallback(final ExitCallback callback) {
        this.exit = callback;
      }

      public void start(final Map<String, String> env) throws IOException {
        err.write(Constants.encodeASCII("gerrit: no shell available\n"));
        in.close();
        out.close();
        err.close();
        exit.onExit(127);
      }

      public void destroy() {
      }
    };
  }
}

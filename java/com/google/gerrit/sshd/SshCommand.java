// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.sshd;

import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.logging.TraceContext;
import java.io.IOException;
import java.io.PrintWriter;
import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Option;

public abstract class SshCommand extends BaseCommand {
  @Option(name = "--trace", usage = "enable request tracing")
  private boolean trace;

  @Option(name = "--trace-id", usage = "trace ID (can only be set if --trace was set too)")
  private String traceId;

  protected PrintWriter stdout;
  protected PrintWriter stderr;

  @Override
  public void start(Environment env) throws IOException {
    startThread(
        new CommandRunnable() {
          @Override
          public void run() throws Exception {
            parseCommandLine();
            stdout = toPrintWriter(out);
            stderr = toPrintWriter(err);
            try (TraceContext traceContext = enableTracing()) {
              SshCommand.this.run();
            } finally {
              stdout.flush();
              stderr.flush();
            }
          }
        },
        AccessPath.SSH_COMMAND);
  }

  protected abstract void run() throws UnloggedFailure, Failure, Exception;

  private TraceContext enableTracing() throws UnloggedFailure {
    if (!trace && traceId != null) {
      throw die("A trace ID can only be set if --trace was specified.");
    }
    return TraceContext.newTrace(
        trace,
        traceId,
        (tagName, traceId) -> stderr.println(String.format("%s: %s", tagName, traceId)));
  }
}

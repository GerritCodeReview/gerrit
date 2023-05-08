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

import com.google.common.base.Throwables;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.CancellationMetrics;
import com.google.gerrit.server.DeadlineChecker;
import com.google.gerrit.server.DynamicOptions;
import com.google.gerrit.server.InvalidDeadlineException;
import com.google.gerrit.server.RequestInfo;
import com.google.gerrit.server.RequestListener;
import com.google.gerrit.server.cache.PerThreadCache;
import com.google.gerrit.server.cancellation.RequestCancelledException;
import com.google.gerrit.server.cancellation.RequestStateContext;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.logging.PerformanceLogContext;
import com.google.gerrit.server.logging.PerformanceLogger;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.channel.ChannelSession;
import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Option;

public abstract class SshCommand extends BaseCommand {
  @Inject private DynamicSet<PerformanceLogger> performanceLoggers;
  @Inject private PluginSetContext<RequestListener> requestListeners;
  @Inject @GerritServerConfig private Config config;
  @Inject private DeadlineChecker.Factory deadlineCheckerFactory;
  @Inject private CancellationMetrics cancellationMetrics;

  @Option(name = "--trace", usage = "enable request tracing")
  private boolean trace;

  @Option(name = "--trace-id", usage = "trace ID (can only be set if --trace was set too)")
  private String traceId;

  @Option(name = "--deadline", usage = "deadline after which the request should be aborted)")
  private String deadline;

  protected PrintWriter stdout;
  protected PrintWriter stderr;

  @Override
  public void start(ChannelSession channel, Environment env) throws IOException {
    startThread(
        () -> {
          try (PerThreadCache ignored = PerThreadCache.create();
              DynamicOptions pluginOptions = new DynamicOptions(injector, dynamicBeans)) {
            parseCommandLine(pluginOptions);
            stdout = toPrintWriter(out);
            stderr = toPrintWriter(err);
            try (TraceContext traceContext = enableTracing();
                PerformanceLogContext performanceLogContext =
                    new PerformanceLogContext(config, performanceLoggers)) {
              RequestInfo requestInfo =
                  RequestInfo.builder(RequestInfo.RequestType.SSH, user, traceContext).build();
              try (RequestStateContext requestStateContext =
                  RequestStateContext.open()
                      .addRequestStateProvider(
                          deadlineCheckerFactory.create(requestInfo, deadline))) {
                requestListeners.runEach(l -> l.onRequest(requestInfo));
                SshCommand.this.run();
              } catch (InvalidDeadlineException e) {
                stderr.println(e.getMessage());
              } catch (RuntimeException e) {
                Optional<RequestCancelledException> requestCancelledException =
                    RequestCancelledException.getFromCausalChain(e);
                if (!requestCancelledException.isPresent()) {
                  Throwables.throwIfUnchecked(e);
                }
                cancellationMetrics.countCancelledRequest(
                    requestInfo, requestCancelledException.get().getCancellationReason());
                StringBuilder msg =
                    new StringBuilder(requestCancelledException.get().formatCancellationReason());
                if (requestCancelledException.get().getCancellationMessage().isPresent()) {
                  msg.append(
                      String.format(
                          " (%s)", requestCancelledException.get().getCancellationMessage().get()));
                }
                stderr.println(msg.toString());
              }
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

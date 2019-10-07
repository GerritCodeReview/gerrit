// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.git;

import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.server.logging.TraceContext;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.transport.FetchV2Request;
import org.eclipse.jgit.transport.LsRefsV2Request;
import org.eclipse.jgit.transport.ProtocolV2Hook;

/**
 * Git hook for ls-refs and fetch that enables Gerrit request tracing if the user sets the 'trace'
 * server option.
 *
 * <p>This hook is only invoked if Git protocol v2 is used.
 *
 * <p>If the 'trace' server option is specified without value, this means without providing a trace
 * ID, a trace ID is generated, but it's not returned to the client. Hence users are advised to
 * always provide a trace ID.
 */
public class TracingHook implements ProtocolV2Hook, AutoCloseable {
  private TraceContext traceContext;

  @Override
  public void onLsRefs(LsRefsV2Request req) {
    maybeStartTrace(req.getServerOptions());
  }

  @Override
  public void onFetch(FetchV2Request req) {
    maybeStartTrace(req.getServerOptions());
  }

  @Override
  public void close() {
    if (traceContext != null) {
      traceContext.close();
    }
  }

  /**
   * Starts request tracing if 'trace' server option is set.
   *
   * @param serverOptionList list of provided server options
   */
  private void maybeStartTrace(List<String> serverOptionList) {
    checkState(traceContext == null, "Trace was already started.");

    Optional<String> traceOption = parseTraceOption(serverOptionList);
    traceContext =
        TraceContext.newTrace(
            traceOption.isPresent(),
            traceOption.orElse(null),
            (tagName, traceId) -> {
              // TODO(ekempin): Return trace ID to client
            });
  }

  private Optional<String> parseTraceOption(List<String> serverOptionList) {
    if (serverOptionList == null || serverOptionList.isEmpty()) {
      return Optional.empty();
    }

    Optional<String> traceOption =
        serverOptionList.stream().filter(o -> o.startsWith("trace")).findAny();
    if (!traceOption.isPresent()) {
      return Optional.empty();
    }

    int e = traceOption.get().indexOf('=');
    if (e > 0) {
      // trace option was specified with trace ID: "--trace=<trace-ID>"
      return Optional.of(traceOption.get().substring(e + 1));
    }

    // trace option was specified without trace ID: "--trace",
    // return an empty string so that a trace ID is generated
    return Optional.of("");
  }
}

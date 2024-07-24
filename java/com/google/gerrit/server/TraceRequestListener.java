// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.logging.RequestId;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;

/**
 * Request listener that sets additional logging tags and enables tracing automatically if the
 * request matches any tracing configuration in gerrit.config (see description of
 * 'tracing.<trace-id>' subsection in config-gerrit.txt).
 */
@Singleton
public class TraceRequestListener implements RequestListener {
  public static String TAG_REQUEST = "request";

  private static String TAG_PROJECT = "project";
  private static String SECTION_TRACING = "tracing";

  private final ImmutableList<RequestConfig> traceConfigs;

  @Inject
  TraceRequestListener(@GerritServerConfig Config cfg) {
    this.traceConfigs = RequestConfig.parseTraceConfigs(cfg, SECTION_TRACING);
  }

  @Override
  public void onRequest(RequestInfo requestInfo) {
    requestInfo.traceContext().addTag(TAG_REQUEST, requestInfo.formatForLogging());
    requestInfo.project().ifPresent(p -> requestInfo.traceContext().addTag(TAG_PROJECT, p));
    traceConfigs.stream()
        .filter(traceConfig -> traceConfig.matches(requestInfo))
        .forEach(
            traceConfig ->
                requestInfo
                    .traceContext()
                    .forceLogging()
                    .addTag(RequestId.Type.TRACE_ID, traceConfig.id()));
  }
}

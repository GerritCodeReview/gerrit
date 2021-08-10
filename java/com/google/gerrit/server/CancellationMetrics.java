// Copyright (C) 2021 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Counter3;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.cancellation.RequestStateProvider;
import com.google.gerrit.server.logging.Metadata;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/** Metrics for request cancellations and deadlines. */
@Singleton
public class CancellationMetrics {
  private final Counter3<String, String, String> advisoryDeadlineCount;
  private final Counter3<String, String, RequestStateProvider.Reason> cancelledRequestsCount;
  private final Counter1<String> receiveTimeoutCount;

  @Inject
  CancellationMetrics(MetricMaker metrics) {
    this.advisoryDeadlineCount =
        metrics.newCounter(
            "cancellation/advisory_deadline_count",
            new Description("Exceeded advisory deadlines by request").setRate(),
            Field.ofString("request_type", Metadata.Builder::requestType)
                .description("The type of the request to which the advisory deadline applied.")
                .build(),
            Field.ofString("request_uri", Metadata.Builder::restViewName)
                .description(
                    "The URI of the request to which the advisory deadline applied"
                        + " (only set for request_type = REST).")
                .build(),
            Field.ofString("deadline_id", (metadataBuilder, resolveAllUsers) -> {})
                .description("The ID of the advisory deadline.")
                .build());

    this.cancelledRequestsCount =
        metrics.newCounter(
            "cancellation/cancelled_requests_count",
            new Description("Number of request cancellations by request").setRate(),
            Field.ofString("request_type", Metadata.Builder::requestType)
                .description("The type of the request that was cancelled.")
                .build(),
            Field.ofString("request_uri", Metadata.Builder::restViewName)
                .description(
                    "The URI of the request that was cancelled"
                        + " (only set for request_type = REST).")
                .build(),
            Field.ofEnum(
                    RequestStateProvider.Reason.class,
                    "cancellation_reason",
                    Metadata.Builder::cancellationReason)
                .description("The reason why the request was cancelled.")
                .build());

    this.receiveTimeoutCount =
        metrics.newCounter(
            "cancellation/receive_timeout_count",
            new Description(
                    "Number of requests that are cancelled because receive.timout is exceeded")
                .setRate(),
            Field.ofString("cancellation_type", (metadataBuilder, resolveAllUsers) -> {})
                .description("The cancellation type (graceful or forceful).")
                .build());
  }

  public void countAdvisoryDeadline(RequestInfo requestInfo, String deadlineId) {
    advisoryDeadlineCount.increment(
        requestInfo.requestType(),
        requestInfo.requestUri().map(CancellationMetrics::redactRequestUri).orElse(""),
        deadlineId);
  }

  public void countCancelledRequest(
      RequestInfo requestInfo, RequestStateProvider.Reason cancellationReason) {
    cancelledRequestsCount.increment(
        requestInfo.requestType(),
        requestInfo.requestUri().map(CancellationMetrics::redactRequestUri).orElse(""),
        cancellationReason);
  }

  @UsedAt(UsedAt.Project.GOOGLE)
  public void countCancelledRequest(
      String requestType,
      String redactedRequestUri,
      RequestStateProvider.Reason cancellationReason) {
    cancelledRequestsCount.increment(requestType, redactedRequestUri, cancellationReason);
  }

  public void countGracefulReceiveTimeout() {
    receiveTimeoutCount.increment("graceful");
  }

  public void countForcefulReceiveTimeout() {
    receiveTimeoutCount.increment("forceful");
  }

  /**
   * Redacts resource IDs from the given request URI.
   *
   * <p>resource IDs in the request URI are replaced with '*'.
   *
   * @param requestUri a REST URI that has path segments that alternate between view name and
   *     resource IDs (e.g. "/<view>", "/<view>/<id>", "/<view>/<id>/<view>",
   *     "/<view>/<id>/<view>/<id>", "/<view>/<id>/<view>/<id>/<view>" etc.), must be given without
   *     the '/a' prefix
   * @return the redacted request URI
   */
  @VisibleForTesting
  static String redactRequestUri(String requestUri) {
    requireNonNull(requestUri, "requestUri");
    checkState(!requestUri.startsWith("/a"), "request URI must not start with '/a'");

    StringBuilder redactedRequestUri = new StringBuilder();

    boolean hasLeadingSlash = false;
    boolean hasTrailingSlash = false;
    if (requestUri.startsWith("/")) {
      hasLeadingSlash = true;
      requestUri = requestUri.substring(1);
    }
    if (requestUri.endsWith("/")) {
      hasTrailingSlash = true;
      requestUri = requestUri.substring(0, requestUri.length() - 1);
    }

    boolean idPathSegment = false;
    for (String pathSegment : Splitter.on('/').split(requestUri)) {
      if (!idPathSegment) {
        redactedRequestUri.append("/" + pathSegment);
        idPathSegment = true;
      } else {
        redactedRequestUri.append("/");
        if (!pathSegment.isEmpty()) {
          redactedRequestUri.append("*");
        }
        idPathSegment = false;
      }
    }

    if (!hasLeadingSlash) {
      redactedRequestUri.deleteCharAt(0);
    }
    if (hasTrailingSlash) {
      redactedRequestUri.append('/');
    }

    return redactedRequestUri.toString();
  }
}

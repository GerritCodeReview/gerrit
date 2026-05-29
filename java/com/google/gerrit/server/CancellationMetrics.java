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
                    "The redacted URI of the request to which the advisory deadline applied"
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
                    "The redacted URI of the request that was cancelled"
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
        requestInfo.requestType(), requestInfo.redactedRequestUri().orElse(""), deadlineId);
  }

  public void countCancelledRequest(
      RequestInfo requestInfo, RequestStateProvider.Reason cancellationReason) {
    cancelledRequestsCount.increment(
        requestInfo.requestType(), requestInfo.redactedRequestUri().orElse(""), cancellationReason);
  }

  public void countCancelledRequest(
      RequestInfo.RequestType requestType,
      String requestUri,
      RequestStateProvider.Reason cancellationReason) {
    cancelledRequestsCount.increment(
        requestType.name(), RequestInfo.redactRequestUri(requestUri), cancellationReason);
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
}

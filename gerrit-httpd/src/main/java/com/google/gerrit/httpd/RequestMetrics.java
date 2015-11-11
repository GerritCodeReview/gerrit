// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.httpd;

import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.util.http.RequestUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import javax.servlet.ServletRequest;

import static com.google.common.base.Preconditions.checkNotNull;

@Singleton
public class RequestMetrics {
  public final Counter1<Integer> errors;
  public final Counter1<Integer> successes;

  /** HTTP request attribute for storing metrics. */
  public static final String ATTRIBUTE_METRICS =
      RequestUtil.class.getName() + "/Metrics";

  @Inject
  public RequestMetrics(MetricMaker metricMaker) {
    errors = metricMaker.newCounter(
        "rest/responses/errors",
        new Description("Rate of REST API error responses")
          .setRate()
          .setUnit("errors"),
        Field.ofInteger(
            "status", "HTTP status code"));
    successes = metricMaker.newCounter(
        "rest/responses/successes",
        new Description("Rate of REST API success responses")
          .setRate()
          .setUnit("successes"),
        Field.ofInteger(
            "status", "HTTP status code"));
  }

  public static RequestMetrics forRequest(ServletRequest req) {
    return (RequestMetrics) checkNotNull(req.getAttribute(ATTRIBUTE_METRICS));
  }

  public void attachToRequest(ServletRequest req) {
    req.setAttribute(ATTRIBUTE_METRICS, this);
  }
}

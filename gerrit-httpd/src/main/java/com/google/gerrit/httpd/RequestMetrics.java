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
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class RequestMetrics {
  final Counter1<Integer> errors;
  final Counter1<Integer> successes;

  @Inject
  public RequestMetrics(MetricMaker metricMaker) {
    errors =
        metricMaker.newCounter(
            "http/server/error_count",
            new Description("Rate of REST API error responses").setRate().setUnit("errors"),
            Field.ofInteger("status", "HTTP status code"));
    successes =
        metricMaker.newCounter(
            "http/server/success_count",
            new Description("Rate of REST API success responses").setRate().setUnit("successes"),
            Field.ofInteger("status", "HTTP status code"));
  }
}

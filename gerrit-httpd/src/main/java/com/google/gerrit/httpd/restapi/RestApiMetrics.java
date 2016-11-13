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

package com.google.gerrit.httpd.restapi;

import com.google.common.base.Strings;
import com.google.gerrit.httpd.restapi.RestApiServlet.ViewData;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Counter2;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.Histogram1;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer1;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class RestApiMetrics {
  private static final String[] PKGS = {
    "com.google.gerrit.server.", "com.google.gerrit.",
  };

  final Counter1<String> count;
  final Counter2<String, Integer> errorCount;
  final Timer1<String> serverLatency;
  final Histogram1<String> responseBytes;

  @Inject
  RestApiMetrics(MetricMaker metrics) {
    Field<String> view = Field.ofString("view", "view implementation class");
    count =
        metrics.newCounter(
            "http/server/rest_api/count",
            new Description("REST API calls by view").setRate(),
            view);

    errorCount =
        metrics.newCounter(
            "http/server/rest_api/error_count",
            new Description("REST API calls by view").setRate(),
            view,
            Field.ofInteger("error_code", "HTTP status code"));

    serverLatency =
        metrics.newTimer(
            "http/server/rest_api/server_latency",
            new Description("REST API call latency by view")
                .setCumulative()
                .setUnit(Units.MILLISECONDS),
            view);

    responseBytes =
        metrics.newHistogram(
            "http/server/rest_api/response_bytes",
            new Description("Size of response on network (may be gzip compressed)")
                .setCumulative()
                .setUnit(Units.BYTES),
            view);
  }

  String view(ViewData viewData) {
    String impl = viewData.view.getClass().getName().replace('$', '.');
    for (String p : PKGS) {
      if (impl.startsWith(p)) {
        impl = impl.substring(p.length());
        break;
      }
    }
    if (!Strings.isNullOrEmpty(viewData.pluginName) && !"gerrit".equals(viewData.pluginName)) {
      impl = viewData.pluginName + '-' + impl;
    }
    return impl;
  }
}

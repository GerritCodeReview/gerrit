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
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.registration.PluginName;
import com.google.gerrit.httpd.restapi.RestApiServlet.ViewData;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Counter3;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.Histogram1;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer2;
import com.google.gerrit.server.logging.Metadata;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class RestApiMetrics {
  private static final String[] PKGS = {
    "com.google.gerrit.server.", "com.google.gerrit.",
  };

  final Counter1<String> count;
  final Counter3<String, Integer, String> errorCount;
  final Timer2<String, String> serverLatency;
  final Histogram1<String> responseBytes;

  @Inject
  RestApiMetrics(MetricMaker metrics) {
    Field<String> viewField =
        Field.ofString("view", Metadata.Builder::className)
            .description("view implementation class")
            .build();
    Field<String> accessPathField =
        Field.ofString("access_path", Metadata.Builder::requestType)
            .description(
                "The access path through which the user accessed Gerrit (REST_API, WEB_BROWSER or"
                    + " UNKNOWN).")
            .build();
    count =
        metrics.newCounter(
            "http/server/rest_api/count",
            new Description("REST API calls by view").setRate(),
            viewField);

    errorCount =
        metrics.newCounter(
            "http/server/rest_api/error_count",
            new Description("REST API errors by view").setRate(),
            viewField,
            Field.ofInteger("error_code", Metadata.Builder::httpStatus)
                .description("HTTP status code")
                .build(),
            Field.ofString("cause", Metadata.Builder::cause)
                .description("The cause of the error.")
                .build());

    serverLatency =
        metrics.newTimer(
            "http/server/rest_api/server_latency",
            new Description("REST API call latency by view")
                .setCumulative()
                .setUnit(Units.MILLISECONDS),
            viewField,
            accessPathField);

    responseBytes =
        metrics.newHistogram(
            "http/server/rest_api/response_bytes",
            new Description("Size of response on network (may be gzip compressed)")
                .setCumulative()
                .setUnit(Units.BYTES),
            viewField);
  }

  String view(ViewData viewData) {
    return view(viewData.view.getClass(), viewData.pluginName);
  }

  String view(Class<?> clazz, @Nullable String pluginName) {
    String impl = clazz.getName().replace('$', '.');
    for (String p : PKGS) {
      if (impl.startsWith(p)) {
        impl = impl.substring(p.length());
        break;
      }
    }
    if (!Strings.isNullOrEmpty(pluginName) && !PluginName.GERRIT.equals(pluginName)) {
      impl = pluginName + '-' + impl;
    }
    return impl;
  }
}

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

package com.google.gerrit.metrics.dropwizard;

import com.codahale.metrics.Metric;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.ConfigResource;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.kohsuke.args4j.Option;

class ListMetrics implements RestReadView<ConfigResource> {
  private final CurrentUser user;
  private final DropWizardMetricMaker metrics;

  @Option(name = "--data-only", usage = "return only values")
  boolean dataOnly;

  @Option(
    name = "--prefix",
    aliases = {"-p"},
    metaVar = "PREFIX",
    usage = "match metric by exact match or prefix"
  )
  List<String> query = new ArrayList<>();

  @Inject
  ListMetrics(CurrentUser user, DropWizardMetricMaker metrics) {
    this.user = user;
    this.metrics = metrics;
  }

  @Override
  public Map<String, MetricJson> apply(ConfigResource resource) throws AuthException {
    if (!user.getCapabilities().canViewCaches()) {
      throw new AuthException("restricted to viewCaches");
    }

    SortedMap<String, MetricJson> out = new TreeMap<>();
    List<String> prefixes = new ArrayList<>(query.size());
    for (String q : query) {
      if (q.endsWith("/")) {
        prefixes.add(q);
      } else {
        Metric m = metrics.getMetric(q);
        if (m != null) {
          out.put(q, toJson(q, m));
        }
      }
    }

    if (query.isEmpty() || !prefixes.isEmpty()) {
      for (String name : metrics.getMetricNames()) {
        if (include(prefixes, name)) {
          out.put(name, toJson(name, metrics.getMetric(name)));
        }
      }
    }

    return out;
  }

  private MetricJson toJson(String q, Metric m) {
    return new MetricJson(m, metrics.getAnnotations(q), dataOnly);
  }

  private static boolean include(List<String> prefixes, String name) {
    if (prefixes.isEmpty()) {
      return true;
    }
    for (String p : prefixes) {
      if (name.startsWith(p)) {
        return true;
      }
    }
    return false;
  }
}

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

package com.google.gerrit.server.index;

import com.google.gerrit.metrics.CallbackMetric1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.logging.Metadata;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Random;

@Singleton
public class IndexMetrics {
  private static final Field<String> F_NAME =
      Field.ofString("index_name", Metadata.Builder::indexName)
          .description("The name of the index.")
          .build();

  @Inject
  public IndexMetrics(MetricMaker metrics) {
    CallbackMetric1<String, Long> indexMetric =
        metrics.newCallbackMetric(
            "indexes",
            Long.class,
            new Description("Indexes entries").setGauge().setUnit("entries"),
            F_NAME);

    metrics.newTrigger(
        indexMetric,
        () -> {
          Random random = new Random();
          indexMetric.set("account", random.nextLong());
          indexMetric.set("change", random.nextLong());
          indexMetric.set("group", random.nextLong());
          indexMetric.set("project", random.nextLong());
        });
  }
}

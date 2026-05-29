// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.lucene;

import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.inject.Inject;
import java.util.Collection;

class LuceneIndexMetrics {

  @Inject
  LuceneIndexMetrics(MetricMaker metrics, Collection<IndexDefinition<?, ?, ?>> defs) {
    for (IndexDefinition<?, ?, ?> def : defs) {
      String indexName = def.getName();

      metrics.newCallbackMetric(
          String.format("index/lucene/%s", indexName),
          Integer.class,
          new Description(String.format("%s Lucene Index documents", indexName))
              .setGauge()
              .setUnit("documents"),
          () -> {
            if (def.getIndexCollection().getSearchIndex() == null) {
              return -1;
            }
            return def.getIndexCollection().getSearchIndex().numDocs();
          });
    }
  }
}

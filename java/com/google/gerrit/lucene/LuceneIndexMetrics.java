package com.google.gerrit.lucene;

import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.inject.Inject;
import java.util.Collection;

public class LuceneIndexMetrics {

  @Inject
  public LuceneIndexMetrics(MetricMaker metrics, Collection<IndexDefinition<?, ?, ?>> defs) {
    for (IndexDefinition<?, ?, ?> def : defs) {
      String indexName = def.getName();

      metrics.newCallbackMetric(
          String.format("indexes/%s", indexName),
          Integer.class,
          new Description(String.format("%s Index documents", indexName))
              .setGauge()
              .setUnit("documents"),
          () -> {
            if (def.getIndexCollection().getSearchIndex() == null) {
              return -1;
            } else {
              return def.getIndexCollection().getSearchIndex().numDocs();
            }
          });
    }
  }

}

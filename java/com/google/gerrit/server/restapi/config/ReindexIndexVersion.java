package com.google.gerrit.server.restapi.config;

import static com.google.gerrit.server.git.QueueProvider.QueueType.BATCH;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.index.SiteIndexer;
import com.google.gerrit.index.SiteIndexer.Result;
import com.google.gerrit.server.config.IndexVersionResource;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.restapi.config.ReindexIndexVersion.Input;
import com.google.inject.Inject;
import java.util.Collection;

public class ReindexIndexVersion implements RestModifyView<IndexVersionResource, Input> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class Input {
    boolean reuse;
  }

  private final Collection<IndexDefinition<?, ?, ?>> indexDefs;
  private final ListeningExecutorService executor;

  @Inject
  ReindexIndexVersion(
      Collection<IndexDefinition<?, ?, ?>> indexDefs,
      @IndexExecutor(BATCH) ListeningExecutorService executor) {
    this.indexDefs = indexDefs;
    this.executor = executor;
  }

  @Override
  public Response<?> apply(IndexVersionResource rsrc, Input input)
      throws ResourceNotFoundException {
    String indexName = rsrc.getIndexResource().getName();
    for (IndexDefinition<?, ?, ?> def : indexDefs) {
      if (def.getName().equals(indexName)) {
        reindex(def, rsrc.getVersion(), input.reuse);
        return Response.accepted(
            String.format(
                "Index %s version %d submitted for reindexing", indexName, rsrc.getVersion()));
      }
    }
    throw new ResourceNotFoundException();
  }

  private <K, V, I extends Index<K, V>> void reindex(
      IndexDefinition<K, V, I> def, int version, boolean reuse) {
    I index = def.getIndexCollection().getWriteIndex(version);
    SiteIndexer<K, V, I> siteIndexer = def.getSiteIndexer(reuse);
    var unused =
        executor.submit(
            () -> {
              String name = def.getName();
              logger.atInfo().log("Starting reindex of %s version %d", name, version);
              Result result = siteIndexer.indexAll(index);
              if (result.success()) {
                logger.atInfo().log("Reindex %s version %s complete", name, version);
              } else {
                logger.atInfo().log(
                    "Reindex %s version %s failed. Successfully indexed %s, failed to index %s",
                    name, version, result.doneCount(), result.failedCount());
              }
            });
  }
}

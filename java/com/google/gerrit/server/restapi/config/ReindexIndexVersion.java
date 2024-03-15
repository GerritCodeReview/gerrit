package com.google.gerrit.server.restapi.config;

import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.server.config.IndexVersionResource;
import com.google.gerrit.server.index.IndexVersionReindexer;
import com.google.gerrit.server.restapi.config.ReindexIndexVersion.Input;
import com.google.inject.Inject;
import java.util.Collection;

public class ReindexIndexVersion implements RestModifyView<IndexVersionResource, Input> {
  public static class Input {
    boolean reuse;
  }

  private final Collection<IndexDefinition<?, ?, ?>> indexDefs;
  private final IndexVersionReindexer indexVersionReindexer;

  @Inject
  ReindexIndexVersion(
      Collection<IndexDefinition<?, ?, ?>> indexDefs, IndexVersionReindexer indexVersionReindexer) {
    this.indexDefs = indexDefs;
    this.indexVersionReindexer = indexVersionReindexer;
  }

  @Override
  public Response<?> apply(IndexVersionResource rsrc, Input input)
      throws ResourceNotFoundException {
    String indexName = rsrc.getIndexResource().getName();
    for (IndexDefinition<?, ?, ?> def : indexDefs) {
      if (def.getName().equals(indexName)) {
        var unused = indexVersionReindexer.reindex(def, rsrc.getVersion(), input.reuse);
        return Response.accepted(
            String.format(
                "Index %s version %d submitted for reindexing", indexName, rsrc.getVersion()));
      }
    }
    throw new ResourceNotFoundException();
  }
}

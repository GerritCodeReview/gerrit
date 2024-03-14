package com.google.gerrit.server.restapi.config;

import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.server.config.IndexVersionResource;
import com.google.gerrit.server.index.IndexVersionReindexer;
import com.google.gerrit.server.restapi.config.ReindexIndexVersion.Input;
import com.google.inject.Inject;

public class ReindexIndexVersion implements RestModifyView<IndexVersionResource, Input> {
  public static class Input {
    boolean reuse;
  }

  private final IndexVersionReindexer indexVersionReindexer;

  @Inject
  ReindexIndexVersion(IndexVersionReindexer indexVersionReindexer) {
    this.indexVersionReindexer = indexVersionReindexer;
  }

  @Override
  public Response<?> apply(IndexVersionResource rsrc, Input input)
      throws ResourceNotFoundException {
    IndexDefinition<?, ?, ?> def = rsrc.getIndexDefinition();
    int version = rsrc.getIndex().getSchema().getVersion();
    var unused = indexVersionReindexer.reindex(def, version, input.reuse);
    return Response.accepted(
        String.format("Index %s version %d submitted for reindexing", def.getName(), version));
  }
}

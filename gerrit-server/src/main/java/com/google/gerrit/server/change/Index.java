package com.google.gerrit.server.change;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.Index.Input;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.inject.Inject;

import java.util.concurrent.ExecutionException;

public class Index implements RestModifyView<ChangeResource, Input> {
  public static class Input {
  }

  private final ChangeIndexer indexer;

  @Inject
  Index(ChangeIndexer indexer) {
    this.indexer = indexer;
  }

  @Override
  public Object apply(ChangeResource rsrc, Input input) throws AuthException,
      InterruptedException, ExecutionException {
    CurrentUser caller = rsrc.getControl().getCurrentUser();
    if (!caller.getCapabilities().canAdministrateServer()) {
      throw new AuthException("index not permitted");
    }
    indexer.index(rsrc.getChange()).get();
    return Response.none();
  }
}

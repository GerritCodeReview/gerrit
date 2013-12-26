package com.google.gerrit.server.change;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class RevisionEdits implements ChildCollection<ChangeResource, RevisionEditResource> {
  private final DynamicMap<RestView<RevisionEditResource>> views;
  private final Provider<ListRevisionEdits> list;

  @Inject
  RevisionEdits(DynamicMap<RestView<RevisionEditResource>> views,
      Provider<ListRevisionEdits> list,
      Provider<ReviewDb> dbProvider) {
    this.views = views;
    this.list = list;
  }

  @Override
  public DynamicMap<RestView<RevisionEditResource>> views() {
    return views;
  }

  @Override
  public RestView<ChangeResource> list() throws ResourceNotFoundException {
    return list.get();
  }

  @Override
  public RevisionEditResource parse(ChangeResource change, IdString id)
      throws ResourceNotFoundException, OrmException {
    throw new IllegalStateException("not yet implemented");
  }
}

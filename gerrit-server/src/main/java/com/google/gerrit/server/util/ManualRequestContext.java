package com.google.gerrit.server.util;

import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.inject.Provider;
import com.google.inject.util.Providers;

/**
 * Closeable version of a {@link RequestContext} with manually-specified
 * providers.
 */
public class ManualRequestContext implements RequestContext, AutoCloseable {
  private final CurrentUser user;
  private final Provider<ReviewDb> db;
  private final ThreadLocalRequestContext requestContext;
  private final RequestContext old;

  ManualRequestContext(CurrentUser user, ReviewDb db,
      ThreadLocalRequestContext requestContext) {
    this.user = user;
    this.db = Providers.of(db);
    this.requestContext = requestContext;
    old = requestContext.setContext(this);
  }

  @Override
  public CurrentUser getCurrentUser() {
    return user;
  }

  @Override
  public Provider<ReviewDb> getReviewDbProvider() {
    return db;
  }

  @Override
  public void close() {
    requestContext.setContext(old);
    db.get().close();
  }
}

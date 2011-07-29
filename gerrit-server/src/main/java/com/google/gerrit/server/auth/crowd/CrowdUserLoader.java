package com.google.gerrit.server.auth.crowd;

import static com.google.gerrit.reviewdb.AccountExternalId.SCHEME_GERRIT;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.cache.EntryCreator;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;

class CrowdUserLoader extends EntryCreator<String, Account.Id> {
  private final SchemaFactory<ReviewDb> schema;

  @Inject
  CrowdUserLoader(SchemaFactory<ReviewDb> schema) {
    this.schema = schema;
  }

  @Override
  public Account.Id createEntry(final String username) throws Exception {
    try {
      final ReviewDb db = schema.open();
      try {
        final AccountExternalId extId =
            db.accountExternalIds().get(
                new AccountExternalId.Key(SCHEME_GERRIT, username));
        return extId != null ? extId.getAccountId() : null;
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      CrowdRealm.log.warn("Cannot query for username in database", e);
      return null;
    }
  }
}
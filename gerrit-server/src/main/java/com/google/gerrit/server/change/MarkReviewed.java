package com.google.gerrit.server.change;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountPatchReview;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.MarkReviewed.Input;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collections;

class MarkReviewed implements RestModifyView<PatchResource, Input> {
  static class Input {
    boolean reviewed;
  }

  private final Provider<CurrentUser> user;
  private final Provider<ReviewDb> dbProvider;

  @Inject
  MarkReviewed(Provider<CurrentUser> user,
      Provider<ReviewDb> dbProvider) {
    this.user = user;
    this.dbProvider = dbProvider;
  }

  @Override
  public Object apply(PatchResource resource, Input input) throws AuthException,
      BadRequestException, ResourceConflictException, Exception {
    Account.Id account = getAccountId();
    if (account == null) {
      throw new AuthException(
          "mark reviewed only available to authenticated users");
    }
    AccountPatchReview.Key key = new AccountPatchReview.Key(resource.getKey(), account);
    ReviewDb db = dbProvider.get();
    db.accounts().beginTransaction(account);
    try {
      AccountPatchReview apr = db.accountPatchReviews().get(key);
      if (apr == null && input.reviewed) {
        db.accountPatchReviews().insert(
            Collections.singleton(new AccountPatchReview(resource.getKey(), account)));
      } else if (apr != null && !input.reviewed) {
        db.accountPatchReviews().delete(Collections.singleton(apr));
      }
      db.commit();
      return null; // TODO(dborowitz): better return value?
    } finally {
      db.rollback();
    }
  }

  private Account.Id getAccountId() {
    CurrentUser u = user.get();
    if (u instanceof IdentifiedUser) {
      return ((IdentifiedUser) u).getAccountId();
    }
    return null;
  }
}

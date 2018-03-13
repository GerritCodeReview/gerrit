package com.google.gerrit.server.query.account;

import com.google.gerrit.index.query.PostFilterPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;
import java.util.Collection;
import java.util.Objects;

public class CanSeeChangePredicate extends PostFilterPredicate<AccountState> {
  private final Provider<ReviewDb> db;
  private final PermissionBackend permissionBackend;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ChangeNotes changeNotes;

  CanSeeChangePredicate(
      Provider<ReviewDb> db,
      PermissionBackend permissionBackend,
      IdentifiedUser.GenericFactory userFactory,
      ChangeNotes changeNotes) {
    this.db = db;
    this.permissionBackend = permissionBackend;
    this.userFactory = userFactory;
    this.changeNotes = changeNotes;
  }

  @Override
  public boolean match(AccountState accountState) throws OrmException {
    try {
      return permissionBackend
          .user(userFactory.create(accountState.getAccount().getId()))
          .database(db)
          .change(changeNotes)
          .test(ChangePermission.READ);
    } catch (PermissionBackendException e) {
      throw new OrmException("Failed to check if account can see change", e);
    }
  }

  @Override
  public int getCost() {
    return 1;
  }

  @Override
  public Predicate<AccountState> copy(Collection<? extends Predicate<AccountState>> children) {
    return new CanSeeChangePredicate(db, permissionBackend, userFactory, changeNotes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(changeNotes.getChange().getChangeId());
  }

  @Override
  public boolean equals(Object other) {
    if (other == null) {
      return false;
    }
    return getClass() == other.getClass()
        && changeNotes.getChange().getChangeId()
            == ((CanSeeChangePredicate) other).changeNotes.getChange().getChangeId();
  }
}

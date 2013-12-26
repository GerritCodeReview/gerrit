package com.google.gerrit.server.change;

import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ChangeControl;
import com.google.inject.TypeLiteral;

public class RevisionEditResource implements RestResource {
  public static final TypeLiteral<RestView<RevisionEditResource>> REVISION_EDIT_KIND =
      new TypeLiteral<RestView<RevisionEditResource>>() {};

  private final ChangeResource change;
  private final PatchSet ps;

  public RevisionEditResource(ChangeResource change, PatchSet ps) {
    this.change = change;
    this.ps = ps;
  }

  public boolean isCacheable() {
    return false;
  }

  public ChangeResource getChangeResource() {
    return change;
  }

  public ChangeControl getControl() {
    return getChangeResource().getControl();
  }

  public Change getChange() {
    return getControl().getChange();
  }

  public PatchSet getPatchSet() {
    return ps;
  }

  Account.Id getAccountId() {
    return getUser().getAccountId();
  }

  IdentifiedUser getUser() {
    return (IdentifiedUser) getControl().getCurrentUser();
  }
}

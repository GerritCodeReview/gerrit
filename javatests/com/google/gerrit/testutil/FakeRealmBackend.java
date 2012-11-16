/*
 * Copyright 2014 CollabNet, Inc. All rights reserved.
 * http://www.collab.net
 */

package com.google.gerrit.testutil;

import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.server.auth.AuthUser;
import com.google.gerrit.server.auth.AuthUser.UUID;
import com.google.gerrit.server.auth.RealmBackend;
import com.google.gerrit.server.auth.UserData;

public class FakeRealmBackend implements RealmBackend {

  @Override
  public boolean handles(UUID uuid) {
    return true;
  }

  @Override
  public UserData getUserData(AuthUser user) {
    return new UserData.Builder(user.getUsername())
        .setExternalId(AccountExternalId.SCHEME_GERRIT + user.getUUID().uuid())
        .build();
  }
}

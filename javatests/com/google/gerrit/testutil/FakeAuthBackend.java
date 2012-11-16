/*
 * Copyright 2014 CollabNet, Inc. All rights reserved.
 * http://www.collab.net
 */

package com.google.gerrit.testutil;

import com.google.gerrit.server.auth.AuthBackend;
import com.google.gerrit.server.auth.AuthException;
import com.google.gerrit.server.auth.AuthRequest;
import com.google.gerrit.server.auth.AuthUser;
import com.google.gerrit.server.auth.InvalidCredentialsException;
import com.google.gerrit.server.auth.MissingCredentialsException;
import com.google.gerrit.server.auth.UnknownUserException;
import com.google.gerrit.server.auth.UserNotAllowedException;

public class FakeAuthBackend implements AuthBackend {

  @Override
  public String getDomain() {
    return "fake";
  }

  @Override
  public AuthUser authenticate(AuthRequest req)
      throws MissingCredentialsException, InvalidCredentialsException, UnknownUserException,
          UserNotAllowedException, AuthException {
    String username = req.getUsername();
    return new AuthUser(AuthUser.UUID.create(getDomain(), username), username);
  }
}

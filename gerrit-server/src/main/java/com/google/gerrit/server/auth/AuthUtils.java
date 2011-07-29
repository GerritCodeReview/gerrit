package com.google.gerrit.server.auth;

import com.google.gerrit.reviewdb.AccountExternalId;

import java.util.Collection;

public class AuthUtils {
  public static String findUsername(String scheme, final Collection<AccountExternalId> ids) {
    for (final AccountExternalId i : ids) {
      if (i.isScheme(scheme)) {
        return i.getSchemeRest();
      }
    }
    return null;
  }
}

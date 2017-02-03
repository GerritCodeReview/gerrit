// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.client.account;

import com.google.gerrit.extensions.client.AuthType;

public final class ExternalIdScheme {
  /**
   * Scheme used for {@link AuthType#LDAP}, {@link AuthType#CLIENT_SSL_CERT_LDAP},
   * {@link AuthType#HTTP_LDAP}, and {@link AuthType#LDAP_BIND} usernames.
   * <p>
   * The name {@code gerrit:} was a very poor choice.
   */
  public static final String SCHEME_GERRIT = "gerrit:";

  /** Scheme used to represent only an email address. */
  public static final String SCHEME_MAILTO = "mailto:";

  /** Scheme for the username used to authenticate an account, e.g. over SSH. */
  public static final String SCHEME_USERNAME = "username:";

  public static boolean isScheme(String id, final String scheme) {
    return (id != null) && id.startsWith(scheme);
  }

  public static String getSchemeRest(String externalId) {
    String scheme = getScheme(externalId);
    return (scheme != null)
        ? externalId.substring(scheme.length() + 1)
        : null;
  }

  private static String getScheme(String externalId) {
    int c = externalId.indexOf(':');
    return (c > 0) ? externalId.substring(0, c) : null;
  }
}

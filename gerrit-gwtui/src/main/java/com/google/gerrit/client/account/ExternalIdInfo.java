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

import com.google.gerrit.client.auth.openid.OpenIdUtil;
import com.google.gerrit.common.auth.openid.OpenIdUrls;
import com.google.gerrit.extensions.client.AuthType;
import com.google.gwt.core.client.JavaScriptObject;

public class ExternalIdInfo extends JavaScriptObject implements Comparable<ExternalIdInfo> {
  /**
   * Scheme used for {@link AuthType#LDAP}, {@link AuthType#CLIENT_SSL_CERT_LDAP}, {@link
   * AuthType#HTTP_LDAP}, and {@link AuthType#LDAP_BIND} usernames.
   *
   * <p>The name {@code gerrit:} was a very poor choice.
   */
  private static final String SCHEME_GERRIT = "gerrit:";

  /** Scheme used to represent only an email address. */
  private static final String SCHEME_MAILTO = "mailto:";

  /** Scheme for the username used to authenticate an account, e.g. over SSH. */
  private static final String SCHEME_USERNAME = "username:";

  public final native String identity() /*-{ return this.identity; }-*/;

  public final native String emailAddress() /*-{ return this.email_address; }-*/;

  public final native boolean isTrusted() /*-{ return this['trusted'] ? true : false; }-*/;

  public final native boolean canDelete() /*-{ return this['can_delete'] ? true : false; }-*/;

  public final boolean isUsername() {
    return isScheme(SCHEME_USERNAME);
  }

  public final String describe() {
    if (isScheme(SCHEME_GERRIT)) {
      // A local user identity should just be itself.
      return getSchemeRest();
    } else if (isScheme(SCHEME_USERNAME)) {
      // A local user identity should just be itself.
      return getSchemeRest();
    } else if (isScheme(SCHEME_MAILTO)) {
      // Describe a mailto address as just its email address,
      // which is already shown in the email address field.
      return "";
    } else if (isScheme(OpenIdUrls.URL_LAUNCHPAD)) {
      return OpenIdUtil.C.nameLaunchpad();
    } else if (isScheme(OpenIdUrls.URL_YAHOO)) {
      return OpenIdUtil.C.nameYahoo();
    }

    return identity();
  }

  @Override
  public final int compareTo(ExternalIdInfo a) {
    return emailOf(this).compareTo(emailOf(a));
  }

  private boolean isScheme(String scheme) {
    return identity() != null && identity().startsWith(scheme);
  }

  private String getSchemeRest() {
    int colonIdx = identity().indexOf(':');
    String scheme = (colonIdx > 0) ? identity().substring(0, colonIdx) : null;
    return scheme != null ? identity().substring(scheme.length() + 1) : null;
  }

  private String emailOf(ExternalIdInfo a) {
    return a.emailAddress() != null ? a.emailAddress() : "";
  }

  protected ExternalIdInfo() {}
}

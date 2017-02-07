// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.extensions.client;

public enum AuthType {
  /** Login relies upon the <a href="http://openid.net/">OpenID standard</a> */
  OPENID,

  /**
   * Login relies upon the <a href="http://openid.net/">OpenID standard</a> in Single Sign On mode
   */
  OPENID_SSO,

  /**
   * Login relies upon the container/web server security.
   *
   * <p>The container or web server must populate an HTTP header with a unique name for the current
   * user. Gerrit will implicitly trust the value of this header to supply the unique identity.
   */
  HTTP,

  /**
   * Login relies upon the container/web server security, but also uses LDAP.
   *
   * <p>Like {@link #HTTP}, the container or web server must populate an HTTP header with a unique
   * name for the current user. Gerrit will implicitly trust the value of this header to supply the
   * unique identity.
   *
   * <p>In addition to trusting the HTTP headers, Gerrit will obtain basic user registration (name
   * and email) from LDAP, and some group memberships.
   */
  HTTP_LDAP,

  /**
   * Login via client SSL certificate.
   *
   * <p>This authentication type is actually kind of SSO. Gerrit will configure Jetty's SSL channel
   * to request client's SSL certificate. For this authentication to work a Gerrit administrator has
   * to import the root certificate of the trust chain used to issue the client's certificate into
   * the &lt;review-site&gt;/etc/keystore.
   *
   * <p>After the authentication is done Gerrit will obtain basic user registration (name and email)
   * from LDAP, and some group memberships. Therefore, the "_LDAP" suffix in the name of this
   * authentication type.
   */
  CLIENT_SSL_CERT_LDAP,

  /**
   * Login collects username and password through a web form, and binds to LDAP.
   *
   * <p>Unlike {@link #HTTP_LDAP}, Gerrit presents a sign-in dialog to the user and makes the
   * connection to the LDAP server on their behalf.
   */
  LDAP,

  /**
   * Login collects username and password through a web form, and binds to LDAP.
   *
   * <p>Unlike {@link #HTTP_LDAP}, Gerrit presents a sign-in dialog to the user and makes the
   * connection to the LDAP server on their behalf.
   *
   * <p>Unlike the more generic {@link #LDAP} mode, Gerrit can only query the directory via an
   * actual authenticated user account.
   */
  LDAP_BIND,

  /** Login is managed by additional, unspecified code. */
  CUSTOM_EXTENSION,

  /** Development mode to enable becoming anyone you want. */
  DEVELOPMENT_BECOME_ANY_ACCOUNT,

  /** Generic OAuth provider over HTTP. */
  OAUTH
}

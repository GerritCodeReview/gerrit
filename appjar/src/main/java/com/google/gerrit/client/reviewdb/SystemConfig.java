// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.reviewdb;

import com.google.gerrit.server.HostPageServlet;
import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.StringKey;

/** Global configuration needed to serve web requests. */
public final class SystemConfig {
  public static final class Key extends
      StringKey<com.google.gwtorm.client.Key<?>> {
    private static final String VALUE = "X";

    @Column(length = 1)
    protected String one = VALUE;

    public Key() {
    }

    @Override
    public String get() {
      return VALUE;
    }

    @Override
    protected void set(final String newValue) {
      assert get().equals(newValue);
    }
  }

  public static enum LoginType {
    /** Login relies upon the OpenID standard: {@link "http://openid.net/"} */
    OPENID,

    /**
     * Login relies upon the container/web server security.
     * <p>
     * The container or web server must populate an HTTP header with the some
     * user token. Gerrit will implicitly trust the value of this header to
     * supply the unique identity.
     */
    HTTP;
  }

  public static SystemConfig create() {
    final SystemConfig r = new SystemConfig();
    r.singleton = new SystemConfig.Key();
    r.maxSessionAge = 12 * 60 * 60 /* seconds */;
    return r;
  }

  @Column
  protected Key singleton;

  /** Private key to sign XSRF protection tokens. */
  @Column(length = 36)
  public transient String xsrfPrivateKey;

  /** Private key to sign account identification cookies. */
  @Column(length = 36)
  public transient String accountPrivateKey;

  /** Maximum web session age, in seconds. */
  @Column
  public transient int maxSessionAge;

  /**
   * Local filesystem location of header/footer/CSS configuration files
   * 
   * @see HostPageServlet
   */
  @Column(notNull = false)
  public transient String sitePath;

  /** Optional canonical URL for this application. */
  @Column(notNull = false)
  public String canonicalUrl;

  /** Optional URL of a gitweb installation to also view changes through. */
  @Column(notNull = false)
  public String gitwebUrl;

  /** Local filesystem loction all projects reside within. */
  @Column(notNull = false)
  public transient String gitBasePath;

  /** Type of login access used by this instance. */
  @Column(length = 16)
  protected String loginType;

  /** HTTP header to use for the user identity if loginType is HTTP. */
  @Column(length = 30, notNull = false)
  public transient String loginHttpHeader;

  /** Format to generate email address from a login names */
  @Column(length = 30, notNull = false)
  public transient String emailFormat;

  /** Is a verified {@link AccountAgreement} required to upload changes? */
  @Column
  public boolean useContributorAgreements;

  /** Local TCP port number the embedded SSHD server binds onto. */
  @Column
  public int sshdPort;

  /** Identity of the administration group; those with full access. */
  @Column
  public AccountGroup.Id adminGroupId;

  /** Identity of the anonymous group, which permits anyone. */
  @Column
  public AccountGroup.Id anonymousGroupId;

  /** Identity of the registered users group, which permits anyone. */
  @Column
  public AccountGroup.Id registeredGroupId;

  public LoginType getLoginType() {
    return loginType != null ? LoginType.valueOf(loginType) : null;
  }

  public void setLoginType(final LoginType t) {
    loginType = t.name();
  }

  protected SystemConfig() {
  }
}

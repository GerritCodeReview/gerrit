// Copyright (C) 2008 The Android Open Source Project
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
    private static final long serialVersionUID = 1L;

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

  /** Construct a new, unconfigured instance. */
  public static SystemConfig create() {
    final SystemConfig r = new SystemConfig();
    r.singleton = new SystemConfig.Key();
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

  /**
   * Optional URL of the anonymous git daemon for project access.
   * <p>
   * For example: <code>git://host/base/</code>
   */
  @Column(notNull = false)
  public String gitDaemonUrl;

  /** Local filesystem location all projects reside within. */
  @Column(notNull = false)
  public transient String gitBasePath;

  /** Name this Gerrit instance calls itself when it makes changes in Git. */
  @Column
  public String gerritGitName;

  /** Email this Gerrit instance calls itself when it makes changes in Git. */
  @Column(notNull = false)
  public String gerritGitEmail;

  /** Type of login access used by this instance. */
  @Column(length = 16)
  protected String loginType;

  /** HTTP header to use for the user identity if loginType is HTTP. */
  @Column(length = 30, notNull = false)
  public transient String loginHttpHeader;

  /** Format to generate email address from a login names */
  @Column(length = 30, notNull = false)
  public transient String emailFormat;

  /**
   * Can user accounts from Gerrit1 upgrade to use OpenID?
   * <p>
   * This setting should only be true if this server is an upgraded database
   * from Gerrit1, and if there are still outstanding accounts which need to be
   * upgraded to Gerrit2's OpenID authentication scheme. Any other system should
   * leave this setting false.
   */
  @Column
  public transient boolean allowGoogleAccountUpgrade;

  /** Is a verified {@link AccountAgreement} required to upload changes? */
  @Column
  public boolean useContributorAgreements;

  /** Local TCP port number the embedded SSHD server binds onto. */
  @Column
  public int sshdPort;

  /** Should Gerrit advertise 'repo download' for patch sets? */
  @Column
  public boolean useRepoDownload;

  /** Identity of the administration group; those with full access. */
  @Column
  public AccountGroup.Id adminGroupId;

  /** Identity of the anonymous group, which permits anyone. */
  @Column
  public AccountGroup.Id anonymousGroupId;

  /** Identity of the registered users group, which permits anyone. */
  @Column
  public AccountGroup.Id registeredGroupId;

  /** Optional URL of the contact information store. */
  @Column(notNull = false)
  public transient String contactStoreURL;

  /** APPSEC token to get into {@link #contactStoreURL}. */
  @Column(notNull = false)
  public transient String contactStoreAPPSEC;

  public LoginType getLoginType() {
    return loginType != null ? LoginType.valueOf(loginType) : null;
  }

  public void setLoginType(final LoginType t) {
    loginType = t.name();
  }

  protected SystemConfig() {
  }
}

// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.reviewdb;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.StringKey;

public final class ActiveSession {
  public static final class Key extends
      StringKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1, length = 60)
    protected String token;

    public Key(final String token) {
      this.token = token;
    }

    @Override
    public String get() {
      return token;
    }

    @Override
    protected void set(String newValue) {
      token = newValue;
    }
  }

  @Column(id = 1, name = Column.NONE)
  protected ActiveSession.Key key;

  @Column(id = 2)
  protected Account.Id accountId;

  @Column(id = 3)
  protected long refreshCookieAt;

  @Column(id = 4)
  protected boolean persistentCookie;

  @Column(id = 5)
  protected AccountExternalId.Key externalId;

  @Column(id = 6)
  protected String xsrfToken;

  public ActiveSession(final ActiveSession.Key k, final Account.Id accountId,
      final long refreshCookieAt, final boolean persistentCookie,
      final AccountExternalId.Key externalId, final String xsrfToken) {
    this.key = k;
    this.accountId = accountId;
    this.refreshCookieAt = refreshCookieAt;
    this.persistentCookie = persistentCookie;
    this.externalId = externalId;
    this.xsrfToken = xsrfToken;
  }

  public Account.Id getAccountId() {
    return accountId;
  }

  public void setAccountId(Account.Id accountId) {
    this.accountId = accountId;
  }

  public long getRefreshCookieAt() {
    return refreshCookieAt;
  }

  public void setRefreshCookieAt(long refreshCookieAt) {
    this.refreshCookieAt = refreshCookieAt;
  }

  public boolean isPersistentCookie() {
    return persistentCookie;
  }

  public void setPersistentCookie(boolean persistentCookie) {
    this.persistentCookie = persistentCookie;
  }

  public AccountExternalId.Key getExternalId() {
    return externalId;
  }

  public void setExternalId(AccountExternalId.Key externalId) {
    this.externalId = externalId;
  }

  public String getXsrfToken() {
    return xsrfToken;
  }

  public void setXsrfToken(String xsrfToken) {
    this.xsrfToken = xsrfToken;
  }

  public boolean needsCookieRefresh() {
    return refreshCookieAt <= System.currentTimeMillis();
  }
}

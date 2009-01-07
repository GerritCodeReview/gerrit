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

package com.google.gerrit.server;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.Common.CurrentAccountImpl;
import com.google.gwtjsonrpc.server.ActiveCall;
import com.google.gwtjsonrpc.server.ValidToken;
import com.google.gwtorm.client.OrmException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class GerritCall extends ActiveCall {
  static {
    Common.setCurrentAccountImpl(new CurrentAccountImpl() {
      public Account.Id getAccountId() {
        final GerritCall c = GerritJsonServlet.getCurrentCall();
        return c != null ? c.getAccountId() : null;
      }
    });
  }

  private final GerritServer server;
  private boolean accountRead;
  private ValidToken accountInfo;
  private Account.Id accountId;

  public GerritCall(final GerritServer gs, final HttpServletRequest i,
      final HttpServletResponse o) {
    super(i, o);
    server = gs;
  }

  @Override
  public void onFailure(final Throwable error) {
    if (error instanceof OrmException) {
      onInternalFailure(error);
    } else {
      super.onFailure(error);
    }
  }

  @Override
  public String getUser() {
    initAccount();
    return accountInfo != null ? accountInfo.getData() : null;
  }

  public Account.Id getAccountId() {
    initAccount();
    return accountId;
  }

  private void initAccount() {
    if (accountRead) {
      return;
    }

    accountRead = true;
    accountInfo = getCookie(Gerrit.ACCOUNT_COOKIE, server.getAccountToken());

    if (accountInfo == null) {
      if (getCookie(Gerrit.ACCOUNT_COOKIE) != null) {
        // The cookie is bogus, but it was sent. Send an expired cookie
        // back to clear it out of the browser's cookie store.
        //
        removeCookie(Gerrit.ACCOUNT_COOKIE);
      }
      return;
    }

    try {
      accountId = new Account.Id(Integer.parseInt(accountInfo.getData()));
    } catch (NumberFormatException e) {
      // Whoa, did we change our cookie format or something? This should
      // never happen on a valid acocunt token, but discard it anyway.
      //
      removeCookie(Gerrit.ACCOUNT_COOKIE);
      accountInfo = null;
      accountId = null;
      return;
    }

    if (accountInfo.needsRefresh()) {
      // The cookie is valid, but its getting stale. Update it with a
      // newer date so it doesn't expire on an active user.
      //
      final String idstr = String.valueOf(accountId.get());
      setCookie(Gerrit.ACCOUNT_COOKIE, idstr, server.getAccountToken());
    }
  }
}

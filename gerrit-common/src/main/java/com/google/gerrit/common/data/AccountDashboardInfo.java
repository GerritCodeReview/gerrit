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

package com.google.gerrit.common.data;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;

import java.util.ArrayList;
import java.util.List;

/** Summary information needed to display an account dashboard. */
public class AccountDashboardInfo {
  protected AccountInfoCache accounts;
  protected Account.Id owner;
  protected List<ChangeInfo> byOwner;
  protected List<ChangeInfo> forReview;
  protected List<ChangeInfo> closed;

  protected AccountDashboardInfo() {
  }

  public AccountDashboardInfo(final Account.Id forUser) {
    owner = forUser;
  }

  public AccountInfoCache getAccounts() {
    return accounts;
  }

  public void setAccounts(final AccountInfoCache ac) {
    accounts = ac;
  }

  public Account.Id getOwner() {
    return owner;
  }

  public List<ChangeInfo> getByOwner() {
    return byOwner;
  }

  public void setByOwner(List<ChangeInfo> c) {
    byOwner = c;
  }

  public List<ChangeInfo> getForReview() {
    return forReview;
  }

  public void setForReview(List<ChangeInfo> c) {
    // Although a change that is "Work In Progress" is officially "open",
    // we don't want to see those in our review list.
    final ArrayList<ChangeInfo> r = new ArrayList<ChangeInfo>();
    for (ChangeInfo ci: c) {
      if (ci.getStatus() != Change.Status.WORKINPROGRESS) {
        r.add(ci);
      }
    }

    forReview = r;
   }

  public List<ChangeInfo> getClosed() {
    return closed;
  }

  public void setClosed(List<ChangeInfo> c) {
    closed = c;
  }
}

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

package com.google.gerrit.client.data;

import com.google.gerrit.client.changes.MineStarredScreen;
import com.google.gerrit.client.reviewdb.Account;

import java.util.List;

/** Summary information needed for {@link MineStarredScreen}. */
public class MineStarredInfo {
  protected AccountInfoCache accounts;
  protected Account.Id owner;
  protected List<ChangeInfo> starred;

  protected MineStarredInfo() {
  }

  public MineStarredInfo(final Account.Id forUser) {
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

  public List<ChangeInfo> getStarred() {
    return starred;
  }

  public void setStarred(List<ChangeInfo> c) {
    starred = c;
  }
}

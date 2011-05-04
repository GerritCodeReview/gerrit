// Copyright (C) 2011 The Android Open Source Project
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

import com.google.gerrit.reviewdb.Account;

import java.util.List;

public class RemoteAccountDashboardInfo {
  private AccountInfoCache remoteAccounts;
  private Account.Id remoteOwnerId;
  private List<ChangeInfo> byOwner;
  private List<ChangeInfo> forReview;
  private List<ChangeInfo> closed;
  private String remoteUrl;

  protected RemoteAccountDashboardInfo() {
  }

  public RemoteAccountDashboardInfo(final String remoteUrl) {
    this.remoteUrl = remoteUrl;
  }

  public AccountInfoCache getRemoteAccounts() {
    return remoteAccounts;
  }

  public void setRemoteAccounts(final AccountInfoCache remoteAccounts) {
    this.remoteAccounts = remoteAccounts;
  }

  public Account.Id getRemoteOwnerId() {
    return remoteOwnerId;
  }

  public void setRemoteOwnerId(final Account.Id remoteOwnerId) {
    this.remoteOwnerId = remoteOwnerId;
  }

  public List<ChangeInfo> getByOwner() {
    return byOwner;
  }

  public void setByOwner(final List<ChangeInfo> c) {
    byOwner = c;
  }

  public List<ChangeInfo> getForReview() {
    return forReview;
  }

  public void setForReview(final List<ChangeInfo> c) {
    forReview = c;
  }

  public List<ChangeInfo> getClosed() {
    return closed;
  }

  public void setClosed(final List<ChangeInfo> c) {
    closed = c;
  }

  public String getRemoteUrl() {
    return remoteUrl;
  }
}

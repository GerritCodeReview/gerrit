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

package com.google.gerrit.client.data;

import com.google.gerrit.client.patches.PatchScreen;
import com.google.gerrit.client.reviewdb.Patch;

import java.util.List;

/** Detail necessary to display {@link PatchScreen}. */
public abstract class BasePatchDetail {
  protected AccountInfoCache accounts;
  protected Patch patch;
  protected List<Patch> history;

  protected BasePatchDetail() {
  }

  public BasePatchDetail(final Patch p, final AccountInfoCache aic) {
    patch = p;
    accounts = aic;
  }

  public AccountInfoCache getAccounts() {
    return accounts;
  }

  public Patch getPatch() {
    return patch;
  }

  public List<Patch> getHistory() {
    return history;
  }

  public void setHistory(final List<Patch> ids) {
    history = ids;
  }
}

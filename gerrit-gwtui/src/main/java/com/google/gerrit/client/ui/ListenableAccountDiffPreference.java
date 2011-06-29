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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.account.Util;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.reviewdb.AccountDiffPreference;
import com.google.gwtjsonrpc.client.VoidResult;

public class ListenableAccountDiffPreference
    extends ListenableValue<AccountDiffPreference> {

  public ListenableAccountDiffPreference() {
    reset();
  }

  public void save(final GerritCallback<VoidResult> cb) {
    if (Gerrit.isSignedIn()) {
      Util.ACCOUNT_SVC.changeDiffPreferences(get(),
          new GerritCallback<VoidResult>() {
        @Override
        public void onSuccess(VoidResult result) {
          Gerrit.setAccountDiffPreference(get());
          cb.onSuccess(result);
        }

        @Override
        public void onFailure(Throwable caught) {
          cb.onFailure(caught);
        }
      });
    }
  }

  public void reset() {
    if (Gerrit.isSignedIn() && Gerrit.getAccountDiffPreference() != null) {
      set(Gerrit.getAccountDiffPreference());
    } else {
      set(AccountDiffPreference.createDefault(null));
    }
  }
}

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

package com.google.gerrit.client.account;

import com.google.gerrit.client.Screen;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.rpc.AsyncCallback;

public class AccountSettings extends Screen {
  public AccountSettings() {
    super(Util.C.accountSettingsHeading());

    Util.ACCOUNT_SVC.myAccount(new AsyncCallback<Account>() {
      public void onFailure(Throwable caught) {
        GWT.log("myAccount failed", caught);
      }

      public void onSuccess(Account result) {
        GWT.log("yay, i am " + result.getPreferredEmail(), null);
        GWT.log("created on " + result.getRegisteredOn(), null);
      }
    });
  }
}

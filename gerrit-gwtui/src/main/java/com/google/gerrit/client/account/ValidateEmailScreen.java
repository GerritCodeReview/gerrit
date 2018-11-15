// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.VoidResult;
import com.google.gerrit.client.config.ConfigServerApi;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.AccountScreen;
import com.google.gerrit.common.PageLinks;

public class ValidateEmailScreen extends AccountScreen {
  private final String magicToken;

  public ValidateEmailScreen(String magicToken) {
    this.magicToken = magicToken;
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setPageTitle(Util.C.settingsHeading());
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    ConfigServerApi.confirmEmail(
        magicToken,
        new ScreenLoadCallback<VoidResult>(this) {
          @Override
          protected void preDisplay(VoidResult result) {}

          @Override
          protected void postDisplay() {
            Gerrit.display(PageLinks.SETTINGS_CONTACT);
          }
        });
  }
}

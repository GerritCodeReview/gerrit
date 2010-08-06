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

package com.google.gerrit.client;

import com.google.gerrit.common.auth.SignInMode;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.user.client.AutoCenterDialogBox;

/** Prompts the user to sign in to their account. */
public abstract class SignInDialog extends AutoCenterDialogBox {
  protected final SignInMode mode;
  protected final String token;

  /**
   * Create a new dialog to handle user sign in.
   *
   * @param signInMode type of mode the login will perform.
   * @param token the token to jump to after sign-in is complete.
   */
  protected SignInDialog(final SignInMode signInMode, final String token) {
    super(/* auto hide */true, /* modal */true);
    setGlassEnabled(true);

    this.mode = signInMode;
    this.token = token;

    switch (signInMode) {
      case LINK_IDENTIY:
        setText(Gerrit.C.linkIdentityDialogTitle());
        break;
      case REGISTER:
        setText(Gerrit.C.registerDialogTitle());
        break;
      default:
        setText(Gerrit.C.signInDialogTitle());
        break;
    }
  }

  @Override
  public void show() {
    super.show();
    GlobalKey.dialog(this);
  }
}

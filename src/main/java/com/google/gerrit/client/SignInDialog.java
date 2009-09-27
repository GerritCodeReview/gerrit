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

import com.google.gwtexpui.user.client.AutoCenterDialogBox;

/** Prompts the user to sign in to their account. */
public class SignInDialog extends AutoCenterDialogBox {
  public static enum Mode {
    SIGN_IN, LINK_IDENTIY, REGISTER;
  }

  protected final SignInDialog.Mode mode;

  /**
   * Create a new dialog to handle user sign in.
   *
   * @param signInMode type of mode the login will perform.
   */
  protected SignInDialog(final Mode signInMode) {
    super(/* auto hide */true, /* modal */true);
    mode = signInMode;

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
}

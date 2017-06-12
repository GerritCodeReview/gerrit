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

package com.google.gerrit.client.change;

import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.changes.Util;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import com.google.gwtjsonrpc.client.RemoteJsonException;

class SubmitFailureDialog extends ErrorDialog {
  static boolean isConflict(Throwable err) {
    return err instanceof RemoteJsonException && 409 == ((RemoteJsonException) err).getCode();
  }

  SubmitFailureDialog(String msg) {
    super(new SafeHtmlBuilder().append(msg.trim()).wikify());
    setText(Util.C.submitFailed());
  }
}

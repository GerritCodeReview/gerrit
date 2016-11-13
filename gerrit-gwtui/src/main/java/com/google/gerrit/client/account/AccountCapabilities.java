// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.client.rpc.RestApi;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.rpc.AsyncCallback;

/** Capabilities the caller has from {@code /accounts/self/capabilities}. */
public class AccountCapabilities extends JavaScriptObject {
  public static void all(AsyncCallback<AccountCapabilities> cb, String... filter) {
    new RestApi("/accounts/self/capabilities").addParameter("q", filter).get(cb);
  }

  protected AccountCapabilities() {}

  public final native boolean canPerform(String name)/*-{ return this[name] ? true : false; }-*/ ;
}

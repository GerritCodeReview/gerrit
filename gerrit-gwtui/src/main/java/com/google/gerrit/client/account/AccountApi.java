// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.client.VoidResult;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * A collection of static methods which work on the Gerrit REST API for specific
 * accounts.
 */
public class AccountApi {
  /** Register a new email address */
  public static void registerEmail(String account, String email,
      AsyncCallback<NativeString> cb) {
    JavaScriptObject in = JavaScriptObject.createObject();
    new RestApi("/accounts/").id(account).view("emails").id(email)
        .ifNoneMatch().put(in, cb);
  }

  /** Generate a new HTTP password */
  public static void generateHttpPassword(String account,
      AsyncCallback<NativeString> cb) {
    HttpPasswordInput in = HttpPasswordInput.create();
    in.generate(true);
    new RestApi("/accounts/").id(account).view("password.http").put(in, cb);
  }

  /** Clear HTTP password */
  public static void clearHttpPassword(String account,
      AsyncCallback<VoidResult> cb) {
    new RestApi("/accounts/").id(account).view("password.http").delete(cb);
  }

  private static class HttpPasswordInput extends JavaScriptObject {
    final native void generate(boolean g) /*-{ if(g)this.generate=g; }-*/;

    static HttpPasswordInput create() {
      return (HttpPasswordInput) createObject();
    }

    protected HttpPasswordInput() {
    }
  }
}

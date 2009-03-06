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

package com.google.gerrit.client.account;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.CallbackHandle;
import com.google.gwtjsonrpc.client.RemoteJsonService;

/** Works with the LoginServlet to connect to accounts. */
public interface LoginService extends RemoteJsonService {
  /**
   * Create a callback for LoginServlet to call post sign in.
   * <p>
   * The LoginResult.getStatus() is {@link SignInResult.Status#CANCEL} is null
   * if the sign in was aborted by the user (or failed too many times).
   */
  CallbackHandle<SignInResult> signIn(AsyncCallback<SignInResult> c);
}

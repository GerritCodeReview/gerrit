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

package com.google.gerrit.client.auth.openid;

import com.google.gerrit.common.auth.openid.OpenIdService;
import com.google.gwt.core.client.GWT;
import com.google.gwtjsonrpc.client.JsonUtil;

public class OpenIdUtil {
  public static final LoginConstants C;
  public static final LoginMessages M;
  public static final OpenIdService SVC;

  static {
    C = GWT.create(LoginConstants.class);
    M = GWT.create(LoginMessages.class);
    SVC = GWT.create(OpenIdService.class);
    JsonUtil.bind(SVC, "rpc/OpenIdService");
  }
}

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

import com.google.gerrit.common.data.AccountSecurity;
import com.google.gerrit.common.data.AccountService;
import com.google.gerrit.common.data.ProjectAdminService;
import com.google.gwt.core.client.GWT;
import com.google.gwtjsonrpc.client.JsonUtil;

public class Util {
  public static final AccountConstants C = GWT.create(AccountConstants.class);
  public static final AccountMessages M = GWT.create(AccountMessages.class);
  public static final AccountService ACCOUNT_SVC;
  public static final AccountSecurity ACCOUNT_SEC;
  public static final ProjectAdminService PROJECT_SVC;

  static {
    ACCOUNT_SVC = GWT.create(AccountService.class);
    JsonUtil.bind(ACCOUNT_SVC, "rpc/AccountService");

    ACCOUNT_SEC = GWT.create(AccountSecurity.class);
    JsonUtil.bind(ACCOUNT_SEC, "rpc/AccountSecurity");

    PROJECT_SVC = GWT.create(ProjectAdminService.class);
    JsonUtil.bind(PROJECT_SVC, "rpc/ProjectAdminService");
  }
}

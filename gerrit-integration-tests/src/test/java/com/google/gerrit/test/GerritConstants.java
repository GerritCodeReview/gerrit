// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.test;

import com.google.gerrit.client.account.AccountConstants;
import com.google.gerrit.client.changes.ChangeConstants;
import com.google.gerrit.client.changes.ChangeMessages;
import com.google.gerrit.client.auth.userpass.UserPassConstants;
import com.google.gerrit.test.util.PropertyAccessor;

public class GerritConstants {

  public final static AccountConstants ACCOUNT_CONSTANTS = PropertyAccessor
      .create(AccountConstants.class);
  public final static ChangeConstants CHANGE_CONSTANTS = PropertyAccessor
      .create(ChangeConstants.class);
  public final static ChangeMessages CHANGE_MESSAGES = PropertyAccessor
      .create(ChangeMessages.class);
  public final static com.google.gerrit.client.GerritConstants GERRIT_CONSTANTS =
      PropertyAccessor.create(com.google.gerrit.client.GerritConstants.class);
  public final static UserPassConstants USER_PASS_CONSTANTS = PropertyAccessor
      .create(UserPassConstants.class);
}

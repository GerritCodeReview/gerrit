// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.index.account;

import com.google.gerrit.common.data.AccountInfo;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.FieldType;

import java.util.ArrayList;

public class AccountField {

  /** Account ID */
  public static final FieldDef<AccountInfo, Integer> ID =
      new FieldDef.Single<AccountInfo, Integer>("id",
          FieldType.INTEGER, true) {
       @Override
       public Integer get(AccountInfo input, FillArgs args) {
         return input.getId().get();
       }
    };

  /** Account display name */
  public static final FieldDef<AccountInfo, Iterable<String>> NAME =
      new FieldDef.Repeatable<AccountInfo, String>(
          "displayname", FieldType.PREFIX, false) {
        @Override
        public Iterable<String> get(AccountInfo input, FillArgs args) {
          ArrayList<String> result = new ArrayList<String>();
          for (String part : input.getFullName().split("[\\w,]+")) {
            result.add(part);
          }
          return result;
        }
      };

  /** Account preferred email address */
  public static final FieldDef<AccountInfo, String> EMAIL =
      new FieldDef.Single<AccountInfo, String>(
          "email", FieldType.PREFIX, false) {
        @Override
        public String get(AccountInfo input, FillArgs args) {
          return input.getPreferredEmail();
        }
      };

}
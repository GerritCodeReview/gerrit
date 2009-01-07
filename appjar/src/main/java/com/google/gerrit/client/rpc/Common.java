// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.rpc;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gwt.core.client.GWT;

public class Common {
  public static final RpcConstants C;
  private static CurrentAccountImpl caImpl;

  static {
    if (GWT.isClient()) {
      C = GWT.create(RpcConstants.class);
      caImpl = new CurrentAccountImpl() {
        public Account.Id getAccountId() {
          final Account a = Gerrit.getUserAccount();
          return a != null ? a.getId() : null;
        }
      };
    } else {
      C = null;
    }
  }

  /** Get the unique id for this account; null if there is no account. */
  public static Account.Id getAccountId() {
    return caImpl.getAccountId();
  }

  public static void setCurrentAccountImpl(final CurrentAccountImpl i) {
    caImpl = i;
  }

  public interface CurrentAccountImpl {
    /** Get the unique id for this account; null if there is no account. */
    public Account.Id getAccountId();
  }
}

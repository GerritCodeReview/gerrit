// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.account;

import com.google.gerrit.entities.Account;

public interface ServiceUserClassifier {
  /**
   * Name of the Service Users group used by this class to determine whether an account is a service
   * user; if an account is a part of this group, that account is considered a service user.
   */
  public static final String SERVICE_USERS = "Service Users";

  /** Returns {@code true} if the given user is considered a {@code Service User} user. */
  boolean isServiceUser(Account.Id user);

  /** An instance that can be used for testing and will consider no user to be a Service User. */
  class NoOp implements ServiceUserClassifier {
    @Override
    public boolean isServiceUser(Account.Id user) {
      return false;
    }
  }
}

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

package com.google.gerrit.httpd;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Project;

class AdvertisedObjectsCacheKey {
  private final Account.Id account;
  private final Project.NameKey project;

  AdvertisedObjectsCacheKey(Account.Id account, Project.NameKey project) {
    this.account = account;
    this.project = project;
  }

  @Override
  public int hashCode() {
    return account.hashCode();
  }

  public boolean equals(Object other) {
    if (other instanceof AdvertisedObjectsCacheKey) {
      AdvertisedObjectsCacheKey o = (AdvertisedObjectsCacheKey) other;
      return account.equals(o.account) && project.equals(o.project);
    }
    return false;
  }
}

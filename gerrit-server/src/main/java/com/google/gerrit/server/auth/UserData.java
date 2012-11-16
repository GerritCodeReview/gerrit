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

package com.google.gerrit.server.auth;

import com.google.common.collect.Lists;

import java.util.List;

public class UserData {

  public static class Builder {
    private final UserData userData;

    public Builder(String username) {
      userData = new UserData();
      userData.username = username;
    }

    public Builder setDisplayName(String displayName) {
      userData.displayName = displayName;
      return this;
    }

    public Builder addEmailAddress(String emailAddress) {
      userData.emailAddress.add(emailAddress);
      return this;
    }

    public Builder setExternalId(String externalId) {
      userData.externalId = externalId;
      return this;
    }

    public UserData build() {
      return userData;
    }
  }

  private String username;
  private String displayName;
  private String externalId;
  private List<String> emailAddress = Lists.newArrayList();

  protected UserData() {
    // hide default constructor
  }

  public String getUsername() {
    return username;
  }

  public String getDisplayName() {
    return displayName;
  }

  public List<String> getEmailAddress() {
    return emailAddress;
  }

  public String getExteranalId() {
    return externalId;
  }
}

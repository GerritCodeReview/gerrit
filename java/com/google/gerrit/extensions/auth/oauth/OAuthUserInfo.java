// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.extensions.auth.oauth;

public class OAuthUserInfo {

  private final String externalId;
  private final String userName;
  private final String emailAddress;
  private final String displayName;
  private final String claimedIdentity;

  public OAuthUserInfo(
      String externalId,
      String userName,
      String emailAddress,
      String displayName,
      String claimedIdentity) {
    this.externalId = externalId;
    this.userName = userName;
    this.emailAddress = emailAddress;
    this.displayName = displayName;
    this.claimedIdentity = claimedIdentity;
  }

  public String getExternalId() {
    return externalId;
  }

  public String getUserName() {
    return userName;
  }

  public String getEmailAddress() {
    return emailAddress;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getClaimedIdentity() {
    return claimedIdentity;
  }
}

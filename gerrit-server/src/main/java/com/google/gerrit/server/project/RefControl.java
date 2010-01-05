// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.server.CurrentUser;

public class RefControl {

  private final CurrentUser user;
  private final String refName;

  public RefControl (final CurrentUser user, final String refName) {
    this.user = user;
    this.refName = refName;
    System.err.println("refName:" + refName);
    System.err.println("User:" + user.toString());
  }

  public boolean canUpload() {
    return true;
  }

  public boolean canCreate() {
    return true;
  }

  public boolean canDelete() {
    return true;
  }
}

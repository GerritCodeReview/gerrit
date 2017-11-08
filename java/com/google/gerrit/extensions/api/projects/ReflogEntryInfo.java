// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.extensions.api.projects;

import com.google.gerrit.extensions.common.GitPerson;

public class ReflogEntryInfo {
  public String oldId;
  public String newId;
  public GitPerson who;
  public String comment;

  public ReflogEntryInfo(String oldId, String newId, GitPerson who, String comment) {
    this.oldId = oldId;
    this.newId = newId;
    this.who = who;
    this.comment = comment;
  }
}

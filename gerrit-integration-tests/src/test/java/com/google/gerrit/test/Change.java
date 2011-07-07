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

public class Change {

  public final String abbreviatedChangeId;
  public final String subject;
  public final String owner;
  public final String projectName;
  public final String branch;
  public final String updated;

  public Change(final String abbreviatedChangeId, final String subject,
      final String owner, final String projectName, final String branch,
      final String updated) {
    this.abbreviatedChangeId = abbreviatedChangeId;
    this.subject = subject;
    this.owner = owner;
    this.projectName = projectName;
    this.branch = branch;
    this.updated = updated;
  }
}

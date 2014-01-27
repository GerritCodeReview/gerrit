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

package com.google.gerrit.extensions.api.changes;

import com.google.gerrit.extensions.common.ChangeStatus;

import java.sql.Timestamp;

// TODO(davido): don't map ChangeInfo to ChangeDescription.
// Solve Account.Id dependency from reviewdb (used in AccountInfo)
// in the first place and use the same ChangeInfo object tree in
// server and in extension API
public class ChangeDescription {
  public String id;
  public String project;
  public String branch;
  public String topic;
  public String changeId;
  public String subject;
  public ChangeStatus status;
  public Timestamp created;
  public Timestamp updated;
  public Boolean starred;
  public Boolean reviewed;
  public Boolean mergeable;
  public Integer insertions;
  public Integer deletions;
}

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

package com.google.gerrit.extensions.common;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.Map;

public class ChangeInfo {
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
  public AccountInfo owner;
  public String currentRevision;
  public Map<String, ActionInfo> actions;
  public Map<String, LabelInfo> labels;
  public Collection<ChangeMessageInfo> messages;
  public Map<String, RevisionInfo> revisions;
  public int _number;
}

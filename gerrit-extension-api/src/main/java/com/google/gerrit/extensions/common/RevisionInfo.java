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

import com.google.gerrit.extensions.client.ChangeKind;
import java.sql.Timestamp;
import java.util.Map;

public class RevisionInfo {
  public transient boolean isCurrent;
  public Boolean draft;
  public ChangeKind kind;
  public int _number;
  public Timestamp created;
  public AccountInfo uploader;
  public String ref;
  public Map<String, FetchInfo> fetch;
  public CommitInfo commit;
  public Map<String, FileInfo> files;
  public Map<String, ActionInfo> actions;
  public String commitWithFooters;
  public PushCertificateInfo pushCertificate;
}

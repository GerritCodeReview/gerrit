// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.reviewdb.client;

import java.util.List;

/** Additional data about a {@link PatchSet} not normally loaded. */
public final class PatchSetInfo {
  public static class ParentInfo {
    public RevId id;
    public String shortMessage;

    public ParentInfo(RevId id, String shortMessage) {
      this.id = id;
      this.shortMessage = shortMessage;
    }

    protected ParentInfo() {}
  }

  protected PatchSet.Id key;

  /** First line of {@link #message}. */
  protected String subject;

  /** The complete description of the change the patch set introduces. */
  protected String message;

  /** Identity of who wrote the patch set. May differ from {@link #committer}. */
  protected UserIdentity author;

  /** Identity of who committed the patch set to the VCS. */
  protected UserIdentity committer;

  /** List of parents of the patch set. */
  protected List<ParentInfo> parents;

  /** SHA-1 of commit */
  protected String revId;

  /** Optional user-supplied description for the patch set. */
  protected String description;

  protected PatchSetInfo() {}

  public PatchSetInfo(PatchSet.Id k) {
    key = k;
  }

  public PatchSet.Id getKey() {
    return key;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String s) {
    if (s != null && s.length() > 255) {
      subject = s.substring(0, 255);
    } else {
      subject = s;
    }
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String m) {
    message = m;
  }

  public UserIdentity getAuthor() {
    return author;
  }

  public void setAuthor(UserIdentity u) {
    author = u;
  }

  public UserIdentity getCommitter() {
    return committer;
  }

  public void setCommitter(UserIdentity u) {
    committer = u;
  }

  public void setParents(List<ParentInfo> p) {
    parents = p;
  }

  public List<ParentInfo> getParents() {
    return parents;
  }

  public void setRevId(String s) {
    revId = s;
  }

  public String getRevId() {
    return revId;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }
}

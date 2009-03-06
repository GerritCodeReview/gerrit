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

package com.google.gerrit.client.reviewdb;

import com.google.gwtorm.client.Column;

/**
 * Additional data about a {@link PatchSet} not normally loaded.
 * <p>
 * This is stored out of band from the PatchSet itself, to reduce the size of
 * each PatchSet record.
 */
public final class PatchSetInfo {
  @Column(name = Column.NONE)
  protected PatchSet.Id key;

  /** First line of {@link #message}. */
  @Column(notNull = false)
  protected String subject;

  /** The complete description of the change the patch set introduces. */
  @Column(notNull = false, length = Integer.MAX_VALUE)
  protected String message;

  /** Identity of who wrote the patch set. May differ from {@link #committer}. */
  @Column(notNull = false)
  protected UserIdentity author;

  /** Identity of who committed the patch set to the VCS. */
  @Column(notNull = false)
  protected UserIdentity committer;

  protected PatchSetInfo() {
  }

  public PatchSetInfo(final PatchSet.Id k) {
    key = k;
  }

  public PatchSet.Id getKey() {
    return key;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(final String s) {
    if (s != null && s.length() > 255) {
      subject = s.substring(0, 255);
    } else {
      subject = s;
    }
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(final String m) {
    message = m;
  }

  public UserIdentity getAuthor() {
    return author;
  }

  public void setAuthor(final UserIdentity u) {
    author = u;
  }

  public UserIdentity getCommitter() {
    return committer;
  }

  public void setCommitter(final UserIdentity u) {
    committer = u;
  }
}

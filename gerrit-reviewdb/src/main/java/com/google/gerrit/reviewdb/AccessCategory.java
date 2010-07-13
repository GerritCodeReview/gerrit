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

package com.google.gerrit.reviewdb;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.Key;
import com.google.gwtorm.client.StringKey;

/**
 * Types of permissions that can be associated with a {@link Change} or that
 * define rights an user can have.
 */
public final class AccessCategory {

  public static final AccessCategory.Id SUBMIT =
      new AccessCategory.Id("Submit");

  public static final AccessCategory.Id NO_ACCESS =
      new AccessCategory.Id("Read-1");
  public static final AccessCategory.Id READ_ACCESS =
      new AccessCategory.Id("Read+1");
  public static final AccessCategory.Id UPLOAD_PERMISSION =
      new AccessCategory.Id("Read+2");

  public static final AccessCategory.Id OWN = new AccessCategory.Id("Own");

  public static final AccessCategory.Id PUSH_TAG_SIGNED =
      new AccessCategory.Id("PushTag+1");
  public static final AccessCategory.Id PUSH_TAG_ANNOTATED =
      new AccessCategory.Id("PushTag+2");

  public static final AccessCategory.Id PUSH_HEAD_UPDATE =
      new AccessCategory.Id("PushBranch+1");
  public static final AccessCategory.Id PUSH_HEAD_CREATE =
      new AccessCategory.Id("PushBranch+2");
  public static final AccessCategory.Id PUSH_HEAD_REPLACE =
      new AccessCategory.Id("PushBranch+3");

  public static final AccessCategory.Id FORGE_AUTHOR =
      new AccessCategory.Id("ForgeIdentity+1");
  public static final AccessCategory.Id FORGE_COMMITTER =
      new AccessCategory.Id("ForgeIdentity+2");
  public static final AccessCategory.Id FORGE_SERVER =
      new AccessCategory.Id("ForgeIdentity+3");

  public static class Id extends StringKey<Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1, length = 50)
    protected String id;

    protected Id() {
    }

    public Id(final String a) {
      id = a;
    }

    @Override
    public String get() {
      return id;
    }

    @Override
    protected void set(String newValue) {
      id = newValue;
    }
  }

  /** Internal short unique identifier for this category. */
  @Column(id = 1, length = 50)
  protected Id accessId;

  /** Unique name for this category, shown in the web interface to users. */
  @Column(id = 2, length = 50)
  protected String description;

  protected AccessCategory() {
  }

  public AccessCategory(final AccessCategory.Id id, final String description) {
    this.accessId = id;
    this.description = description;
  }

  public AccessCategory.Id getId() {
    return accessId;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(final String n) {
    description = n;
  }
}

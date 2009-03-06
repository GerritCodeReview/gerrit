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
import com.google.gwtorm.client.IntKey;
import com.google.gwtorm.client.StringKey;

/** Projects match a source code repository managed by Gerrit */
public final class Project {
  /** Project name key */
  public static class NameKey extends
      StringKey<com.google.gwtorm.client.Key<?>> {
    @Column
    protected String name;

    protected NameKey() {
    }

    public NameKey(final String n) {
      name = n;
    }

    @Override
    public String get() {
      return name;
    }

    @Override
    protected void set(String newValue) {
      name = newValue;
    }

    /** Parse a Project.NameKey out of a string representation. */
    public static NameKey parse(final String str) {
      final NameKey r = new NameKey();
      r.fromString(str);
      return r;
    }
  }

  /** Synthetic key to link to within the database */
  public static class Id extends IntKey<com.google.gwtorm.client.Key<?>> {
    @Column
    protected int id;

    protected Id() {
    }

    public Id(final int id) {
      this.id = id;
    }

    @Override
    public int get() {
      return id;
    }

    @Override
    protected void set(int newValue) {
      id = newValue;
    }

    /** Parse a Project.Id out of a string representation. */
    public static Id parse(final String str) {
      final Id r = new Id();
      r.fromString(str);
      return r;
    }
  }

  @Column
  protected NameKey name;

  @Column
  protected Id projectId;

  @Column(length = Integer.MAX_VALUE, notNull = false)
  protected String description;

  @Column
  protected AccountGroup.Id ownerGroupId;

  @Column
  protected boolean useContributorAgreements;

  protected Project() {
  }

  public Project(final Project.NameKey newName, final Project.Id newId) {
    name = newName;
    projectId = newId;
    useContributorAgreements = true;
  }

  public Project.Id getId() {
    return projectId;
  }

  public Project.NameKey getNameKey() {
    return name;
  }

  public String getName() {
    return name.get();
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(final String d) {
    description = d;
  }

  public AccountGroup.Id getOwnerGroupId() {
    return ownerGroupId;
  }

  public void setOwnerGroupId(final AccountGroup.Id id) {
    ownerGroupId = id;
  }

  public boolean isUseContributorAgreements() {
    return useContributorAgreements;
  }

  public void setUseContributorAgreements(final boolean u) {
    useContributorAgreements = u;
  }
}

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
import com.google.gwtorm.client.IntKey;
import com.google.gwtorm.client.StringKey;

/** Projects match a source code repository managed by Gerrit */
public final class Project {
  /** Project name key */
  public static class NameKey extends
      StringKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

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
    private static final long serialVersionUID = 1L;

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
  }

  public static enum SubmitType {
    FAST_FORWARD_ONLY('F'),

    MERGE_IF_NECESSARY('M'),

    MERGE_ALWAYS('A'),

    CHERRY_PICK('C');

    private final char code;

    private SubmitType(final char c) {
      code = c;
    }

    public char getCode() {
      return code;
    }

    public static SubmitType forCode(final char c) {
      for (final SubmitType s : SubmitType.values()) {
        if (s.code == c) {
          return s;
        }
      }
      return null;
    }
  }

  @Column
  protected NameKey name;

  @Column
  protected Id projectId;

  @Column(length = Integer.MAX_VALUE, notNull = false)
  protected String description;

  @Column
  protected boolean useContributorAgreements;

  @Column
  protected boolean useSignedOffBy;

  @Column
  protected char submitType;

  protected Project() {
  }

  public Project(final Project.NameKey newName, final Project.Id newId) {
    name = newName;
    projectId = newId;
    useContributorAgreements = true;
    setSubmitType(SubmitType.MERGE_IF_NECESSARY);
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

  public boolean isUseContributorAgreements() {
    return useContributorAgreements;
  }

  public void setUseContributorAgreements(final boolean u) {
    useContributorAgreements = u;
  }

  public boolean isUseSignedOffBy() {
    return useSignedOffBy;
  }

  public void setUseSignedOffBy(final boolean sbo) {
    useSignedOffBy = sbo;
  }

  public SubmitType getSubmitType() {
    return SubmitType.forCode(submitType);
  }

  public void setSubmitType(final SubmitType type) {
    submitType = type.getCode();
  }

  public void copySettingsFrom(final Project update) {
    description = update.description;
    useContributorAgreements = update.useContributorAgreements;
    useSignedOffBy = update.useSignedOffBy;
    submitType = update.submitType;
  }
}

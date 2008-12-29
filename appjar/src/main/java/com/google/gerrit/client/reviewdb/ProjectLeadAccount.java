// Copyright 2008 Google Inc.
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
import com.google.gwtorm.client.CompoundKey;

/** Single {@link Account} as owner/manager of a project. */
public final class ProjectLeadAccount {
  public static class Key extends CompoundKey<Project.NameKey> {
    @Column
    protected Project.NameKey projectName;

    @Column
    protected Account.Id accountId;

    protected Key() {
      projectName = new Project.NameKey();
      accountId = new Account.Id();
    }

    public Key(final Project.NameKey p, final Account.Id a) {
      projectName = p;
      accountId = a;
    }

    @Override
    public Project.NameKey getParentKey() {
      return projectName;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {accountId};
    }
  }

  @Column(name = Column.NONE)
  protected Key key;

  protected ProjectLeadAccount() {
  }

  public ProjectLeadAccount(final ProjectLeadAccount.Key k) {
    key = k;
  }
}

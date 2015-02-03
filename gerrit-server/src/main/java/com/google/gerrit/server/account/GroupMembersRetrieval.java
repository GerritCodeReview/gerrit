// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.account;

import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.NoSuchProjectException;

import java.util.Set;

/**
 * extension point for providing group members by plugins
 */
@ExtensionPoint
public interface GroupMembersRetrieval {
  boolean handles(AccountGroup.UUID group);

  Set<Account> get(AccountGroup.UUID group, Project.NameKey project)
      throws NoSuchGroupException, NoSuchProjectException;
}

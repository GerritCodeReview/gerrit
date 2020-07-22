// Copyright (C) 2020 The Android Open Source Project
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

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.group.db.Groups;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * An implementation of {@link RobotClassifier} that will consider a user to be a robot if they are
 * a member in the {@code Non-Interactive Users} group.
 */
@Singleton
public class NonInteractiveUserGroupRobotClassifier implements RobotClassifier {
  public static Module module() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        bind(RobotClassifier.class)
            .to(NonInteractiveUserGroupRobotClassifier.class)
            .in(Scopes.SINGLETON);
      }
    };
  }

  private final InternalGroupBackend internalGroupBackend;
  private final Groups groupsCollection;

  @Inject
  NonInteractiveUserGroupRobotClassifier(
      InternalGroupBackend internalGroupBackend, Groups groupsCollection) {
    this.internalGroupBackend = internalGroupBackend;
    this.groupsCollection = groupsCollection;
  }

  @Override
  public boolean isRobot(Account.Id user) {
    // TODO(hiesel, brohlfs, paiking): This is just an interim solution until we have figured out a
    // plan
    // Discussion is at: https://gerrit-review.googlesource.com/c/gerrit/+/274854
    Stream<GroupReference> groupReferences;
    try {
      groupReferences = groupsCollection.getAllGroupReferences();
    } catch (IOException | ConfigInvalidException e) {
      throw new StorageException(e);
    }
    Optional<AccountGroup.UUID> maybeUUID =
        groupReferences
            .filter(g -> g.getName().equals("Non-Interactive Users"))
            .map(GroupReference::getUUID)
            .findAny();
    if (maybeUUID.isPresent() && internalGroupBackend.handles(maybeUUID.get())) {
      return internalGroupBackend.get(maybeUUID.get()).getMembers().stream()
          .anyMatch(member -> user.equals(member));
    }
    return false;
  }
}

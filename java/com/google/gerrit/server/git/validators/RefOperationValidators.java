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
package com.google.gerrit.server.git.validators;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.events.RefReceivedEvent;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.transport.ReceiveCommand;

public class RefOperationValidators {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    RefOperationValidators create(Project project, IdentifiedUser user, ReceiveCommand cmd);
  }

  public static ReceiveCommand getCommand(RefUpdate update, ReceiveCommand.Type type) {
    return new ReceiveCommand(
        update.getExpectedOldObjectId(), update.getNewObjectId(), update.getName(), type);
  }

  private final PermissionBackend.WithUser perm;
  private final AllUsersName allUsersName;
  private final PluginSetContext<RefOperationValidationListener> refOperationValidationListeners;
  private final RefReceivedEvent event;

  @Inject
  RefOperationValidators(
      PermissionBackend permissionBackend,
      AllUsersName allUsersName,
      PluginSetContext<RefOperationValidationListener> refOperationValidationListeners,
      @Assisted Project project,
      @Assisted IdentifiedUser user,
      @Assisted ReceiveCommand cmd) {
    this.perm = permissionBackend.user(user);
    this.allUsersName = allUsersName;
    this.refOperationValidationListeners = refOperationValidationListeners;
    event = new RefReceivedEvent();
    event.command = cmd;
    event.project = project;
    event.user = user;
  }

  public List<ValidationMessage> validateForRefOperation() throws RefOperationValidationException {
    List<ValidationMessage> messages = new ArrayList<>();
    boolean withException = false;
    try {
      messages.addAll(
          new DisallowCreationAndDeletionOfGerritMaintainedBranches(perm, allUsersName)
              .onRefOperation(event));
      refOperationValidationListeners.runEach(
          l -> l.onRefOperation(event), ValidationException.class);
    } catch (ValidationException e) {
      messages.add(new ValidationMessage(e.getMessage(), true));
      withException = true;
    }

    if (withException) {
      throwException(messages, event);
    }

    return messages;
  }

  private void throwException(List<ValidationMessage> messages, RefReceivedEvent event)
      throws RefOperationValidationException {
    String header =
        String.format(
            "Ref \"%s\" %S in project %s validation failed",
            event.command.getRefName(), event.command.getType(), event.project.getName());
    logger.atSevere().log(header);
    throw new RefOperationValidationException(
        header, messages.stream().filter(ValidationMessage::isError).collect(toImmutableList()));
  }

  private static class DisallowCreationAndDeletionOfGerritMaintainedBranches
      implements RefOperationValidationListener {
    private final PermissionBackend.WithUser perm;
    private final AllUsersName allUsersName;

    DisallowCreationAndDeletionOfGerritMaintainedBranches(
        PermissionBackend.WithUser perm, AllUsersName allUsersName) {
      this.perm = perm;
      this.allUsersName = allUsersName;
    }

    @Override
    public List<ValidationMessage> onRefOperation(RefReceivedEvent refEvent)
        throws ValidationException {
      if (refEvent.project.getNameKey().equals(allUsersName)) {
        if (refEvent.command.getRefName().startsWith(RefNames.REFS_USERS)
            && !refEvent.command.getRefName().equals(RefNames.REFS_USERS_DEFAULT)) {
          if (refEvent.command.getType().equals(ReceiveCommand.Type.CREATE)) {
            try {
              perm.check(GlobalPermission.ACCESS_DATABASE);
            } catch (AuthException | PermissionBackendException e) {
              throw new ValidationException("Not allowed to create user branch.");
            }
            if (Account.Id.fromRef(refEvent.command.getRefName()) == null) {
              throw new ValidationException(
                  String.format(
                      "Not allowed to create non-user branch under %s.", RefNames.REFS_USERS));
            }
          } else if (refEvent.command.getType().equals(ReceiveCommand.Type.DELETE)) {
            try {
              perm.check(GlobalPermission.ACCESS_DATABASE);
            } catch (AuthException | PermissionBackendException e) {
              throw new ValidationException("Not allowed to delete user branch.");
            }
          }
        }

        if (RefNames.isGroupRef(refEvent.command.getRefName())) {
          if (refEvent.command.getType().equals(ReceiveCommand.Type.CREATE)) {
            throw new ValidationException("Not allowed to create group branch.");
          } else if (refEvent.command.getType().equals(ReceiveCommand.Type.DELETE)) {
            throw new ValidationException("Not allowed to delete group branch.");
          }
        }
      }
      return ImmutableList.of();
    }
  }
}

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

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.events.RefReceivedEvent;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RefOperationValidators {
  private static final GetErrorMessages GET_ERRORS = new GetErrorMessages();
  private static final Logger LOG = LoggerFactory.getLogger(RefOperationValidators.class);

  public interface Factory {
    RefOperationValidators create(Project project, IdentifiedUser user, ReceiveCommand cmd);
  }

  public static ReceiveCommand getCommand(RefUpdate update, ReceiveCommand.Type type) {
    return new ReceiveCommand(
        update.getExpectedOldObjectId(), update.getNewObjectId(), update.getName(), type);
  }

  private final PermissionBackend.WithUser perm;
  private final AllUsersName allUsersName;
  private final DynamicSet<RefOperationValidationListener> refOperationValidationListeners;
  private final RefReceivedEvent event;

  @Inject
  RefOperationValidators(
      PermissionBackend permissionBackend,
      AllUsersName allUsersName,
      DynamicSet<RefOperationValidationListener> refOperationValidationListeners,
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
    List<RefOperationValidationListener> listeners = new ArrayList<>();
    listeners.add(new DisallowCreationAndDeletionOfUserBranches(perm, allUsersName));
    refOperationValidationListeners.forEach(l -> listeners.add(l));
    try {
      for (RefOperationValidationListener listener : listeners) {
        messages.addAll(listener.onRefOperation(event));
      }
    } catch (ValidationException e) {
      messages.add(new ValidationMessage(e.getMessage(), true));
      withException = true;
    }

    if (withException) {
      throwException(messages, event);
    }

    return messages;
  }

  private void throwException(Iterable<ValidationMessage> messages, RefReceivedEvent event)
      throws RefOperationValidationException {
    Iterable<ValidationMessage> errors = Iterables.filter(messages, GET_ERRORS);
    String header =
        String.format(
            "Ref \"%s\" %S in project %s validation failed",
            event.command.getRefName(), event.command.getType(), event.project.getName());
    LOG.error(header);
    throw new RefOperationValidationException(header, errors);
  }

  private static class GetErrorMessages implements Predicate<ValidationMessage> {
    @Override
    public boolean apply(ValidationMessage input) {
      return input.isError();
    }
  }

  private static class DisallowCreationAndDeletionOfUserBranches
      implements RefOperationValidationListener {
    private final PermissionBackend.WithUser perm;
    private final AllUsersName allUsersName;

    DisallowCreationAndDeletionOfUserBranches(
        PermissionBackend.WithUser perm, AllUsersName allUsersName) {
      this.perm = perm;
      this.allUsersName = allUsersName;
    }

    @Override
    public List<ValidationMessage> onRefOperation(RefReceivedEvent refEvent)
        throws ValidationException {
      if (refEvent.project.getNameKey().equals(allUsersName)
          && (refEvent.command.getRefName().startsWith(RefNames.REFS_USERS)
              && !refEvent.command.getRefName().equals(RefNames.REFS_USERS_DEFAULT))) {
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
      return ImmutableList.of();
    }
  }
}

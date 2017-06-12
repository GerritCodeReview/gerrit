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
import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.events.RefReceivedEvent;
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
        update.getOldObjectId(), update.getNewObjectId(), update.getName(), type);
  }

  private final RefReceivedEvent event;
  private final DynamicSet<RefOperationValidationListener> refOperationValidationListeners;

  @Inject
  RefOperationValidators(
      DynamicSet<RefOperationValidationListener> refOperationValidationListeners,
      @Assisted Project project,
      @Assisted IdentifiedUser user,
      @Assisted ReceiveCommand cmd) {
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
      for (RefOperationValidationListener listener : refOperationValidationListeners) {
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
}

// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.flow;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.Optional;

/**
 * Extension point to plug in a service to manage flows.
 *
 * <p>A {@link Flow} is an automation rule on a change that triggers actions on the change when the
 * flow conditions become satisfied. For example, a flow can be an automation rule that adds a
 * reviewer to the change when the change has been verified by the CI.
 *
 * <p>Updating an existing flow is not supported. Instead the flow should be deleted and then be
 * re-created.
 */
@ExtensionPoint
public interface FlowService {

  /**
   * Checks if Flows is enabled for change.
   *
   * <p>Can be used to disable flows at a change/project level. Implementations can have user
   * information injected to disable it for a specific user.
   *
   * @param projectName The name of the project that contains the change.
   * @param changeId The ID of the change for which the flows should be listed.
   * @return If flows is enabled for the user.
   * @throws RestApiException thrown if checking flow access has failed
   */
  public Boolean isFlowsEnabled(Project.NameKey projectName, Change.Id changeId)
      throws RestApiException;

  /**
   * Create a new flow.
   *
   * @param flowCreation parameters needed for the flow creation
   * @return the newly created flow
   * @throws FlowPermissionDeniedException thrown if the caller is not allowed to create the flow
   * @throws InvalidFlowException thrown is the flow to be created is invalid
   * @throws StorageException thrown if storing the flow has failed
   */
  @CanIgnoreReturnValue
  Flow createFlow(FlowCreation flowCreation)
      throws FlowPermissionDeniedException, InvalidFlowException, StorageException;

  /**
   * Retrieves a flow.
   *
   * @param flowKey the key of the flow
   * @return the flow if it was found and the user can see it, otherwise {@link Optional#empty()}
   * @throws StorageException thrown if accessing the flow storage has failed
   */
  Optional<Flow> getFlow(FlowKey flowKey) throws StorageException;

  /**
   * Deletes a flow
   *
   * @param flowKey the key of the flow
   * @return the deleted flow, {@link Optional#empty()} if no flow with the given key was found or
   *     if the flow is not visible to the current user
   * @throws FlowPermissionDeniedException thrown if the caller can see the flow and is not allowed
   *     to delete it
   * @throws StorageException thrown if deleting the flow has failed
   */
  @CanIgnoreReturnValue
  Optional<Flow> deleteFlow(FlowKey flowKey) throws FlowPermissionDeniedException, StorageException;

  /**
   * Lists the flows for one change.
   *
   * <p>The order of the returned flows is stable, but depends on the flow service implementation.
   *
   * @param projectName The name of the project that contains the change.
   * @param changeId The ID of the change for which the flows should be listed.
   * @return The flows of the change. The service may filter out flows that are not visible to the
   *     current user.
   * @throws StorageException thrown if accessing the flow storage has failed
   */
  ImmutableList<Flow> listFlows(Project.NameKey projectName, Change.Id changeId)
      throws StorageException;
}

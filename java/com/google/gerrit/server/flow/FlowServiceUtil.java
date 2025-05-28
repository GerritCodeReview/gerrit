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

import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;

/** Helper class to access the {@link FlowService}. */
@Singleton
public class FlowServiceUtil {
  private final DynamicItem<FlowService> flowService;

  @Inject
  FlowServiceUtil(DynamicItem<FlowService> flowService) {
    this.flowService = flowService;
  }

  /**
   * Returns the {@link FlowService} if a flow service is bound.
   *
   * @return the {@link FlowService}, or {@link Optional#empty()} if no flow service is bound.
   */
  public Optional<FlowService> getFlowService() {
    return Optional.ofNullable(flowService.get());
  }

  /**
   * Returns the {@link FlowService} if a flow service has been bound, or throws a {@link
   * MethodNotAllowedException} otherwise.
   *
   * @return the {@link FlowService}
   * @throws MethodNotAllowedException thrown if no flow service is bound
   */
  public FlowService getFlowServiceOrThrow() throws MethodNotAllowedException {
    return getFlowService()
        .orElseThrow(() -> new MethodNotAllowedException("No FlowService bound."));
  }
}

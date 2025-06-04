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

package com.google.gerrit.server.restapi.change.flow;

import static com.google.gerrit.server.change.ChangeResource.CHANGE_KIND;
import static com.google.gerrit.server.restapi.change.flow.FlowResource.FLOW_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;

/** Guice module that binds all REST endpoints for {@code /changes/<change-id>/flows}. */
public class ChangeFlowsRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    bind(FlowCollection.class);

    DynamicMap.mapOf(binder(), FLOW_KIND);

    /** List flows for change {@code GET /changes/<change-id>/flows}. */
    child(CHANGE_KIND, "flows").to(FlowCollection.class);
    /** Create flow for change {@code POST /changes/<change-id>/flows}. */
    postOnCollection(FLOW_KIND).to(CreateFlow.class);

    /** Get flow for change {@code GET /changes/<change-id>/flows/<flow-id>}. */
    get(FLOW_KIND).to(GetFlow.class);
    /** Delete flow for change {@code DELETE /changes/<change-id>/flows/<flow-id>}. */
    delete(FLOW_KIND).to(DeleteFlow.class);
  }
}

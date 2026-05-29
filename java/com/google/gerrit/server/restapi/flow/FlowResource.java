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

package com.google.gerrit.server.restapi.flow;

import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.flow.Flow;
import com.google.inject.TypeLiteral;

/** REST resource that represents members in the {@link FlowCollection}. */
public class FlowResource implements RestResource {
  public static final TypeLiteral<RestView<FlowResource>> FLOW_KIND = new TypeLiteral<>() {};

  private final Flow flow;

  public FlowResource(Flow flow) {
    this.flow = flow;
  }

  public Flow getFlow() {
    return flow;
  }
}

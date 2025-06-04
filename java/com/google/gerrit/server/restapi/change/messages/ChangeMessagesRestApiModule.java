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

package com.google.gerrit.server.restapi.change.messages;

import static com.google.gerrit.server.change.ChangeMessageResource.CHANGE_MESSAGE_KIND;
import static com.google.gerrit.server.change.ChangeResource.CHANGE_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;

/** Guice module that binds all REST endpoints for {@code /changes/<change-id>/messages}. */
public class ChangeMessagesRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), CHANGE_MESSAGE_KIND);

    /** List messages of change {@code GET /changes/<change-id>/messages}. */
    child(CHANGE_KIND, "messages").to(ChangeMessages.class);

    /** Get message of change {@code GET /changes/<change-id>/messages/<message-id>}. */
    get(CHANGE_MESSAGE_KIND).to(GetChangeMessage.class);
    /** Delete message of change {@code DELETE /changes/<change-id>/messages/<message-id>}. */
    delete(CHANGE_MESSAGE_KIND).to(DeleteChangeMessage.DefaultDeleteChangeMessage.class);

    /** Delete message of change {@code POST /changes/<change-id>/messages/<message-id>/delete}. */
    post(CHANGE_MESSAGE_KIND, "delete").to(DeleteChangeMessage.class);
  }
}

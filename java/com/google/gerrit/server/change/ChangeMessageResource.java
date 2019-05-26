// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.inject.TypeLiteral;

/** A change message resource. */
public class ChangeMessageResource implements RestResource {
  public static final TypeLiteral<RestView<ChangeMessageResource>> CHANGE_MESSAGE_KIND =
      new TypeLiteral<RestView<ChangeMessageResource>>() {};

  private final ChangeResource changeResource;
  private final ChangeMessageInfo changeMessage;
  private final int changeMessageIndex;

  public ChangeMessageResource(
      ChangeResource changeResource, ChangeMessageInfo changeMessage, int changeMessageIndex) {
    this.changeResource = changeResource;
    this.changeMessage = changeMessage;
    this.changeMessageIndex = changeMessageIndex;
  }

  public ChangeResource getChangeResource() {
    return changeResource;
  }

  public ChangeMessageInfo getChangeMessage() {
    return changeMessage;
  }

  public Change.Id getChangeId() {
    return changeResource.getId();
  }

  public String getChangeMessageId() {
    return changeMessage.id;
  }

  /**
   * Gets the index of the change message among all messages of the change sorted by creation time.
   *
   * @return the index of the change message.
   */
  public int getChangeMessageIndex() {
    return changeMessageIndex;
  }
}

// Copyright (C) 2011 The Android Open Source Project
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
// limitations under the License

package com.google.gerrit.client.ui;

import com.google.gwt.event.shared.GwtEvent;

/** Project list popup on move pointer event. */
public class ProjectListPopupOnMovePointerEvent extends
    GwtEvent<ProjectListPopupHandler> {
  private static final Type<ProjectListPopupHandler> TYPE =
      new Type<ProjectListPopupHandler>();

  private boolean popingUp;
  private String projectName;

  public ProjectListPopupOnMovePointerEvent(final boolean popingUp,
      String projectName) {
    this.popingUp = popingUp;
    this.projectName = projectName;
  }

  public static Type<ProjectListPopupHandler> getType() {
    return TYPE;
  }

  public boolean isPopingUp() {
    return popingUp;
  }

  public String getProjectName() {
    return projectName;
  }

  @Override
  protected void dispatch(ProjectListPopupHandler handler) {
    handler.onMovePointer(this);
  }

  @Override
  public Type<ProjectListPopupHandler> getAssociatedType() {
    return TYPE;
  }
}

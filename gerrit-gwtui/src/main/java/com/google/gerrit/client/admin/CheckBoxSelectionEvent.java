// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.client.admin;

import com.google.gwt.event.shared.GwtEvent;

/** CheckBox click event. */
public class CheckBoxSelectionEvent extends GwtEvent<CheckBoxSelectionHandler> {
  private static final Type<CheckBoxSelectionHandler> TYPE =
      new Type<CheckBoxSelectionHandler>();

  private final boolean checked;
  private final String projectName;

  public CheckBoxSelectionEvent(final boolean checked, final String projectName) {
    this.checked = checked;
    this.projectName = projectName;
  }

  public static Type<CheckBoxSelectionHandler> getType() {
    return TYPE;
  }

  /** @returns True if checkBox is checked. False otherwise.*/
  public boolean isChecked() {
    return checked;
  }

  /** @returns The name of selected project.*/
  public String getProjectName() {
    return projectName;
  }

  @Override
  protected void dispatch(CheckBoxSelectionHandler handler) {
    handler.onValueChange(this);
  }

  @Override
  public Type<CheckBoxSelectionHandler> getAssociatedType() {
    return TYPE;
  }
}

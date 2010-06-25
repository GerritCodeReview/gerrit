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

/** Setting parent project on close event */
public class ParentSettingOnCloseEvent extends GwtEvent<ParentSettingHandler> {
  private static final Type<ParentSettingHandler> TYPE =
      new Type<ParentSettingHandler>();

  public ParentSettingOnCloseEvent() {
  }

  public static Type<ParentSettingHandler> getType() {
    return TYPE;
  }

  @Override
  protected void dispatch(ParentSettingHandler handler) {
    handler.onClose(this);
  }

  @Override
  public Type<ParentSettingHandler> getAssociatedType() {
    return TYPE;
  }
}

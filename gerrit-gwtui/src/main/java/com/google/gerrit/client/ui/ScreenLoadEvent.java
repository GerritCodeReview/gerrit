// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.client.ui;

import com.google.gwt.event.shared.GwtEvent;

public class ScreenLoadEvent extends GwtEvent<ScreenLoadHandler> {
  private final Screen screen;

  public ScreenLoadEvent(Screen screen) {
    super();
    this.screen = screen;
  }

  public static final Type<ScreenLoadHandler> TYPE = new Type<ScreenLoadHandler>();

  @Override
  protected void dispatch(ScreenLoadHandler handler) {
    handler.onScreenLoad(this);
  }

  @Override
  public GwtEvent.Type<ScreenLoadHandler> getAssociatedType() {
    return TYPE;
  }

  public Screen getScreen(){
    return screen;
  }
}

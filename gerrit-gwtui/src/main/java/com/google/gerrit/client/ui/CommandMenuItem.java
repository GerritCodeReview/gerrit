// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gerrit.client.Gerrit;
import com.google.gwt.aria.client.Roles;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.Anchor;

public class CommandMenuItem extends Anchor implements ClickHandler {
  private final Command command;

  public CommandMenuItem(String text, Command cmd) {
    super(text);
    setStyleName(Gerrit.RESOURCES.css().menuItem());
    Roles.getMenuitemRole().set(getElement());
    addClickHandler(this);
    command = cmd;
  }

  @Override
  public void onClick(ClickEvent event) {
    setFocus(false);
    command.execute();
  }
}

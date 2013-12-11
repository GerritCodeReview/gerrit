// Copyright (C) 2013 The Android Open Source Project
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

package ${package}.client;

import com.google.gerrit.plugin.client.PluginEntryPoint;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.VerticalPanel;

import ${package}.HelloMenu;

/**
 * HelloWorld Plugin.
 */
public class HelloPlugin extends PluginEntryPoint {

  @Override
  public void onPluginLoad() {
    // Create the dialog box
    final DialogBox dialogBox = new DialogBox();

    // The content of the dialog comes from a User specified Preference
    dialogBox.setText("Hello from GWT Gerrit UI plugin");
    dialogBox.setAnimationEnabled(true);
    Button closeButton = new Button("Close");
    VerticalPanel dialogVPanel = new VerticalPanel();
    dialogVPanel.setWidth("100%");
    dialogVPanel.setHorizontalAlignment(VerticalPanel.ALIGN_CENTER);
    dialogVPanel.add(closeButton);

    closeButton.addClickHandler(new ClickHandler() {
      public void onClick(ClickEvent event) {
        dialogBox.hide();
      }
    });

    // Set the contents of the Widget
    dialogBox.setWidget(dialogVPanel);

    RootPanel rootPanel = RootPanel.get(HelloMenu.MENU_ID);
    rootPanel.getElement().removeAttribute("href");
    rootPanel.addDomHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          dialogBox.center();
          dialogBox.show();
        }
    }, ClickEvent.getType());
  }
}

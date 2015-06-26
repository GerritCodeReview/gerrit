// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gerrit.plugin.client.screen.Screen;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.VerticalPanel;

public class HelloScreen extends VerticalPanel {

  static class Factory implements Screen.EntryPoint {
    @Override
    public void onLoad(Screen screen) {
      screen.setPageTitle("Hello");
      screen.show(new HelloScreen());
    }
  }

  HelloScreen() {
    setStyleName("hello-panel");
    add(new Label("Hello World Screen"));
  }
}

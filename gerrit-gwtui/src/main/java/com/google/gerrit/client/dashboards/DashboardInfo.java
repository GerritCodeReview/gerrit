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

package com.google.gerrit.client.dashboards;

import com.google.gwt.core.client.JavaScriptObject;

public class DashboardInfo extends JavaScriptObject {
  public final native String id() /*-{ return this.id; }-*/;
  public final native String name() /*-{ return this.dashboard_name; }-*/;
  public final native String refName() /*-{ return this.ref_name; }-*/;
  public final native String projectName() /*-{ return this.project_name; }-*/;
  public final native String description() /*-{ return this.description; }-*/;
  public final native String parameters() /*-{ return this.parameters; }-*/;
  public final native boolean isDefault() /*-{ return this.isDefault ? true : false; }-*/;

  protected DashboardInfo() {
  }
}

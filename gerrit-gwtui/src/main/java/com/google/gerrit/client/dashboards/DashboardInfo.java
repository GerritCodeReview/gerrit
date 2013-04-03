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
  public final native String title() /*-{ return this.title; }-*/;
  public final native String project() /*-{ return this.project; }-*/;
  public final native String definingProject() /*-{ return this.defining_project; }-*/;
  public final native String ref() /*-{ return this.ref; }-*/;
  public final native String path() /*-{ return this.path; }-*/;
  public final native String description() /*-{ return this.description; }-*/;
  public final native String foreach() /*-{ return this.foreach; }-*/;
  public final native String url() /*-{ return this.url; }-*/;
  public final native boolean isDefault() /*-{ return this['default'] ? true : false; }-*/;

  protected DashboardInfo() {
  }
}

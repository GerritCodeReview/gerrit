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

package com.google.gerrit.client.plugins;

import com.google.gwt.core.client.JavaScriptObject;

public class PluginInfo extends JavaScriptObject {
  public final native String name() /*-{ return this.name }-*/;

  public final native String version() /*-{ return this.version }-*/;

  public final native String indexUrl() /*-{ return this.index_url }-*/;

  public final native boolean disabled() /*-{ return this.disabled || false }-*/;

  protected PluginInfo() {}
}

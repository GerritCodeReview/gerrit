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

package com.google.gerrit.client.projects;

import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gwt.core.client.JavaScriptObject;

public class RefInfo extends JavaScriptObject {
  public final String getShortName() {
    return RefNames.shortName(ref());
  }

  public final native String ref() /*-{ return this.ref; }-*/;

  public final native String revision() /*-{ return this.revision; }-*/;

  protected RefInfo() {}
}

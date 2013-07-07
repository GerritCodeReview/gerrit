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

package com.google.gerrit.client.diff;

import com.google.gwt.core.client.JavaScriptObject;

public class FileInfo extends JavaScriptObject {
  public final native String path() /*-{ return this.path; }-*/;
  public final native String old_path() /*-{ return this.old_path; }-*/;
  public final native int lines_inserted() /*-{ return this.lines_inserted || 0; }-*/;
  public final native int lines_deleted() /*-{ return this.lines_deleted || 0; }-*/;
  public final native boolean binary() /*-{ return this.binary || false; }-*/;

  protected FileInfo() {
  }
}

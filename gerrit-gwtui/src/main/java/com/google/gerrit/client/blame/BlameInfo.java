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

package com.google.gerrit.client.blame;

import com.google.gerrit.client.RangeInfo;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;

public class BlameInfo extends JavaScriptObject {
  public final native String author() /*-{ return this.author; }-*/;

  public final native String id() /*-{ return this.id; }-*/;

  public final native String commitMsg() /*-{ return this.commit_msg; }-*/;

  public final native int time() /*-{ return this.time; }-*/;

  public final native JsArray<RangeInfo> ranges() /*-{ return this.ranges; }-*/;

  protected BlameInfo() {}
}

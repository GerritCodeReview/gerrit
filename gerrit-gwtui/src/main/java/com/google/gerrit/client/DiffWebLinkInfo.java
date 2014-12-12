// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.client;

public class DiffWebLinkInfo extends WebLinkInfo {
  public final native boolean showOnSideBySideDiffView()
  /*-{ return this.show_on_side_by_side_diff_view || false; }-*/;

  public final native boolean showOnUnifiedDiffView()
  /*-{ return this.show_on_unified_diff_view || false; }-*/;

  protected DiffWebLinkInfo() {
  }
}

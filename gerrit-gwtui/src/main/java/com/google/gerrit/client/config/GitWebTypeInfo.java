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

package com.google.gerrit.client.config;

import com.google.gwt.core.client.JavaScriptObject;

public class GitWebTypeInfo extends JavaScriptObject {
  /**
   * Replace the standard path separator ('/') in a branch name or project
   * name with a custom path separator configured by the property
   * gitweb.pathSeparator.
   * @param urlSegment The branch or project to replace the path separator in
   * @return the urlSegment with the standard path separator replaced by the
   * custom path separator
   */
  public final String replacePathSeparator(String urlSegment) {
    if ('/' != pathSeparator()) {
      return urlSegment.replace('/', pathSeparator());
    }
    return urlSegment;
  }

  public final native String name() /*-{ return this.name; }-*/;
  public final native String revision() /*-{ return this.revision; }-*/;
  public final native String project() /*-{ return this.project; }-*/;
  public final native String branch() /*-{ return this.branch; }-*/;
  public final native String rootTree() /*-{ return this.root_tree; }-*/;
  public final native String file() /*-{ return this.file; }-*/;
  public final native String fileHistory() /*-{ return this.file_history; }-*/;
  public final native char pathSeparator() /*-{ return this.path_separator; }-*/;
  public final native boolean linkDrafts() /*-{ return this.link_drafts; }-*/;
  public final native boolean urlEncode() /*-{ return this.url_encode; }-*/;

  protected GitWebTypeInfo() {
  }
}

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

package com.google.gerrit.client.info;

import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.common.data.FilenameComparator;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import java.util.Collections;
import java.util.Comparator;

public class FileInfo extends JavaScriptObject {
  public final native String path() /*-{ return this.path; }-*/;

  public final native String oldPath() /*-{ return this.old_path; }-*/;

  public final native int linesInserted() /*-{ return this.lines_inserted || 0; }-*/;

  public final native int linesDeleted() /*-{ return this.lines_deleted || 0; }-*/;

  public final native boolean binary() /*-{ return this.binary || false; }-*/;

  public final native String status() /*-{ return this.status; }-*/;

  // JSNI methods cannot have 'long' as a parameter type or a return type and
  // it's suggested to use double in this case:
  // http://www.gwtproject.org/doc/latest/DevGuideCodingBasicsJSNI.html#important
  public final long size() {
    return (long) _size();
  }

  private native double _size() /*-{ return this.size || 0; }-*/;

  public final long sizeDelta() {
    return (long) _sizeDelta();
  }

  private native double _sizeDelta() /*-{ return this.size_delta || 0; }-*/;

  public final native int _row() /*-{ return this._row }-*/;

  public final native void _row(int r) /*-{ this._row = r }-*/;

  public static void sortFileInfoByPath(JsArray<FileInfo> list) {
    Collections.sort(
        Natives.asList(list), Comparator.comparing(FileInfo::path, FilenameComparator.INSTANCE));
  }

  public static String getFileName(String path) {
    String fileName;
    if (Patch.COMMIT_MSG.equals(path)) {
      fileName = "Commit Message";
    } else if (Patch.MERGE_LIST.equals(path)) {
      fileName = "Merge List";
    } else {
      fileName = path;
    }

    int s = fileName.lastIndexOf('/');
    return s >= 0 ? fileName.substring(s + 1) : fileName;
  }

  protected FileInfo() {}
}

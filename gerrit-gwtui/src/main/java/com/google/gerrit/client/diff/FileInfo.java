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

import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;

import java.util.Collections;
import java.util.Comparator;

public class FileInfo extends JavaScriptObject {
  public final native String path() /*-{ return this.path; }-*/;
  public final native String old_path() /*-{ return this.old_path; }-*/;
  public final native int lines_inserted() /*-{ return this.lines_inserted || 0; }-*/;
  public final native int lines_deleted() /*-{ return this.lines_deleted || 0; }-*/;
  public final native boolean binary() /*-{ return this.binary || false; }-*/;
  public final native String status() /*-{ return this.status; }-*/;

  public final native int _row() /*-{ return this._row }-*/;
  public final native void _row(int r) /*-{ this._row = r }-*/;

  public static void sortFileInfoByPath(JsArray<FileInfo> list) {
    Collections.sort(Natives.asList(list), new Comparator<FileInfo>() {
      @Override
      public int compare(FileInfo a, FileInfo b) {
        if (Patch.COMMIT_MSG.equals(a.path())) {
          return -1;
        } else if (Patch.COMMIT_MSG.equals(b.path())) {
          return 1;
        }
        // Look at file suffixes to check if it makes sense to use a different order
        int s1 = a.path().lastIndexOf('.');
        int s2 = b.path().lastIndexOf('.');
        if (s1 > 0 && s2 > 0 &&
            a.path().substring(0, s1).equals(b.path().substring(0, s2))) {
            String suffixA = a.path().substring(s1);
            String suffixB = b.path().substring(s2);
            // C++ and C: give priority to header files (.h/.hpp/...)
            if (suffixA.indexOf(".h") == 0) {
                return -1;
            } else if (suffixB.indexOf(".h") == 0) {
                return 1;
            }
        }
        return a.path().compareTo(b.path());
      }
    });
  }

  public static String getFileName(String path) {
    String fileName = Patch.COMMIT_MSG.equals(path)
        ? Util.C.commitMessage()
        : path;
    int s = fileName.lastIndexOf('/');
    return s >= 0 ? fileName.substring(s + 1) : fileName;
  }

  protected FileInfo() {
  }
}

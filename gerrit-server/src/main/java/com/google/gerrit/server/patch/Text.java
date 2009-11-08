// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.patch;

import com.google.gerrit.common.data.SparseFileContent;

import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.util.RawParseUtils;

public class Text extends RawText {
  public static final Text EMPTY = new Text(new byte[0]);

  public Text(final byte[] r) {
    super(r);
  }

  public byte[] getContent() {
    return content;
  }

  public String getLine(final int i) {
    final int s = lines.get(i + 1);
    int e = lines.get(i + 2);
    if (content[e - 1] == '\n') {
      e--;
    }
    return RawParseUtils.decode(Constants.CHARSET, content, s, e);
  }

  public void addLineTo(final SparseFileContent out, final int i) {
    out.addLine(i, getLine(i));
  }
}

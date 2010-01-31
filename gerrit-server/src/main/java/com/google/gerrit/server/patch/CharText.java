// Copyright (C) 2010 The Android Open Source Project
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

import org.eclipse.jgit.diff.Sequence;

class CharText implements Sequence {
  private final String content;

  CharText(Text text, int s, int e) {
    content = text.getLines(s, e);
  }

  @Override
  public boolean equals(int a, Sequence other, int b) {
    return content.charAt(a) == ((CharText) other).content.charAt(b);
  }

  @Override
  public int size() {
    return content.length();
  }
}

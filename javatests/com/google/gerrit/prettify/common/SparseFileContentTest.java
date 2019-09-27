// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.prettify.common;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class SparseFileContentTest {
  private SparseFileContent content;

  private SparseFileContent createContent(int[] lines) {
    SparseFileContent content = new SparseFileContent();

    for(int line : lines) {
      content.addLine(line, Integer.toString(line));
    }
    if(lines.length > 0) {
      content.setSize(lines[lines.length - 1] + 1);
    }
    return content;
  }

  @Test
  public void nextIterationLineByLine() throws Exception {
    int[] lines = new int[] {0, 1, 2, 5, 6, 8, 10};
    SparseFileContent content = createContent(lines);
    assertEquals(1, content.next(0));
    assertEquals(2, content.next(1));
    assertEquals(5, content.next(2));
    assertEquals(6, content.next(5));
    assertEquals(8, content.next(6));
    assertEquals(10, content.next(8));
    assertEquals(11, content.next(10));
  }

  @Test
  public void nextIdxIncrementByOne() throws Exception {
    int[] lines = new int[] {0, 1, 2, 5, 6, 8, 10};
    SparseFileContent content = createContent(lines);
    assertEquals(1, content.next(0));
    assertEquals(2, content.next(1));
    assertEquals(5, content.next(2));
    assertEquals(5, content.next(3));
    assertEquals(5, content.next(4));
    assertEquals(6, content.next(5));
    assertEquals(8, content.next(6));
    assertEquals(8, content.next(7));
    assertEquals(10, content.next(8));
    assertEquals(10, content.next(9));
    assertEquals(11, content.next(10));
    assertEquals(11, content.next(11));
  }
}

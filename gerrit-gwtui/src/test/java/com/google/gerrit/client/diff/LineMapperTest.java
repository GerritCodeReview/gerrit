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

import static org.junit.Assert.assertEquals;

import com.google.gerrit.client.diff.LineMapper.LineOnOtherInfo;
import org.junit.Test;

/** Unit tests for LineMapper */
public class LineMapperTest {

  @Test
  public void testAppendCommon() {
    LineMapper mapper = new LineMapper();
    mapper.appendCommon(10);
    assertEquals(10, mapper.getLineA());
    assertEquals(10, mapper.getLineB());
  }

  @Test
  public void testAppendInsert() {
    LineMapper mapper = new LineMapper();
    mapper.appendInsert(10);
    assertEquals(0, mapper.getLineA());
    assertEquals(10, mapper.getLineB());
  }

  @Test
  public void testAppendDelete() {
    LineMapper mapper = new LineMapper();
    mapper.appendDelete(10);
    assertEquals(10, mapper.getLineA());
    assertEquals(0, mapper.getLineB());
  }

  @Test
  public void testFindInCommon() {
    LineMapper mapper = new LineMapper();
    mapper.appendCommon(10);
    assertEquals(new LineOnOtherInfo(9, true), mapper.lineOnOther(DisplaySide.A, 9));
    assertEquals(new LineOnOtherInfo(9, true), mapper.lineOnOther(DisplaySide.B, 9));
  }

  @Test
  public void testFindAfterCommon() {
    LineMapper mapper = new LineMapper();
    mapper.appendCommon(10);
    assertEquals(new LineOnOtherInfo(10, true), mapper.lineOnOther(DisplaySide.A, 10));
    assertEquals(new LineOnOtherInfo(10, true), mapper.lineOnOther(DisplaySide.B, 10));
  }

  @Test
  public void testFindInInsertGap() {
    LineMapper mapper = new LineMapper();
    mapper.appendInsert(10);
    assertEquals(new LineOnOtherInfo(-1, false), mapper.lineOnOther(DisplaySide.B, 9));
  }

  @Test
  public void testFindAfterInsertGap() {
    LineMapper mapper = new LineMapper();
    mapper.appendInsert(10);
    assertEquals(new LineOnOtherInfo(0, true), mapper.lineOnOther(DisplaySide.B, 10));
    assertEquals(new LineOnOtherInfo(10, true), mapper.lineOnOther(DisplaySide.A, 0));
  }

  @Test
  public void testFindInDeleteGap() {
    LineMapper mapper = new LineMapper();
    mapper.appendDelete(10);
    assertEquals(new LineOnOtherInfo(-1, false), mapper.lineOnOther(DisplaySide.A, 9));
  }

  @Test
  public void testFindAfterDeleteGap() {
    LineMapper mapper = new LineMapper();
    mapper.appendDelete(10);
    assertEquals(new LineOnOtherInfo(0, true), mapper.lineOnOther(DisplaySide.A, 10));
    assertEquals(new LineOnOtherInfo(10, true), mapper.lineOnOther(DisplaySide.B, 0));
  }

  @Test
  public void testReplaceWithInsertInB() {
    // 0 c c
    // 1 a b
    // 2 a b
    // 3 - b
    // 4 - b
    // 5 c c
    LineMapper mapper = new LineMapper();
    mapper.appendCommon(1);
    mapper.appendReplace(2, 4);
    mapper.appendCommon(1);

    assertEquals(4, mapper.getLineA());
    assertEquals(6, mapper.getLineB());

    assertEquals(new LineOnOtherInfo(1, true), mapper.lineOnOther(DisplaySide.B, 1));
    assertEquals(new LineOnOtherInfo(3, true), mapper.lineOnOther(DisplaySide.B, 5));

    assertEquals(new LineOnOtherInfo(2, true), mapper.lineOnOther(DisplaySide.B, 2));
    assertEquals(new LineOnOtherInfo(2, false), mapper.lineOnOther(DisplaySide.B, 3));
  }
}

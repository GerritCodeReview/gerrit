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

import com.google.gerrit.common.changes.Side;

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
    assertEquals(9, mapper.lineOnOther(Side.PARENT, 9));
  }

  @Test
  public void testFindAfterCommon() {
    LineMapper mapper = new LineMapper();
    mapper.appendCommon(10);
    assertEquals(10, mapper.lineOnOther(Side.PARENT, 10));
  }

  @Test
  public void testFindInInsertGap() {
    LineMapper mapper = new LineMapper();
    mapper.appendInsert(10);
    assertEquals(-1, mapper.lineOnOther(Side.REVISION, 9));
  }

  @Test
  public void testFindAfterInsertGap() {
    LineMapper mapper = new LineMapper();
    mapper.appendInsert(10);
    assertEquals(0, mapper.lineOnOther(Side.REVISION, 10));
  }

  @Test
  public void testFindInDeleteGap() {
    LineMapper mapper = new LineMapper();
    mapper.appendDelete(10);
    assertEquals(-1, mapper.lineOnOther(Side.PARENT, 9));
  }

  @Test
  public void testFindAfterDeleteGap() {
    LineMapper mapper = new LineMapper();
    mapper.appendDelete(10);
    assertEquals(0, mapper.lineOnOther(Side.PARENT, 10));
  }
}

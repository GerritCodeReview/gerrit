// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.project.constraints;

import static org.junit.Assert.assertEquals;

import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ApprovalCategoryConstraintsConfigTest {

  static private Field refPatternField;
  static private Field minValueField;
  static private Field maxValueField;

  @BeforeClass
  static public void setupFieldAccessors() throws SecurityException,
      NoSuchFieldException {
    refPatternField = Allow.class.getDeclaredField("allowedRef");
    refPatternField.setAccessible(true);
    minValueField = Allow.class.getDeclaredField("allowedMin");
    minValueField.setAccessible(true);
    maxValueField = Allow.class.getDeclaredField("allowedMax");
    maxValueField.setAccessible(true);
  }

  @Test
  public void refOnly() throws InvalidConstraintException {
    check("refs/heads/master", "refs/heads/master", Short.MIN_VALUE, Short.MAX_VALUE);
  }

  @Test
  public void refColon() throws InvalidConstraintException {
    check("refs/heads/master:", "refs/heads/master", Short.MIN_VALUE, Short.MAX_VALUE);
  }

  @Test
  public void refColonComma() throws InvalidConstraintException {
    check("refs/heads/master:,", "refs/heads/master", Short.MIN_VALUE, Short.MAX_VALUE);
  }

  @Test
  public void refColonMax() throws InvalidConstraintException {
    check("refs/heads/master:3", "refs/heads/master", Short.MIN_VALUE,
        (short) 3);
    check("refs/heads/master:+3", "refs/heads/master", Short.MIN_VALUE,
        (short) 3);
    check("refs/heads/master:-3", "refs/heads/master", Short.MIN_VALUE,
        (short) -3);
  }

  @Test
  public void refColonMinComma() throws InvalidConstraintException {
    check("refs/heads/master:3,", "refs/heads/master", (short) 3,
        Short.MAX_VALUE);
    check("refs/heads/master:+3,", "refs/heads/master", (short) 3,
        Short.MAX_VALUE);
    check("refs/heads/master:-3,", "refs/heads/master", (short) -3,
        Short.MAX_VALUE);
  }

  @Test
  public void refColonCommaMax() throws InvalidConstraintException {
    check("refs/heads/master:,3", "refs/heads/master", Short.MIN_VALUE,
        (short) 3);
    check("refs/heads/master:,+3", "refs/heads/master", Short.MIN_VALUE,
        (short) 3);
    check("refs/heads/master:,-3", "refs/heads/master", Short.MIN_VALUE,
        (short) -3);
  }

  @Test
  public void refColonMinCommaMax() throws InvalidConstraintException {
    check("refs/heads/master:-2,3", "refs/heads/master", (short) -2, (short) 3);
    check("refs/heads/master:-2,+3", "refs/heads/master", (short) -2, (short) 3);
    check("refs/heads/master:3,3", "refs/heads/master", (short) 3, (short) 3);
 }

  @Test
  public void refColonMaxCommaMin() throws InvalidConstraintException {
    check("refs/heads/master:+3,-2", "refs/heads/master", (short) -2, (short) 3);
    check("refs/heads/master:3,-2", "refs/heads/master", (short) -2, (short) 3);
  }

  @Test
  public void refPattern() throws InvalidConstraintException {
    check("refs/heads/*:-2,3", "refs/heads/*", (short) -2, (short) 3);
  }

  @Test
  public void refRegEx() throws InvalidConstraintException {
    check("^refs/heads/[:,]:-2,3", "^refs/heads/[:,]", (short) -2, (short) 3);
  }

  @Test
  public void refColonMaxCommaMinWhitespace() throws InvalidConstraintException {
    check(" refs/heads/master : +3 , -2 ", "refs/heads/master", (short) -2, (short) 3);
  }

  @Test(expected = InvalidConstraintException.class)
  public void invalidMax() throws InvalidConstraintException {
    parseAllow("refs/heads/master:invalid_number");
  }

  @Test(expected = InvalidConstraintException.class)
  public void invalidMaxValidMin() throws InvalidConstraintException {
    parseAllow("refs/heads/master:-1,invalid_number");
  }

  @Test(expected = InvalidConstraintException.class)
  public void invalidMin() throws InvalidConstraintException {
    parseAllow("refs/heads/master:invalid_number,1");
  }

  @Test(expected = InvalidConstraintException.class)
  public void invalidMinAndMax() throws InvalidConstraintException {
    parseAllow("refs/heads/master:invalid_number,invalid_number");
  }

  @Test
  public void invalidRefRegEx() throws InvalidConstraintException {
    parseAllow("^refs/heads/[\\:-2,3");
  }

  static private Allow parseAllow(String value) throws InvalidConstraintException {
      try {
        Method m = ApprovalCategoryConstraintsConfig.class.getDeclaredMethod(
                "parseAllow", String.class);
        m.setAccessible(true);
        return (Allow) m.invoke(null, value);
      } catch (SecurityException e) {
        throw new RuntimeException(e);
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      } catch (IllegalArgumentException e) {
        throw new RuntimeException(e);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      } catch (InvocationTargetException e) {
        if (e.getCause() instanceof InvalidConstraintException) {
          throw (InvalidConstraintException) e.getCause();
        }
        throw new RuntimeException(e.getCause());
      }
  }

  static private void check(String value, String refPattern, short min,
      short max) throws InvalidConstraintException {
    try {
      Allow a = parseAllow(value);
      assertEquals(refPattern, refPattern(a));
      assertEquals(min, minValue(a));
      assertEquals(max, maxValue(a));
    } catch (IllegalArgumentException e) {
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (SecurityException e) {
      throw new RuntimeException(e);
    }
  }

  static private String refPattern(Allow a) throws IllegalArgumentException,
      IllegalAccessException {
    return (String) refPatternField.get(a);
  }

  static private short minValue(Allow a) throws IllegalArgumentException,
      IllegalAccessException {
    return (Short) minValueField.get(a);
  }

  static private short maxValue(Allow a) throws IllegalArgumentException,
      IllegalAccessException {
    return (Short) maxValueField.get(a);
  }
}

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

package com.google.gerrit.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.google.gerrit.test.GerritTestProperty.EnumProperty;
import com.google.gerrit.test.GerritTestProperty.FileProperty;
import com.google.gerrit.test.GerritTestProperty.IntegerProperty;
import com.google.gerrit.test.GerritTestProperty.StringProperty;
import com.google.gerrit.test.GerritTestProperty.UrlProperty;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class TestGerritTestProperty {

  private final static String TEST_SUB_NAMESPACE = "test.";
  private final static String NAMESPACE_PREFIX =
      GerritTestProperty.NAMESPACE_PREFIX + TEST_SUB_NAMESPACE;
  private static Properties storedPropeties = new Properties();

  @BeforeClass
  public static void setup() {
    storeProperties();
    clearProperties();
  }

  @Test
  public void testString() {
    final StringProperty strProp =
        new StringProperty(TEST_SUB_NAMESPACE + "my-string");
    assertEquals(NAMESPACE_PREFIX + "my-string", strProp.getName());
    assertNull(strProp.get());
    try {
      strProp.getOrFail();
      fail("should have failed");
    } catch (PropertyNotSetException e) {
      // expected
    }

    System.setProperty(NAMESPACE_PREFIX + "my-string-2", "abcd");
    final StringProperty strProp2 =
        new StringProperty(TEST_SUB_NAMESPACE + "my-string-2");
    assertEquals("abcd", strProp2.get());
    assertEquals("abcd", strProp2.getOrFail());
  }

  @Test
  public void testInteger() {
    final IntegerProperty intProp =
        new IntegerProperty(TEST_SUB_NAMESPACE + "my-int", 42);
    assertEquals(NAMESPACE_PREFIX + "my-int", intProp.getName());
    assertEquals(42, intProp.get().intValue());
    assertEquals(42, intProp.getOrFail().intValue());

    System.setProperty(NAMESPACE_PREFIX + "my-int-2", "1234");
    final IntegerProperty intProp2 =
        new IntegerProperty(TEST_SUB_NAMESPACE + "my-int-2", 42);
    assertEquals(1234, intProp2.get().intValue());
    assertEquals(1234, intProp2.getOrFail().intValue());

    System.setProperty(NAMESPACE_PREFIX + "my-int-3", "invalid");
    final IntegerProperty intProp3 =
        new IntegerProperty(TEST_SUB_NAMESPACE + "my-int-3", 42);
    try {
      intProp3.get();
      fail("should have failed");
    } catch (InvalidPropertyValueException e) {
      // expected
    }
  }

  @Test
  public void testUrl() throws MalformedURLException {
    final UrlProperty urlProp = new UrlProperty(TEST_SUB_NAMESPACE + "my-url");
    assertEquals(NAMESPACE_PREFIX + "my-url", urlProp.getName());
    assertNull(urlProp.get());
    try {
      urlProp.getOrFail();
      fail("should have failed");
    } catch (PropertyNotSetException e) {
      // expected
    }

    System.setProperty(NAMESPACE_PREFIX + "my-url-2", "http://localhost");
    final UrlProperty urlProp2 =
        new UrlProperty(TEST_SUB_NAMESPACE + "my-url-2");
    assertEquals(new URL("http://localhost"), urlProp2.get());
    assertEquals(new URL("http://localhost"), urlProp2.getOrFail());

    System.setProperty(NAMESPACE_PREFIX + "my-url-3", "invalid");
    final UrlProperty urlProp3 =
        new UrlProperty(TEST_SUB_NAMESPACE + "my-url-3");
    try {
      urlProp3.get();
      fail("should have failed");
    } catch (InvalidPropertyValueException e) {
      // expected
    }
  }

  @Test
  public void testFile() {
    final FileProperty fileProp =
        new FileProperty(TEST_SUB_NAMESPACE + "my-file");
    assertEquals(NAMESPACE_PREFIX + "my-file", fileProp.getName());
    assertNull(fileProp.get());
    try {
      fileProp.getOrFail();
      fail("should have failed");
    } catch (PropertyNotSetException e) {
      // expected
    }

    System.setProperty(NAMESPACE_PREFIX + "my-file-2", "c:\\temp\\test.txt");
    final FileProperty fileProp2 =
        new FileProperty(TEST_SUB_NAMESPACE + "my-file-2");
    assertEquals((new File("c:\\temp\\test.txt")).getAbsolutePath(), fileProp2
        .get().getAbsolutePath());
    assertEquals((new File("c:\\temp\\test.txt")).getAbsolutePath(), fileProp2
        .getOrFail().getAbsolutePath());

    System.setProperty(NAMESPACE_PREFIX + "my-file-3", "x:\\some-dir\\non-existing.txt");
    final FileProperty fileProp3 =
        new FileProperty(TEST_SUB_NAMESPACE + "my-file-3", true, true);
    assertEquals(NAMESPACE_PREFIX + "my-file-3", fileProp3.getName());
    try {
      fileProp3.get();
      fail("should have failed");
    } catch (InvalidPropertyValueException e) {
      // expected
    }
  }

  private enum TestEnum {
    TESTENUM, TESTENUM2
  };

  @Test
  public void testEnum() {
    final EnumProperty<TestEnum> enumProp =
        new EnumProperty<TestEnum>(TEST_SUB_NAMESPACE + "my-enum",
            TestEnum.class);
    assertEquals(NAMESPACE_PREFIX + "my-enum", enumProp.getName());
    assertNull(enumProp.get());
    try {
      enumProp.getOrFail();
      fail("should have failed");
    } catch (PropertyNotSetException e) {
      // expected
    }

    final EnumProperty<TestEnum> enumProp2 =
        new EnumProperty<TestEnum>(TEST_SUB_NAMESPACE + "my-enum-2",
            TestEnum.TESTENUM, TestEnum.class);
    assertEquals(TestEnum.TESTENUM, enumProp2.get());
    assertEquals(TestEnum.TESTENUM, enumProp2.getOrFail());

    System
        .setProperty(NAMESPACE_PREFIX + "my-enum-3", TestEnum.TESTENUM.name());
    final EnumProperty<TestEnum> enumProp3 =
        new EnumProperty<TestEnum>(TEST_SUB_NAMESPACE + "my-enum-3",
            TestEnum.TESTENUM2, TestEnum.class);
    assertEquals(TestEnum.TESTENUM, enumProp3.get());

    System.setProperty(NAMESPACE_PREFIX + "my-enum-4", TestEnum.TESTENUM.name()
        .toLowerCase());
    final EnumProperty<TestEnum> enumProp4 =
        new EnumProperty<TestEnum>(TEST_SUB_NAMESPACE + "my-enum-4",
            TestEnum.class);
    assertEquals(TestEnum.TESTENUM, enumProp4.get());

    System.setProperty(NAMESPACE_PREFIX + "my-enum-5", "invalid");
    final EnumProperty<TestEnum> enumProp5 =
        new EnumProperty<TestEnum>(TEST_SUB_NAMESPACE + "my-enum-5",
            TestEnum.class);
    try {
      enumProp5.get();
      fail("should have failed");
    } catch (InvalidPropertyValueException e) {
      // expected
    }
  }

  @AfterClass
  public static void cleanup() {
    clearProperties();
    System.getProperties().putAll(storedPropeties);
  }

  private static void storeProperties() {
    for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
      final Object key = entry.getKey();
      if (key instanceof String && ((String) key).startsWith(NAMESPACE_PREFIX)) {
        storedPropeties.put(entry.getKey(), entry.getValue());
      }
    }
  }

  private static void clearProperties() {
    final Set<String> toBeCleared = new HashSet<String>();

    for (final Object key : System.getProperties().keySet()) {
      if (key instanceof String && ((String) key).startsWith(NAMESPACE_PREFIX)) {
        toBeCleared.add((String) key);
      }
    }

    for (final String keyName : toBeCleared) {
      System.clearProperty(keyName);
    }
  }
}

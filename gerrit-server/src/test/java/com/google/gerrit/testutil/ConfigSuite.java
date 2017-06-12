// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.testutil;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.runner.Runner;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.Suite;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;

/**
 * Suite to run tests with different {@code gerrit.config} values.
 *
 * <p>For each {@link Config} method in the class and base classes, a new group of tests is created
 * with the {@link Parameter} field set to the config.
 *
 * <pre>
 * {@literal @}RunWith(ConfigSuite.class)
 * public abstract class MyAbstractTest {
 *   {@literal @}ConfigSuite.Parameter
 *   protected Config cfg;
 *
 *   {@literal @}ConfigSuite.Config
 *   public static Config firstConfig() {
 *     Config cfg = new Config();
 *     cfg.setString("gerrit", null, "testValue", "a");
 *   }
 * }
 *
 * public class MyTest extends MyAbstractTest {
 *   {@literal @}ConfigSuite.Config
 *   public static Config secondConfig() {
 *     Config cfg = new Config();
 *     cfg.setString("gerrit", null, "testValue", "b");
 *   }
 *
 *   {@literal @}Test
 *   public void myTest() {
 *     // Test using cfg.
 *   }
 * }
 * </pre>
 *
 * This creates a suite of tests with three groups:
 *
 * <ul>
 *   <li><strong>default</strong>: {@code MyTest.myTest}
 *   <li><strong>firstConfig</strong>: {@code MyTest.myTest[firstConfig]}
 *   <li><strong>secondConfig</strong>: {@code MyTest.myTest[secondConfig]}
 * </ul>
 *
 * Additionally, config values used by <strong>default</strong> can be set in a method annotated
 * with {@code @ConfigSuite.Default}.
 *
 * <p>The name of the config method corresponding to the currently-running test can be stored in a
 * field annotated with {@code @ConfigSuite.Name}.
 */
public class ConfigSuite extends Suite {
  private static final String DEFAULT = "default";

  @Target({METHOD})
  @Retention(RUNTIME)
  public static @interface Default {}

  @Target({METHOD})
  @Retention(RUNTIME)
  public static @interface Config {}

  @Target({FIELD})
  @Retention(RUNTIME)
  public static @interface Parameter {}

  @Target({FIELD})
  @Retention(RUNTIME)
  public static @interface Name {}

  private static class ConfigRunner extends BlockJUnit4ClassRunner {
    private final Method configMethod;
    private final Field parameterField;
    private final Field nameField;
    private final String name;

    private ConfigRunner(
        Class<?> clazz, Field parameterField, Field nameField, String name, Method configMethod)
        throws InitializationError {
      super(clazz);
      this.parameterField = parameterField;
      this.nameField = nameField;
      this.name = name;
      this.configMethod = configMethod;
    }

    @Override
    public Object createTest() throws Exception {
      Object test = getTestClass().getJavaClass().newInstance();
      parameterField.set(test, callConfigMethod(configMethod));
      if (nameField != null) {
        nameField.set(test, name);
      }
      return test;
    }

    @Override
    protected String getName() {
      return MoreObjects.firstNonNull(name, DEFAULT);
    }

    @Override
    protected String testName(FrameworkMethod method) {
      String n = method.getName();
      return name == null ? n : n + "[" + name + "]";
    }
  }

  private static List<Runner> runnersFor(Class<?> clazz) {
    Method defaultConfig = getDefaultConfig(clazz);
    List<Method> configs = getConfigs(clazz);
    Field parameterField = getOnlyField(clazz, Parameter.class);
    checkArgument(parameterField != null, "No @ConfigSuite.Field found");
    Field nameField = getOnlyField(clazz, Name.class);
    List<Runner> result = Lists.newArrayListWithCapacity(configs.size() + 1);
    try {
      result.add(new ConfigRunner(clazz, parameterField, nameField, null, defaultConfig));
      for (Method m : configs) {
        result.add(new ConfigRunner(clazz, parameterField, nameField, m.getName(), m));
      }
      return result;
    } catch (InitializationError e) {
      System.err.println("Errors initializing runners:");
      for (Throwable t : e.getCauses()) {
        t.printStackTrace();
      }
      throw new RuntimeException(e);
    }
  }

  private static Method getDefaultConfig(Class<?> clazz) {
    Method result = null;
    for (Method m : clazz.getMethods()) {
      Default ann = m.getAnnotation(Default.class);
      if (ann != null) {
        checkArgument(
            result == null,
            "Multiple methods annotated with @ConfigSuite.Method: %s, %s",
            result,
            m);
        result = m;
      }
    }
    return result;
  }

  private static List<Method> getConfigs(Class<?> clazz) {
    List<Method> result = Lists.newArrayListWithExpectedSize(3);
    for (Method m : clazz.getMethods()) {
      Config ann = m.getAnnotation(Config.class);
      if (ann != null) {
        checkArgument(
            !m.getName().equals(DEFAULT), "@ConfigSuite.Config cannot be named %s", DEFAULT);
        result.add(m);
      }
    }
    return result;
  }

  private static org.eclipse.jgit.lib.Config callConfigMethod(Method m) {
    if (m == null) {
      return new org.eclipse.jgit.lib.Config();
    }
    checkArgument(
        org.eclipse.jgit.lib.Config.class.isAssignableFrom(m.getReturnType()),
        "%s must return Config",
        m);
    checkArgument((m.getModifiers() & Modifier.STATIC) != 0, "%s must be static", m);
    checkArgument(m.getParameterTypes().length == 0, "%s must take no parameters", m);
    try {
      return (org.eclipse.jgit.lib.Config) m.invoke(null);
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private static Field getOnlyField(Class<?> clazz, Class<? extends Annotation> ann) {
    List<Field> fields = Lists.newArrayListWithExpectedSize(1);
    for (Field f : clazz.getFields()) {
      if (f.getAnnotation(ann) != null) {
        fields.add(f);
      }
    }
    checkArgument(
        fields.size() <= 1,
        "expected 1 @ConfigSuite.%s field, found: %s",
        ann.getSimpleName(),
        fields);
    return Iterables.getFirst(fields, null);
  }

  public ConfigSuite(Class<?> clazz) throws InitializationError {
    super(clazz, runnersFor(clazz));
  }
}

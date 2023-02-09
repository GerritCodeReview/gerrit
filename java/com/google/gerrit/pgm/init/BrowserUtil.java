// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.pgm.init;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.Locale;

/** A utility class to open a browser for a specific URL. */
class BrowserUtil {
  private BrowserUtil() {}

  static void openBrowser(String url) throws Exception {
    try {
      String osName = getProperty("os.name", "linux").toLowerCase(Locale.ENGLISH);
      Runtime rt = Runtime.getRuntime();
      String browser = System.getenv("BROWSER");
      if (browser != null) {
        if (browser.startsWith("call:")) {
          browser = browser.substring("call:".length());
          callStaticMethod(browser, url);
        } else if (browser.indexOf("%url") >= 0) {
          String[] args = browser.split(",");
          for (int i = 0; i < args.length; i++) {
            args[i] = args[i].replaceAll("%url", url);
          }
          rt.exec(args);
        } else if (osName.indexOf("windows") >= 0) {
          rt.exec(new String[] {"cmd.exe", "/C", browser, url});
        } else {
          rt.exec(new String[] {browser, url});
        }
        return;
      }
      try {
        Class<?> desktopClass = Class.forName("java.awt.Desktop");
        // Desktop.isDesktopSupported()
        Boolean supported =
            (Boolean) desktopClass.getMethod("isDesktopSupported").invoke(null, new Object[0]);
        URI uri = new URI(url);
        if (supported) {
          // Desktop.getDesktop();
          Object desktop = desktopClass.getMethod("getDesktop").invoke(null, new Object[0]);
          // desktop.browse(uri);
          desktopClass.getMethod("browse", URI.class).invoke(desktop, uri);
          return;
        }
      } catch (Exception e) {
        // ignore
      }
      if (osName.indexOf("windows") >= 0) {
        rt.exec(new String[] {"rundll32", "url.dll,FileProtocolHandler", url});
      } else if (osName.indexOf("mac") >= 0 || osName.indexOf("darwin") >= 0) {
        // Mac OS: to open a page with Safari, use "open -a Safari"
        Runtime.getRuntime().exec(new String[] {"open", url});
      } else {
        String[] browsers = {
          "chromium",
          "google-chrome",
          "firefox",
          "mozilla-firefox",
          "mozilla",
          "konqueror",
          "netscape",
          "opera",
          "midori"
        };
        boolean ok = false;
        for (String b : browsers) {
          try {
            rt.exec(new String[] {b, url});
            ok = true;
            break;
          } catch (Exception e) {
            // ignore and try the next
          }
        }
        if (!ok) {
          // No success in detection.
          throw new Exception("Browser detection failed");
        }
      }
    } catch (Exception e) {
      throw new Exception(
          "Failed to start a browser to open the URL " + url + ": " + e.getMessage());
    }
  }

  private static String getProperty(String key, String defaultValue) {
    try {
      return System.getProperty(key, defaultValue);
    } catch (SecurityException se) {
      return defaultValue;
    }
  }

  private static Object callStaticMethod(String classAndMethod, Object... params) throws Exception {
    int lastDot = classAndMethod.lastIndexOf('.');
    String className = classAndMethod.substring(0, lastDot);
    String methodName = classAndMethod.substring(lastDot + 1);
    return callMethod(null, Class.forName(className), methodName, params);
  }

  private static Object callMethod(
      Object instance, Class<?> clazz, String methodName, Object... params) throws Exception {
    Method best = null;
    int bestMatch = 0;
    boolean isStatic = instance == null;
    for (Method m : clazz.getMethods()) {
      if (Modifier.isStatic(m.getModifiers()) == isStatic && m.getName().equals(methodName)) {
        int p = match(m.getParameterTypes(), params);
        if (p > bestMatch) {
          bestMatch = p;
          best = m;
        }
      }
    }
    if (best == null) {
      throw new NoSuchMethodException(methodName);
    }
    return best.invoke(instance, params);
  }

  private static int match(Class<?>[] params, Object[] values) {
    int len = params.length;
    if (len == values.length) {
      int points = 1;
      for (int i = 0; i < len; i++) {
        Class<?> pc = getNonPrimitiveClass(params[i]);
        Object v = values[i];
        Class<?> vc = v == null ? null : v.getClass();
        if (pc == vc) {
          points++;
        } else if (vc == null) {
          // can't verify
        } else if (!pc.isAssignableFrom(vc)) {
          return 0;
        }
      }
      return points;
    }
    return 0;
  }

  private static Class<?> getNonPrimitiveClass(Class<?> clazz) {
    if (!clazz.isPrimitive()) {
      return clazz;
    } else if (clazz == boolean.class) {
      return Boolean.class;
    } else if (clazz == byte.class) {
      return Byte.class;
    } else if (clazz == char.class) {
      return Character.class;
    } else if (clazz == double.class) {
      return Double.class;
    } else if (clazz == float.class) {
      return Float.class;
    } else if (clazz == int.class) {
      return Integer.class;
    } else if (clazz == long.class) {
      return Long.class;
    } else if (clazz == short.class) {
      return Short.class;
    } else if (clazz == void.class) {
      return Void.class;
    }
    return clazz;
  }
}

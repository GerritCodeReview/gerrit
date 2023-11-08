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

@SuppressWarnings("DefaultPackage")
public final class Main {
  private static final String FLOGGER_BACKEND_PROPERTY = "flogger.backend_factory";
  private static final String FLOGGER_LOGGING_CONTEXT = "flogger.logging_context";
  private static final Runtime.Version MIN_JAVA_VERSION = Runtime.Version.parse("11.0.10");

  // We don't do any real work here because we need to import
  // the archive lookup code and we cannot import a class in
  // the default package. So this is just a tiny springboard
  // to jump into the real main code.
  //

  public static void main(String[] argv) throws Exception {
    if (onSupportedJavaVersion()) {
      configureFloggerBackend();
      com.google.gerrit.launcher.GerritLauncher.main(argv);

    } else {
      System.exit(1);
    }
  }

  private static boolean onSupportedJavaVersion() {
    Runtime.Version version = Runtime.version();
    if (version.compareTo(MIN_JAVA_VERSION) >= 0) {
      return true;
    }
    System.err.println("fatal: Gerrit Code Review requires Java " + MIN_JAVA_VERSION + " or later");
    System.err.println("       (trying to run on Java " + version + ")");
    return false;
  }

  private static void configureFloggerBackend() {
    System.setProperty(
        FLOGGER_LOGGING_CONTEXT, "com.google.gerrit.server.logging.LoggingContext#getInstance");

    if (System.getProperty(FLOGGER_BACKEND_PROPERTY) != null) {
      // Flogger backend is already configured
      return;
    }

    // Configure log4j backend
    System.setProperty(
        FLOGGER_BACKEND_PROPERTY,
        "com.google.common.flogger.backend.log4j.Log4jBackendFactory#getInstance");
  }

  private Main() {}
}

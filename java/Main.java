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

public final class Main {
  // We don't do any real work here because we need to import
  // the archive lookup code and we cannot import a class in
  // the default package. So this is just a tiny springboard
  // to jump into the real main code.
  //

  public static void main(String[] argv) throws Exception {
    if (onSupportedJavaVersion()) {
      com.google.gerrit.launcher.GerritLauncher.main(argv);

    } else {
      System.exit(1);
    }
  }

  private static boolean onSupportedJavaVersion() {
    final String version = System.getProperty("java.specification.version");
    if (1.8 <= parse(version)) {
      return true;
    }
    System.err.println("fatal: Gerrit Code Review requires Java 8 or later");
    System.err.println("       (trying to run on Java " + version + ")");
    return false;
  }

  private static double parse(String version) {
    if (version == null || version.length() == 0) {
      return 0.0;
    }

    try {
      final int fd = version.indexOf('.');
      final int sd = version.indexOf('.', fd + 1);
      if (0 < sd) {
        version = version.substring(0, sd);
      }
      return Double.parseDouble(version);
    } catch (NumberFormatException e) {
      return 0.0;
    }
  }

  private Main() {}
}

// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public class SshLogTestUtil {
  private static final int MAX_RETRY = 3;

  public static boolean validateLogWithRetry(String sitePath, String command) throws Exception {
    int count = 0;
    boolean result = false;
    while (count < MAX_RETRY && !result) {
      result = validateLog(sitePath, command);
      count++;
      TimeUnit.SECONDS.sleep(1);
    }
    return result;
  }

  public static boolean validateLog(String sitePath, String command) throws Exception {
    Path logFile = Paths.get(sitePath, "/logs/sshd_log");
    String commandLog = String.join(".", command.split(" "));
    try (FileInputStream fstream = new FileInputStream(logFile.toFile())) {
      BufferedReader br = new BufferedReader(new InputStreamReader(fstream));
      String strLine;
      while ((strLine = br.readLine()) != null) {
        if (strLine.contains(commandLog)) {
          return true;
        }
      }
    }
    return false;
  }
}

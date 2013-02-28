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

package com.google.gerrit.acceptance;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TempFileUtil {

  private static int testCount;
  private static DateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
  private static final File temp = new File(new File("target"), "temp");

  private static String createUniqueTestFolderName() {
    return "test_" + (df.format(new Date()) + "_" + (testCount++));
  }

  public static File createTempDirectory() {
    final String name = createUniqueTestFolderName();
    final File directory = new File(temp, name);
    if (!directory.mkdirs()) {
      throw new RuntimeException("failed to create folder '"
          + directory.getAbsolutePath() + "'");
    }
    return directory;
  }
}

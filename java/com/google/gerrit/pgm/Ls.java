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

package com.google.gerrit.pgm;

import com.google.gerrit.launcher.GerritLauncher;
import com.google.gerrit.pgm.util.AbstractProgram;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** List the files available in our archive. */
public class Ls extends AbstractProgram {
  @Override
  public int run() throws IOException {
    try (ZipFile zf = new ZipFile(GerritLauncher.getDistributionArchive())) {
      final Enumeration<? extends ZipEntry> e = zf.entries();
      while (e.hasMoreElements()) {
        final ZipEntry ze = e.nextElement();
        String name = ze.getName();
        boolean show = false;
        show |= name.startsWith("WEB-INF/");
        show |= name.equals("LICENSES.txt");

        show &= !ze.isDirectory();
        show &= !name.startsWith("WEB-INF/classes/");
        show &= !name.startsWith("WEB-INF/lib/");
        show &= !name.startsWith("WEB-INF/pgm-lib/");
        show &= !name.equals("WEB-INF/web.xml");
        if (show) {
          if (name.startsWith("WEB-INF/")) {
            name = name.substring("WEB-INF/".length());
          }
          System.out.println(name);
        }
      }
    }
    return 0;
  }
}

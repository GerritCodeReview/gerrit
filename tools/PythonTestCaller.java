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

import static org.junit.Assert.assertTrue;

import com.google.common.io.ByteStreams;
import com.google.common.base.Splitter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;

public class PythonTestCaller {

  @Test
  public void resolveUrl() throws Exception {
    PythonTestCaller.pythonUnit("tools", "util_test");
  }

  private static void pythonUnit(String d, String sut) throws Exception {
    ProcessBuilder b =
        new ProcessBuilder(Splitter.on(' ').splitToList(
                "python -m unittest " + sut))
            .directory(new File(d))
            .redirectErrorStream(true);
    Process p = null;
    InputStream i = null;
    byte[] out;
    try {
      p = b.start();
      i = p.getInputStream();
      out = ByteStreams.toByteArray(i);
    } catch (IOException e) {
      throw new Exception(e);
    } finally {
      if (p != null) {
        p.getOutputStream().close();
      }
      if (i != null) {
        i.close();
      }
    }
    int value;
    try {
      value = p.waitFor();
    } catch (InterruptedException e) {
      throw new Exception("interrupted waiting for process");
    }
    String err = new String(out, "UTF-8");
    if (value != 0) {
      System.err.print(err);
    }
    assertTrue(err, value == 0);
  }
}

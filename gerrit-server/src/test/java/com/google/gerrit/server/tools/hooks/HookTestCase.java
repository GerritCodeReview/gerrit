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
//
// Portions related to finding the hook script to execute are:
// Copyright (C) 2008, Imran M Yousuf <imyousuf@smartitengineering.com>
//
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or
// without modification, are permitted provided that the following
// conditions are met:
//
// - Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
//
// - Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following
// disclaimer in the documentation and/or other materials provided
// with the distribution.
//
// - Neither the name of the Git Development Community nor the
// names of its contributors may be used to endorse or promote
// products derived from this software without specific prior
// written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
// CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
// INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
// OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
// CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
// NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
// CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
// STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
// ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.google.gerrit.server.tools.hooks;

import static com.google.common.truth.Truth.assert_;

import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;

@Ignore
public abstract class HookTestCase extends LocalDiskRepositoryTestCase {
  protected Repository repository;
  private final Map<String, File> hooks = new TreeMap<>();
  private final List<File> cleanup = new ArrayList<>();

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    repository = createWorkRepository();
  }

  @Override
  @After
  public void tearDown() throws Exception {
    super.tearDown();
    for (File p : cleanup) {
      if (!p.delete()) {
        p.deleteOnExit();
      }
    }
    cleanup.clear();
  }

  protected File getHook(String name) throws IOException {
    File hook = hooks.get(name);
    if (hook != null) {
      return hook;
    }

    String scproot = "com/google/gerrit/server/tools/root";
    String path = scproot + "/hooks/" + name;
    String errorMessage = "Cannot locate " + path + " in CLASSPATH";
    URL url = cl().getResource(path);
    assert_().withFailureMessage(errorMessage).that(url).isNotNull();

    String protocol = url.getProtocol();
    assert_().withFailureMessage("Cannot invoke " + url).that(protocol).isAnyOf("file", "jar");

    if ("file".equals(protocol)) {
      hook = new File(url.getPath());
      assert_().withFailureMessage(errorMessage).that(hook.isFile()).isTrue();
      long time = hook.lastModified();
      hook.setExecutable(true);
      hook.setLastModified(time);
      hooks.put(name, hook);
    } else if ("jar".equals(protocol)) {
      try (InputStream in = url.openStream()) {
        hook = File.createTempFile("hook_", ".sh");
        cleanup.add(hook);
        try (OutputStream out = Files.newOutputStream(hook.toPath())) {
          ByteStreams.copy(in, out);
        }
      }
      hook.setExecutable(true);
      hooks.put(name, hook);
    }
    return hook;
  }

  private ClassLoader cl() {
    return HookTestCase.class.getClassLoader();
  }
}

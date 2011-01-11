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

import static org.junit.Assert.fail;

import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

public abstract class HookTestCase extends LocalDiskRepositoryTestCase {
  protected Repository repository;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    repository = createWorkRepository();
  }

  protected File getHook(final String name) {
    final String scproot = "com/google/gerrit/server/tools/root";
    final String path = scproot + "/hooks/" + name;
    final URL url = cl().getResource(path);
    if (url == null) {
      fail("Cannot locate " + path + " in CLASSPATH");
    }

    File hook;
    try {
      hook = new File(url.toURI());
    } catch (URISyntaxException e) {
      hook = new File(url.getPath());
    }
    if (!hook.isFile()) {
      fail("Cannot locate " + path + " in CLASSPATH");
    }

    // The hook was copied out of our source control system into the
    // target area by Java tools. Its not executable in the source
    // are, nor did the copying Java program make it executable in the
    // destination area. So we must force it to be executable.
    //
    final long time = hook.lastModified();
    hook.setExecutable(true);
    hook.setLastModified(time);
    return hook;
  }

  private ClassLoader cl() {
    return HookTestCase.class.getClassLoader();
  }
}

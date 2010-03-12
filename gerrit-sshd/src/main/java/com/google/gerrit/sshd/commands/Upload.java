// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.server.project.RefControl;
import com.google.gerrit.sshd.AbstractGitCommand;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RefFilter;
import org.eclipse.jgit.transport.UploadPack;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Publishes Git repositories over SSH using the Git upload-pack protocol. */
final class Upload extends AbstractGitCommand implements RefFilter {
  @Override
  protected void runImpl() throws IOException {
    final UploadPack up = new UploadPack(repo);
    up.setRefFilter(this);
    up.upload(in, out, err);
  }

  @Override
  public Map<String, Ref> filter(Map<String,Ref> refs) {
    Map<String, Ref> result = new HashMap<String, Ref>();
    for (String k : refs.keySet()) {
      Ref ref = refs.get(k);
      RefControl ctl = projectControl.controlForRef(ref.getName());
      if (ctl.isVisible()) {
        result.put(k, ref);
      }
    }
    return result;
  }
}

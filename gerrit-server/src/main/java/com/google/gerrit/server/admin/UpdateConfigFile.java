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

package com.google.gerrit.server.admin;

import com.google.common.io.ByteStreams;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.PutInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.admin.UpdateConfigFile.Input;
import com.google.gerrit.server.plugins.PluginLoader;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
class UpdateConfigFile implements RestModifyView<ConfigFileResource, Input> {
  static final Logger log = LoggerFactory.getLogger(PluginLoader.class);
  static class Input {
    PutInput raw;
  }

  @Override
  public Object apply(ConfigFileResource resource, Input input)
      throws AuthException, BadRequestException, ResourceConflictException,
      Exception {
    File current = resource.getConfigFile();
    File dir = current.getParentFile();

    File tmp = asTemp(input, ".next_" + current.getName(), dir);
    validate(tmp, current);

    File old = new File(dir, ".last_" + current.getName());

    synchronized (this) {
      log.info(String.format("Replacing %s with %s",
          current.getCanonicalPath(), tmp.getCanonicalPath()));
      current.renameTo(old);
      tmp.renameTo(current);
      log.info(String.format("Replaced %s with %s, restart server to make"
          + " changes active",
          current.getCanonicalPath(), tmp.getCanonicalPath()));
    }
    return Response.ok("");
  }

  private static File asTemp(Input input,
      String prefix, File dir) throws IOException {
    File tmp = File.createTempFile(prefix, "", dir);
    boolean keep = false;
    InputStream in = input.raw.getInputStream();
    try {
      FileOutputStream out = new FileOutputStream(tmp);
      try {
        ByteStreams.copy(in, out);
        keep = true;
        return tmp;
      } finally {
        out.close();
      }
    } finally {
      if (!keep) {
        tmp.delete();
      }
      in.close();
    }
  }

  private void validate(File tmp, File target) throws IOException, BadRequestException {
    if (target.getName().endsWith(".config")) {
      try {
        new FileBasedConfig(tmp, FS.DETECTED).load();
      } catch (ConfigInvalidException e) {
        log.error("Error parsing " + tmp.getCanonicalPath(), e);
        throw new BadRequestException(tmp.getCanonicalPath()
            + " is not parseable as a Git style config file");
      }
    }
  }
}

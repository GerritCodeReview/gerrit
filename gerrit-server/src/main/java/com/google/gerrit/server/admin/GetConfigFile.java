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
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.RestReadView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
public class GetConfigFile implements RestReadView<ConfigFileResource> {

  @Override
  public BinaryResult apply(ConfigFileResource rsrc) {
    final File f = rsrc.getConfigFile();
    return new BinaryResult() {
      @Override
      public void writeTo(OutputStream os) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
          ByteStreams.copy(in, os);
        } finally {
          in.close();
        }
      }
    }.setContentLength(f.length()).base64();
  }
}

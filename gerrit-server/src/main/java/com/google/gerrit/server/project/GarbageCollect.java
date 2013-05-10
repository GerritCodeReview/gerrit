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

package com.google.gerrit.server.project;

import com.google.common.base.Charsets;
import com.google.gerrit.common.data.GarbageCollectionResult;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.git.GarbageCollection;
import com.google.gerrit.server.project.GarbageCollect.Input;
import com.google.inject.Inject;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Collections;

@RequiresCapability(GlobalCapability.RUN_GC)
public class GarbageCollect implements RestModifyView<ProjectResource, Input> {
  public static class Input {
  }

  private GarbageCollection.Factory garbageCollectionFactory;

  @Inject
  GarbageCollect(GarbageCollection.Factory garbageCollectionFactory) {
    this.garbageCollectionFactory = garbageCollectionFactory;
  }

  @Override
  public BinaryResult apply(final ProjectResource rsrc, Input input) {
    return new BinaryResult() {
      @Override
      public void writeTo(OutputStream out) throws IOException {
        PrintWriter writer = new PrintWriter(
            new OutputStreamWriter(out, Charsets.UTF_8)) {
          @Override
          public void println() {
            write('\n');
          }
        };
        try {
          GarbageCollectionResult result = garbageCollectionFactory.create().run(
              Collections.singletonList(rsrc.getNameKey()), writer);
          if (result.hasErrors()) {
            for (GarbageCollectionResult.Error e : result.getErrors()) {
              String msg;
              switch (e.getType()) {
                case REPOSITORY_NOT_FOUND:
                  msg = "error: project \"" + e.getProjectName() + "\" not found";
                  break;
                case GC_ALREADY_SCHEDULED:
                  msg = "error: garbage collection for project \""
                      + e.getProjectName() + "\" was already scheduled";
                  break;
                case GC_FAILED:
                  msg = "error: garbage collection for project \"" + e.getProjectName()
                      + "\" failed";
                  break;
                default:
                  msg = "error: garbage collection for project \"" + e.getProjectName()
                      + "\" failed: " + e.getType();
              }
              writer.println(msg);
            }
          }
        } finally {
          writer.flush();
        }
      }
    }.setContentType("text/plain; charset=UTF-8")
     .disableGzip();
  }
}

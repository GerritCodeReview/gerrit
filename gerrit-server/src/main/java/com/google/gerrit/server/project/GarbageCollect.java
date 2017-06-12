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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.common.data.GarbageCollectionResult;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.git.GarbageCollection;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.project.GarbageCollect.Input;
import com.google.gerrit.server.util.IdGenerator;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Collections;

@RequiresCapability(GlobalCapability.RUN_GC)
@Singleton
public class GarbageCollect
    implements RestModifyView<ProjectResource, Input>, UiAction<ProjectResource> {
  public static class Input {
    public boolean showProgress;
    public boolean aggressive;
    public boolean async;
  }

  private final boolean canGC;
  private final GarbageCollection.Factory garbageCollectionFactory;
  private final WorkQueue workQueue;
  private final Provider<String> canonicalUrl;

  @Inject
  GarbageCollect(
      GitRepositoryManager repoManager,
      GarbageCollection.Factory garbageCollectionFactory,
      WorkQueue workQueue,
      @CanonicalWebUrl Provider<String> canonicalUrl) {
    this.workQueue = workQueue;
    this.canonicalUrl = canonicalUrl;
    this.canGC = repoManager instanceof LocalDiskRepositoryManager;
    this.garbageCollectionFactory = garbageCollectionFactory;
  }

  @Override
  public Object apply(ProjectResource rsrc, Input input) {
    Project.NameKey project = rsrc.getNameKey();
    if (input.async) {
      return applyAsync(project, input);
    }
    return applySync(project, input);
  }

  private Response.Accepted applyAsync(final Project.NameKey project, final Input input) {
    Runnable job =
        new Runnable() {
          @Override
          public void run() {
            runGC(project, input, null);
          }

          @Override
          public String toString() {
            return "Run "
                + (input.aggressive ? "aggressive " : "")
                + "garbage collection on project "
                + project.get();
          }
        };

    @SuppressWarnings("unchecked")
    WorkQueue.Task<Void> task = (WorkQueue.Task<Void>) workQueue.getDefaultQueue().submit(job);

    String location =
        canonicalUrl.get() + "a/config/server/tasks/" + IdGenerator.format(task.getTaskId());

    return Response.accepted(location);
  }

  @SuppressWarnings("resource")
  private BinaryResult applySync(final Project.NameKey project, final Input input) {
    return new BinaryResult() {
      @Override
      public void writeTo(OutputStream out) throws IOException {
        PrintWriter writer =
            new PrintWriter(new OutputStreamWriter(out, UTF_8)) {
              @Override
              public void println() {
                write('\n');
              }
            };
        try {
          PrintWriter progressWriter = input.showProgress ? writer : null;
          GarbageCollectionResult result = runGC(project, input, progressWriter);
          String msg = "Garbage collection completed successfully.";
          if (result.hasErrors()) {
            for (GarbageCollectionResult.Error e : result.getErrors()) {
              switch (e.getType()) {
                case REPOSITORY_NOT_FOUND:
                  msg = "Error: project \"" + e.getProjectName() + "\" not found.";
                  break;
                case GC_ALREADY_SCHEDULED:
                  msg =
                      "Error: garbage collection for project \""
                          + e.getProjectName()
                          + "\" was already scheduled.";
                  break;
                case GC_FAILED:
                  msg =
                      "Error: garbage collection for project \""
                          + e.getProjectName()
                          + "\" failed.";
                  break;
                default:
                  msg =
                      "Error: garbage collection for project \""
                          + e.getProjectName()
                          + "\" failed: "
                          + e.getType()
                          + ".";
              }
            }
          }
          writer.println(msg);
        } finally {
          writer.flush();
        }
      }
    }.setContentType("text/plain").setCharacterEncoding(UTF_8).disableGzip();
  }

  GarbageCollectionResult runGC(Project.NameKey project, Input input, PrintWriter progressWriter) {
    return garbageCollectionFactory
        .create()
        .run(Collections.singletonList(project), input.aggressive, progressWriter);
  }

  @Override
  public UiAction.Description getDescription(ProjectResource rsrc) {
    return new UiAction.Description()
        .setLabel("Run GC")
        .setTitle("Triggers the Git Garbage Collection for this project.")
        .setVisible(canGC);
  }
}

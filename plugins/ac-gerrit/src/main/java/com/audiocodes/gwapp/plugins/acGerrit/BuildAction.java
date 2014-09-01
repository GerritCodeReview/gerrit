package com.audiocodes.gwapp.plugins.acGerrit;

import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.change.RevisionResource;
import java.util.Arrays;
import java.util.List;

class BuildAction
    implements UiAction<RevisionResource>, RestModifyView<RevisionResource, BuildAction.Input> {

  static List<String> buildableProjects = Arrays.asList("GWApp", "TrunkPackRam", "TP/GWApp");

  static class Input {}

  @Override
  public Response<String> apply(RevisionResource rev, Input input) {
    return Response.ok("");
  }

  @Override
  public Description getDescription(RevisionResource rev) {
    final String project = rev.getChange().getProject().get();
    if (!buildableProjects.contains(project)
        && (!project.contains("TP/OpenSource") || project.contains("u-boot"))) return null;
    Description button = new Description().setLabel("Build");
    if (rev.getPatchSet().id().get() == 0) {
      button.setTitle("Edit patch set cannot be built. Publish it first.");
      button.setEnabled(false);
    } else {
      button.setTitle("Build this change using Jenkins");
    }
    return button;
  }
}

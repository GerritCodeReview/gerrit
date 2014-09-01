package com.audiocodes.gwapp.plugins.acGerrit;

import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.change.RevisionResource;
import java.util.Arrays;
import java.util.List;

class FullSanityAction
    implements UiAction<RevisionResource>,
        RestModifyView<RevisionResource, FullSanityAction.Input> {

  static List<String> buildableProjectsDRT = Arrays.asList("TP/GWApp", "TP/Tools/VoiceAIConnector");

  static class Input {}

  @Override
  public Response<String> apply(RevisionResource rev, Input input) {
    return Response.ok("");
  }

  @Override
  public Description getDescription(RevisionResource rev) {
    final String project = rev.getChange().getProject().get();
    if (buildableProjectsDRT.contains(project) || project.contains("TP/OpenSource")) {
      Description button = new Description().setLabel("Sanity");
      button.setTitle("Send Full Sanity request");
      return button;
    } else {
      return null;
    }
  }
}

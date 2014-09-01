package com.audiocodes.gwapp.plugins.acGerrit;

import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;

class SanityAction
    implements UiAction<RevisionResource>, RestModifyView<RevisionResource, SanityAction.Input> {

  static List<String> buildableProjectsDRT = Arrays.asList("TP/GWApp");
  static List<String> buildableProjectsAT = Arrays.asList("IPP/SFB");

  static class Input {}

  private final ProjectCache projectCache;

  @Inject
  public SanityAction(ProjectCache projectCache) {
    this.projectCache = projectCache;
  }

  @Override
  public Response<String> apply(RevisionResource rev, Input input) {
    return Response.ok("");
  }

  private boolean hasLabel(RevisionResource rev, String label) {
    ChangeNotes notes = rev.getNotes();
    ProjectState projectState =
        projectCache
            .get(rev.getChange().getProject())
            .orElseThrow(illegalState(notes.getProjectName()));
    final LabelTypes labelTypes = projectState.getLabelTypes(notes);
    return labelTypes.byLabel(label) != null;
  }

  @Override
  public Description getDescription(RevisionResource rev) {
    final String project = rev.getChange().getProject().get();
    if (buildableProjectsDRT.contains(project) || project.contains("TP/OpenSource")) {
      if (!hasLabel(rev, "DRT")) return null;
      Description button = new Description().setLabel("DRT");
      button.setTitle("Compile boards and send DRT request");
      return button;
    } else if (buildableProjectsAT.contains(project)) {
      Description button = new Description().setLabel("Automation Test");
      button.setTitle("Send Automation Test request");
      return button;
    } else {
      return null;
    }
  }
}

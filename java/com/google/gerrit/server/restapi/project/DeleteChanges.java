package com.google.gerrit.server.restapi.project;

import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.api.projects.DeleteChangeInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.change.ChangeFinder;
import com.google.gerrit.server.change.DeleteChangeOp;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeleteChanges implements RestModifyView<ProjectResource, DeleteChangeInput> {
  private static final String FAILURE = "FAILURE";
  private static final String SUCCESS = "SUCCESS";
  private static final String NOT_UNIQUE = "NOT_UNIQUE";

  private final PermissionBackend permissionBackend;
  private final ChangeFinder changeFinder;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final DeleteChangeOp.Factory opFactory;

  @Inject
  public DeleteChanges(
      PermissionBackend permissionBackend,
      ChangeFinder changeFinder,
      BatchUpdate.Factory batchUpdateFactory,
      DeleteChangeOp.Factory opFactory) {

    this.permissionBackend = permissionBackend;
    this.changeFinder = changeFinder;
    this.batchUpdateFactory = batchUpdateFactory;
    this.opFactory = opFactory;
  }

  @Override
  public Response<?> apply(ProjectResource resource, DeleteChangeInput input) throws Exception {
    if (input == null || input.changes == null || input.changes.isEmpty()) {
      throw new BadRequestException("Change Ids must be specified");
    }
    Map<String, List<String>> responseBody = new HashMap<>();

    try (BatchUpdate bu =
        batchUpdateFactory.create(resource.getNameKey(), resource.getUser(), TimeUtil.now())) {
      for (String change : input.changes) {
        List<ChangeNotes> cn = changeFinder.find(change);
        if (cn.isEmpty()) {
          constructResponse(FAILURE, change, responseBody);
        } else if (cn.size() > 1) {
          constructResponse(NOT_UNIQUE, change, responseBody);
        } else {
          checkPermissions(cn);
          if (isChangeDeletable(cn.getFirst())) {
            Change.Id changeId = cn.getFirst().getChange().getId();
            bu.addOp(changeId, opFactory.create(changeId));
            constructResponse(SUCCESS, change, responseBody);
          }
        }
      }
      bu.execute();
    }
    return Response.ok(responseBody);
  }

  private void constructResponse(
      String status, String change, Map<String, List<String>> responseMsg) {
    switch (status) {
      case SUCCESS:
        responseMsg.computeIfAbsent(SUCCESS, k -> new ArrayList<>()).add(change);
        break;
      case FAILURE:
        responseMsg.computeIfAbsent(FAILURE, k -> new ArrayList<>()).add(change);
        break;
      case NOT_UNIQUE:
        responseMsg.computeIfAbsent(NOT_UNIQUE, k -> new ArrayList<>()).add(change);
        break;
    }
  }

  public void checkPermissions(List<ChangeNotes> cn)
      throws PermissionBackendException, AuthException {
    if (cn.getFirst() != null) {
      permissionBackend.currentUser().change(cn.getFirst()).check(ChangePermission.DELETE);
    }
  }

  private static boolean isChangeDeletable(ChangeNotes cn) {
    // Merged changes should never be deleted.
    return !cn.getChange().isMerged();
    // New or abandoned changes can be deleted with the right permissions.
  }
}

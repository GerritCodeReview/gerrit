package com.google.gerrit.server.restapi.change;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.change.FileResource;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;

/** Computes the ported comments for a specific file path. */
@Singleton
public class GetPortedComments implements RestReadView<FileResource> {
  private final CommentsUtil commentsUtil;
  private final CommentPorter commentPorter;
  private final Provider<CommentJson> commentJson;

  @Inject
  public GetPortedComments(
      Provider<CommentJson> commentJson, CommentsUtil commentsUtil, CommentPorter commentPorter) {
    this.commentJson = commentJson;
    this.commentsUtil = commentsUtil;
    this.commentPorter = commentPorter;
  }

  @Override
  public Response<Map<String, List<CommentInfo>>> apply(FileResource fileResource)
      throws PermissionBackendException {
    PatchSet targetPatchset = fileResource.getRevision().getPatchSet();
    ChangeNotes notes = fileResource.getRevision().getNotes();

    List<HumanComment> allComments = commentsUtil.publishedHumanCommentsByChange(notes);

    // Filter comments for a specific file path
    allComments =
        allComments.stream()
            .filter(c -> c.key.filename.equals(fileResource.getPatchKey().fileName()))
            .collect(toImmutableList());

    ImmutableList<HumanComment> portedComments =
        commentPorter.portComments(
            notes, targetPatchset, allComments, ImmutableList.of(new UnresolvedCommentFilter()));
    return Response.ok(format(portedComments));
  }

  private Map<String, List<CommentInfo>> format(List<HumanComment> comments)
      throws PermissionBackendException {
    return commentJson
        .get()
        .setFillAccounts(true)
        .setFillPatchSet(true)
        .newHumanCommentFormatter()
        .format(comments);
  }
}

package com.google.gerrit.server.schema;

public class Schema_186 implements NoteDbSchemaVersion {
  @Override
  public void upgrade(NoteDbSchemaVersion.Arguments args, UpdateUI ui) throws Exception {
    new GrantReviewPermission(
        args.repoManager, args.projectConfigFactory, args.systemGroupBackend, args.serverUser)
        .execute(args.allProjects);
  }
}

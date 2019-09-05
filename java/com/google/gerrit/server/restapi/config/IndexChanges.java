package com.google.gerrit.server.restapi.config;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.ChangeFinder;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.restapi.config.IndexChanges.Input;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Set;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@Singleton
public class IndexChanges implements RestModifyView<ConfigResource, Input> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class Input {
    Set<String> changes;
  }

  private final ChangeFinder changeFinder;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final ChangeData.Factory changeDataFactory;
  private final ChangeIndexer indexer;

  @Inject
  IndexChanges(
      ChangeFinder changeFinder,
      SchemaFactory<ReviewDb> schemaFactory,
      ChangeData.Factory changeDataFactory,
      ChangeIndexer indexer) {
    this.changeFinder = changeFinder;
    this.schemaFactory = schemaFactory;
    this.changeDataFactory = changeDataFactory;
    this.indexer = indexer;
  }

  @Override
  public Object apply(ConfigResource resource, Input input) {
    System.out.println("input = " + (input.changes != null ? input.changes : "NULL"));
    try (ReviewDb db = schemaFactory.open()) {
      for (String id : input.changes) {
        for (ChangeNotes n : changeFinder.find(id)) {
          try {
            indexer.index(changeDataFactory.create(db, n));
            logger.atFine().log("Indexed change %s", id);
          } catch (IOException e) {
            logger.atSevere().withCause(e).log("Failed to index change %s", id);
          }
        }
      }
    } catch (OrmException e) {
      logger.atSevere().withCause(e).log("Couldn't index changes %s", input.changes);
    }
    return Response.accepted("Changes " + input + " accepted for indexing");
  }
}

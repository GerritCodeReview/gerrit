// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.gerrit.pgm;

import static com.google.gerrit.server.schema.DataSourceProvider.Context.MULTI_USER;

import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.util.PrologJar;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Injector;

import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Option;

import java.util.List;

/**
 * Gets rules.pl at refs/meta/config and compiles into jar file called
 * rules-(sha1 of rules.pl).jar in (site-path)/cache/rules
 */
public class Rulec extends SiteProgram {
  @Option(name = "--name", usage = "Project name")
  private String projectName = null;

  private Injector dbInjector;
  private final LifecycleManager manager = new LifecycleManager();

  @Inject
  private GitRepositoryManager gitManager;

  @Inject
  private SchemaFactory<ReviewDb> database;

  @Override
  public int run() throws Exception {
    dbInjector = createDbInjector(MULTI_USER);
    manager.add(dbInjector);
    manager.start();
    dbInjector.injectMembers(this);

    Project.NameKey project = getNameKey();
    Repository git = gitManager.openRepository(project);

    PrologJar jarMaker = new PrologJar(getSitePath(), git);
    boolean success = jarMaker.run();

    return 0;
  }

  private Project.NameKey getNameKey() throws OrmException{
    if (projectName != null) {
      return new Project.NameKey(projectName);
    }
    //open database to get change to get project namekey to get objectid of rules.pl
    ReviewDb db = database.open();
    List<Change> changes = db.changes().all().toList();
    Change change = changes.get(0);
    db.close();
    return change.getDest().getParentKey();
  }
}
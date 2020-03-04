
package com.google.gerrit.acceptance.ssh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;

import org.junit.Test;

import com.google.common.base.Joiner;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.server.index.VersionManager;
import com.google.inject.Inject;

@UseLocalDisk
public class AbstractOnDiskIndexTests extends AbstractIndexTests {
	  @Inject private VersionManager versionManager;

	  @Test
	  public void indexStart() throws Exception {
	    configureIndex(server.getTestInjector());

	    String[] indexes = {"groups", "accounts", "changes", "projects"};
	    for (String index : indexes) {
	      String cmd = Joiner.on(" ").join("gerrit", "index", "start", index);
	      adminSshSession.exec(cmd);
	      adminSshSession.assertSuccess();

	      versionManager.setLowestIndex(index);
	      Path sitePath = server.getSitePath();
	      String result = adminSshSession.exec(cmd);
	      adminSshSession.assertSuccess();

	      assertEquals(sitePath, server.getSitePath());
	      assertEquals("Reindexer started", result.trim());

	      boolean reindexing = true;
	      while (reindexing) {
	        adminSshSession.exec(cmd);
	        if (adminSshSession.getError() != null) {
	          assertTrue(
	              adminSshSession
	                  .getError()
	                  .trim()
	                  .equals("fatal: Failed to start reindexer: Reindexer is already running."));
	        } else {
	          reindexing = false;
	        }
	      }
	      adminSshSession.assertSuccess();

	      result = adminSshSession.exec(cmd);
	      adminSshSession.assertSuccess();
	      assertEquals("Nothing to reindex, index is already the latest version", result.trim());

	      adminSshSession.exec(cmd + "random");
	      adminSshSession.assertFailure();
	    }
	  }

	  @Test
	  public void indexActivate() throws Exception {
	    configureIndex(server.getTestInjector());

	    String[] indexes = {"groups", "accounts", "changes"};
	    for (String index : indexes) {
	      String cmd = Joiner.on(" ").join("gerrit", "index", "activate", index);
	      adminSshSession.exec(cmd);
	      adminSshSession.assertSuccess();

	      adminSshSession.exec(cmd + "random");
	      adminSshSession.assertFailure();

	      versionManager.setLowestIndex(index);
	      versionManager.startIndex(index);
	      String result = adminSshSession.exec(cmd);
	      adminSshSession.assertSuccess();
	      assertTrue(result.trim().contains("Activated latest index"));
	    }
	  }

}

package com.google.gerrit.httpd;

import com.google.gerrit.common.PageLinks;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.restapi.change.ChangesCollection;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Redirects {@code domain.tld/123} to {@code domain.tld/c/project/+/123}. */
@Singleton
public class NumericChangeIdRedirectServlet extends HttpServlet {
  private final ChangesCollection changesCollection;

  @Inject
  NumericChangeIdRedirectServlet(ChangesCollection changesCollection) {
    this.changesCollection = changesCollection;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    String idString = req.getPathInfo();
    if (idString.endsWith("/")) {
      idString = idString.substring(0, idString.length() - 1);
    }
    Change.Id id;
    try {
      id = Change.Id.parse(idString);
    } catch (IllegalArgumentException e) {
      rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    ChangeResource changeResource;
    try {
      changeResource = changesCollection.parse(id);
    } catch (ResourceConflictException | ResourceNotFoundException e) {
      rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    } catch (OrmException | PermissionBackendException e) {
      throw new IOException("Unable to lookup change", e);
    }
    String path =
        PageLinks.toChange(changeResource.getProject(), changeResource.getChange().getId());
    UrlModule.toGerrit(path, req, rsp);
  }
}

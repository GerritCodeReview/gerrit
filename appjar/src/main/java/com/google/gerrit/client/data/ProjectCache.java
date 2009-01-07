// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.client.data;

import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.Common;
import com.google.gwtorm.client.OrmException;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Cache of project information, including access rights. */
public class ProjectCache {
  private final LinkedHashMap<Project.Id, Entry> byId =
      new LinkedHashMap<Project.Id, Entry>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(
            final Map.Entry<Project.Id, ProjectCache.Entry> eldest) {
          return 1024 <= size();
        }
      };
  private final LinkedHashMap<Project.NameKey, Entry> byName =
      new LinkedHashMap<Project.NameKey, Entry>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(
            final Map.Entry<Project.NameKey, ProjectCache.Entry> eldest) {
          return 1024 <= size();
        }
      };
  private Collection<ProjectRight> wildcardRights;

  /** Get the rights which are applied to all projects in the system. */
  public Collection<ProjectRight> getWildcardRights() {
    synchronized (this) {
      if (wildcardRights != null) {
        return wildcardRights;
      }
    }

    Collection<ProjectRight> m;
    try {
      final ReviewDb db = Common.getSchemaFactory().open();
      try {
        m =
            Collections.unmodifiableCollection(db.projectRights().byProject(
                ProjectRight.WILD_PROJECT).toList());
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      return null;
    }

    synchronized (this) {
      wildcardRights = m;
    }
    return m;
  }

  /** Invalidate the cached information about the given project. */
  public void invalidate(final Project p) {
    if (p != null) {
      synchronized (byName) {
        byName.remove(p.getNameKey());
      }
      synchronized (byId) {
        byId.remove(p.getId());
      }
    }
  }

  /** Invalidate the cached information about the given project. */
  public void invalidate(final Project.Id projectId) {
    if (projectId != null) {
      final Entry e;
      synchronized (byId) {
        e = byId.remove(projectId);
      }
      if (e != null) {
        synchronized (byName) {
          byName.remove(e.getProject().getNameKey());
        }
      }
    }
  }

  /**
   * Get the cached data for a project by its unique id.
   * 
   * @param projectId id of the project.
   * @return the cached data; null if no such project exists.
   */
  public Entry get(final Project.Id projectId) {
    if (projectId == null) {
      return null;
    }

    Entry m;
    synchronized (byId) {
      m = byId.get(projectId);
    }
    if (m != null) {
      return m;
    }

    try {
      final ReviewDb db = Common.getSchemaFactory().open();
      try {
        final Project p = db.projects().get(projectId);
        if (p == null) {
          return null;
        }
        m = new Entry(db, p);
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      return null;
    }

    synchronized (byName) {
      byName.put(m.getProject().getNameKey(), m);
    }
    synchronized (byId) {
      byId.put(m.getProject().getId(), m);
    }
    return m;
  }

  /**
   * Get the cached data for a project by its unique name.
   * 
   * @param projectName name of the project.
   * @return the cached data; null if no such project exists.
   */
  public Entry get(final Project.NameKey projectName) {
    if (projectName == null) {
      return null;
    }

    Entry m;
    synchronized (byName) {
      m = byName.get(projectName);
    }
    if (m != null) {
      return m;
    }

    try {
      final ReviewDb db = Common.getSchemaFactory().open();
      try {
        final Project p = db.projects().get(projectName);
        if (p == null) {
          return null;
        }
        m = new Entry(db, p);
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      return null;
    }

    synchronized (byName) {
      byName.put(m.getProject().getNameKey(), m);
    }
    synchronized (byId) {
      byId.put(m.getProject().getId(), m);
    }
    return m;
  }

  /** Force the entire cache to flush from memory and recompute. */
  public void flush() {
    synchronized (this) {
      wildcardRights = null;
    }
    synchronized (byId) {
      byId.clear();
    }
    synchronized (byName) {
      byName.clear();
    }
  }

  /** Cached information on a project. */
  public static class Entry {
    private final Project project;
    private final Collection<ProjectRight> rights;

    protected Entry(final ReviewDb db, final Project p) throws OrmException {
      project = p;
      rights =
          Collections.unmodifiableCollection(db.projectRights().byProject(
              project.getId()).toList());
    }

    public Project getProject() {
      return project;
    }

    public Collection<ProjectRight> getRights() {
      return rights;
    }
  }
}

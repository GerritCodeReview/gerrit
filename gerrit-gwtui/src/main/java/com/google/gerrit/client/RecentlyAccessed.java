// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.client;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.Cookies;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class RecentlyAccessed implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final String RECENTLY_ACCESSED_COOKIE = "recently-accessed";
  private static final String PROJECTS_KEY = "projects:";
  private static RecentlyAccessed instance;

  private final LinkedList<Project.NameKey> projects;

  public static RecentlyAccessed get() {
    if (instance == null) {
      instance = load();
    }
    return instance;
  }

  private static RecentlyAccessed load() {
    final LinkedList<Project.NameKey> projects =
        new LinkedList<Project.NameKey>();
    final String s = Cookies.getCookie(RECENTLY_ACCESSED_COOKIE);
    if (s != null && s.startsWith(PROJECTS_KEY)) {
      final String[] projectNames =
          s.substring(PROJECTS_KEY.length()).split("\\|");
      for (final String p : projectNames) {
        projects.add(new Project.NameKey(URL.decode(p)));
      }
    }
    return new RecentlyAccessed(projects);
  }

  private RecentlyAccessed(final LinkedList<Project.NameKey> projects) {
    this.projects = projects;
  }

  /**
   * Adds a project that was recently accessed.
   *
   * @param project the name of the project that was recently accessed
   */
  public void add(final Project.NameKey project) {
    projects.remove(project);
    projects.addFirst(project);
    limit();
    save();
  }

  /**
   * Returns the projects that were recently accessed.
   */
  public List<Project.NameKey> getProjects() {
    if (limit()) {
      save();
    }
    return new ArrayList<Project.NameKey>(projects);
  }

  /**
   * If there are more recently accessed objects stored than the given
   * maxEntries, remove the oldest entries so that maxEntries stay stored.
   *
   * @param maxEntries maximum number of entries that should stay
   * @return <code>true</code> if any entries were removed, otherwise
   *         <code>false</code>
   */
  private boolean limit() {
    if (Gerrit.isSignedIn()) {
      final int size = projects.size();
      while (projects.size() > Gerrit.getUserAccount().getGeneralPreferences()
          .getMaxRecentEntries()) {
        projects.removeLast();
      }
      return projects.size() != size;
    }
    return false;
  }

  private void save() {
    final StringBuilder b = new StringBuilder();
    b.append(PROJECTS_KEY);
    for (final Project.NameKey p : projects) {
      b.append(URL.encode(p.get()));
      b.append("|");
    }
    Cookies.setCookie(RECENTLY_ACCESSED_COOKIE, b.toString());
  }
}

// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.common.data;

/** Class to store information about different gitweb types.  */
public class GitWebType {
    /** Nice name for this GitWebType. */
    private final String name;

    /** String for revision view url. */
    private String revision;

    /** ParamertizedString for project view url. */
    private String project;

    /** ParamertizedString for branch view url. */
    private String branch;

    /**
     * Private default constructor for gson.
     */
    protected GitWebType() {
        this("gson", "", "", "");
    }

    /**
     * Create a new GitWebType
     *
     * @param name Name for this type.
     * @param revision Paramertized String to use for revision view.
     * @param project Paramertized String to use for project view.
     * @param branch  Paramertized String to use for branch view.
     */
    private GitWebType(final String name, final String revision, final String project, final String branch) {
        this.name = name;
        setBranch(branch);
        setProject(project);
        setRevision(revision);
    }

    /**
     * Get the name for this type.
     *
     * @return Name for this type.
     */
    public String getName() {
        return name;
    }

    /**
     * Get the String for branch view.
     *
     * @return The String for branch view
     */
    public String getBranch() {
        return branch;
    }

    /**
     * Get the String for project view.
     *
     * @return The String for project view
     */
    public String getProject() {
        return project;
    }

    /**
     * Get the String for revision view.
     *
     * @return The String for revision view
     */
    public String getRevision() {
        return revision;
    }

    /**
     * Set the pattern for branch view.
     *
     * @param pattern The pattern for branch view
     */
    public void setBranch(final String pattern) {
        if (pattern != null && !pattern.isEmpty()) {
            branch = pattern;
        }
    }

    /**
     * Set the pattern for project view.
     *
     * @param pattern The pattern for project view
     */
    public void setProject(final String pattern) {
        if (pattern != null && !pattern.isEmpty()) {
            project = pattern;
        }
    }

    /**
     * Set the pattern for revision view.
     *
     * @param pattern The pattern for revision view
     */
    public void setRevision(final String pattern) {
        if (pattern != null && !pattern.isEmpty()) {
            revision = pattern;
        }
    }

     /**
     * Get a GitWebType based on the given name.
     *
     * @param name Name to look for.
     * @return GitWebType from teh given name, else null if not found.
     */
    public static GitWebType fromName(final String name) {
        if (name == null || name.isEmpty() || name.equalsIgnoreCase("gitweb")) {
            return new GitWebType("GitWeb", "?p=${project}.git;a=commit;h=${commit}", "?p=${project}.git;a=summary", "?p=${project}.git;a=shortlog;h=${branch}");
        } else if (name.equalsIgnoreCase("cgit")) {
            return new GitWebType("CGit", "${project}/commit/?id=${commit}", "${project}/summary", "${project}/log/?h=${branch}");
        } else if (name.equalsIgnoreCase("custom")) {
            return new GitWebType("Custom", "", "", "");
        }

        return null;
    }
}

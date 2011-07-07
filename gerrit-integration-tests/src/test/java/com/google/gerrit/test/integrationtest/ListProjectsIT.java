// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.test.integrationtest;

import static org.junit.Assert.assertEquals;

import com.jcraft.jsch.JSchException;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

public class ListProjectsIT extends AbstractIntegrationTest{

  @BeforeClass
  public static void createProjects() throws JSchException {
    ssh.createProject("project3", false);
    ssh.createProject("project2", false);
    ssh.createProject("project1", false);
  }

  @Test
  public void testSshListAllProjects() throws JSchException {
    final List<String> projects = ssh.listProjects();
    assertEquals(3, projects.size());
    assertEquals("project1", projects.get(0));
    assertEquals("project2", projects.get(1));
    assertEquals("project3", projects.get(2));
  }

  @Test
  public void testSshListProjectsAsTree() throws JSchException {
    final List<String> projects = ssh.listProjectsAsTree();
    assertEquals(4, projects.size());
    assertEquals("`-- All-Projects", projects.get(0));
    assertEquals("    |-- project1", projects.get(1));
    assertEquals("    |-- project2", projects.get(2));
    assertEquals("    `-- project3", projects.get(3));
  }
}

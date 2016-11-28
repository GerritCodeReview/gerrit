// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.project;

import static com.google.gerrit.server.ioutil.BasicSerialization.readString;
import static com.google.gerrit.server.ioutil.BasicSerialization.readVarInt32;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeString;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeVarInt32;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

class ProjectNamesList implements Serializable {

  private SortedSet<Project.NameKey> names;

  ProjectNamesList(SortedSet<NameKey> names) {
    this.names = Collections.unmodifiableSortedSet(names);
  }

  SortedSet<Project.NameKey> get() {
    return names;
  }

  ProjectNamesList add(Project.NameKey name) {
    TreeSet<Project.NameKey> n = new TreeSet<>(names);
    n.add(name);
    return new ProjectNamesList(n);
  }

  ProjectNamesList remove(Project.NameKey name) {
    TreeSet<Project.NameKey> n = new TreeSet<>(names);
    n.remove(name);
    return new ProjectNamesList(n);
  }

  private void writeObject(ObjectOutputStream output) throws IOException {
    writeVarInt32(output, names.size());
    try (DeflaterOutputStream out = new DeflaterOutputStream(output)) {
      for (Project.NameKey n : names) {
        writeString(out, n.get());
      }
    }
  }

  private void readObject(ObjectInputStream input) throws IOException {
    int size = readVarInt32(input);
    ArrayList<Project.NameKey> list = new ArrayList<>(size);
    try (InflaterInputStream in = new InflaterInputStream(input)) {
      for (int i = 0; i < size; i++) {
        list.add(new Project.NameKey(readString(in)));
      }
    }
    names = Collections.unmodifiableSortedSet(new TreeSet<>(list));
  }
}

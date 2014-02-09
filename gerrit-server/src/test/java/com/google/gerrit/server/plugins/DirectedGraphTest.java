// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.plugins;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class DirectedGraphTest {

  static class Node {
    public String name;
    Node(String n) {
      name = n;
    }
    @Override
    public String toString() {
      return name;
    }
  }

  @Test
  public void sort() throws DAGCycleException {
    DirectedGraph<Node> g = new DirectedGraph<>();
    Node seven = addNode(g, "7");
    Node five = addNode(g, "5");
    Node three = addNode(g, "3");
    Node eleven = addNode(g, "11");
    Node eight = addNode(g, "8");
    Node two = addNode(g, "2");
    Node nine = addNode(g, "9");
    Node ten = addNode(g, "10");

    g.addEdge(five, three);
    g.addEdge(three, seven);
    g.addEdge(seven, eleven);
    g.addEdge(seven, eight);
    g.addEdge(five, eleven);
    g.addEdge(three, eight);
    g.addEdge(three, ten);
    g.addEdge(eleven, two);
    g.addEdge(two, eight);
    g.addEdge(eleven, nine);
    g.addEdge(eleven, ten);
    g.addEdge(eight, nine);
    g.addEdge(eight, ten);
    g.addEdge(nine, ten);

    assertArrayEquals(new Node[] {five, three, seven, eleven, two, eight, nine,
        ten}, TopologicalSort.sort(g).toArray());
  }

  @Test(expected = DAGCycleException.class)
  public void sort_DAGhasCycle() throws DAGCycleException {
    DirectedGraph<Node> g = new DirectedGraph<>();
    Node three = addNode(g, "3");
    Node five = addNode(g, "5");
    Node seven = addNode(g, "7");

    g.addEdge(three, five);
    g.addEdge(five, seven);
    g.addEdge(seven, three);

    TopologicalSort.sort(g);
  }

  private static Node addNode(DirectedGraph<Node> g, String n) {
    Node node = new Node(n);
    g.addNode(node);
    return node;
  }
}

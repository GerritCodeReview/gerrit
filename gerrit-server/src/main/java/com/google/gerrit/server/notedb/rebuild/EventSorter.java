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

package com.google.gerrit.server.notedb.rebuild;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.SetMultimap;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Helper to sort a list of events.
 *
 * <p>Events are sorted in two passes:
 *
 * <ol>
 *   <li>Sort by natural order (timestamp, patch set, author, etc.)
 *   <li>Postpone any events with dependencies to occur only after all of their dependencies, where
 *       this violates natural order.
 * </ol>
 *
 * {@link #sort()} modifies the event list in place (similar to {@link Collections#sort(List)}), but
 * does not modify any event. In particular, events might end up out of order with respect to
 * timestamp; callers are responsible for adjusting timestamps later if they prefer monotonicity.
 */
class EventSorter {
  private final List<Event> out;
  private final LinkedHashSet<Event> sorted;
  private ListMultimap<Event, Event> waiting;
  private SetMultimap<Event, Event> deps;

  EventSorter(List<Event> events) {
    LinkedHashSet<Event> all = new LinkedHashSet<>(events);
    out = events;

    for (Event e : events) {
      for (Event d : e.deps) {
        checkArgument(all.contains(d), "dep %s of %s not in input list", d, e);
      }
    }

    all.clear();
    sorted = all; // Presized.
  }

  void sort() {
    // First pass: sort by natural order.
    PriorityQueue<Event> todo = new PriorityQueue<>(out);

    // Populate waiting map after initial sort to preserve natural order.
    waiting = ArrayListMultimap.create();
    deps = HashMultimap.create();
    for (Event e : todo) {
      for (Event d : e.deps) {
        deps.put(e, d);
        waiting.put(d, e);
      }
    }

    // Second pass: enforce dependencies.
    int size = out.size();
    while (!todo.isEmpty()) {
      process(todo.remove(), todo);
    }
    checkState(
        sorted.size() == size, "event sort expected %s elements, got %s", size, sorted.size());

    // Modify out in-place a la Collections#sort.
    out.clear();
    out.addAll(sorted);
  }

  void process(Event e, PriorityQueue<Event> todo) {
    if (sorted.contains(e)) {
      return; // Already emitted.
    }
    if (!deps.get(e).isEmpty()) {
      // Not all events that e depends on have been emitted yet. Ignore e for
      // now; it will get added back to the queue in the block below once its
      // last dependency is processed.
      return;
    }

    // All events that e depends on have been emitted, so e can be emitted.
    sorted.add(e);

    // Remove e from the dependency set of all events waiting on e, and add
    // those events back to the queue in the original priority order for
    // reconsideration.
    for (Event w : waiting.get(e)) {
      deps.get(w).remove(e);
      todo.add(w);
    }
  }
}

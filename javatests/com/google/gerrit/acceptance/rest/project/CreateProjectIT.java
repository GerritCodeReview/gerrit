// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpStatus;
import org.junit.Test;

public class CreateProjectIT extends AbstractDaemonTest {
  @Test
  public void createSameProjectFromTenConcurrentRequests1() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(10);
    try {
      for (int i = 0; i < 10; i++) {
        String newProjectName = name("foo" + i);
        CyclicBarrier sync = new CyclicBarrier(10);
        Callable<RestResponse> createProjectFoo =
            () -> {
              sync.await();
              return adminRestSession.put("/projects/" + newProjectName);
            };

        Future<RestResponse> r1 = executor.submit(createProjectFoo);
        Future<RestResponse> r2 = executor.submit(createProjectFoo);
        Future<RestResponse> r3 = executor.submit(createProjectFoo);
        Future<RestResponse> r4 = executor.submit(createProjectFoo);
        Future<RestResponse> r5 = executor.submit(createProjectFoo);
        Future<RestResponse> r6 = executor.submit(createProjectFoo);
        Future<RestResponse> r7 = executor.submit(createProjectFoo);
        Future<RestResponse> r8 = executor.submit(createProjectFoo);
        Future<RestResponse> r9 = executor.submit(createProjectFoo);
        Future<RestResponse> r10 = executor.submit(createProjectFoo);
        assertThat(
                ImmutableList.of(
                    r1.get().getStatusCode(),
                    r2.get().getStatusCode(),
                    r3.get().getStatusCode(),
                    r4.get().getStatusCode(),
                    r5.get().getStatusCode(),
                    r6.get().getStatusCode(),
                    r7.get().getStatusCode(),
                    r8.get().getStatusCode(),
                    r9.get().getStatusCode(),
                    r10.get().getStatusCode()))
            .containsAllOf(HttpStatus.SC_CREATED, HttpStatus.SC_CONFLICT);
      }
    } finally {
      executor.shutdown();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  public void createSameProjectFromTenConcurrentRequests2() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(10);
    try {
      for (int i = 0; i < 100; i++) {
        String newProjectName = name("foo" + i);
        CyclicBarrier sync = new CyclicBarrier(10);
        Callable<RestResponse> createProjectFoo =
            () -> {
              sync.await();
              return adminRestSession.put("/projects/" + newProjectName);
            };

        Future<RestResponse> r1 = executor.submit(createProjectFoo);
        Future<RestResponse> r2 = executor.submit(createProjectFoo);
        Future<RestResponse> r3 = executor.submit(createProjectFoo);
        Future<RestResponse> r4 = executor.submit(createProjectFoo);
        Future<RestResponse> r5 = executor.submit(createProjectFoo);
        Future<RestResponse> r6 = executor.submit(createProjectFoo);
        Future<RestResponse> r7 = executor.submit(createProjectFoo);
        Future<RestResponse> r8 = executor.submit(createProjectFoo);
        Future<RestResponse> r9 = executor.submit(createProjectFoo);
        Future<RestResponse> r10 = executor.submit(createProjectFoo);
        assertThat(
                ImmutableList.of(
                    r1.get().getStatusCode(),
                    r2.get().getStatusCode(),
                    r3.get().getStatusCode(),
                    r4.get().getStatusCode(),
                    r5.get().getStatusCode(),
                    r6.get().getStatusCode(),
                    r7.get().getStatusCode(),
                    r8.get().getStatusCode(),
                    r9.get().getStatusCode(),
                    r10.get().getStatusCode()))
            .containsAllOf(HttpStatus.SC_CREATED, HttpStatus.SC_CONFLICT);
      }
    } finally {
      executor.shutdown();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  public void createSameProjectFromTenConcurrentRequests3() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(10);
    try {
      for (int i = 0; i < 1000; i++) {
        String newProjectName = name("foo" + i);
        CyclicBarrier sync = new CyclicBarrier(1);
        Callable<RestResponse> createProjectFoo =
            () -> {
              sync.await();
              return adminRestSession.put("/projects/" + newProjectName);
            };

        Future<RestResponse> r1 = executor.submit(createProjectFoo);
        assertThat(r1.get().getStatusCode()).isEqualTo(HttpStatus.SC_CREATED);
      }
    } finally {
      executor.shutdown();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  public void createSameProjectFromTenConcurrentRequests4() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(10);
    try {
      for (int i = 0; i < 10; i++) {
        String newProjectName = name("foo" + i);
        CyclicBarrier sync = new CyclicBarrier(10);
        Callable<RestResponse> createProjectFoo =
            () -> {
              sync.await();
              return adminRestSession.put("/projects/" + newProjectName);
            };

        Future<RestResponse> r1 = executor.submit(createProjectFoo);
        Future<RestResponse> r2 = executor.submit(createProjectFoo);
        Future<RestResponse> r3 = executor.submit(createProjectFoo);
        Future<RestResponse> r4 = executor.submit(createProjectFoo);
        Future<RestResponse> r5 = executor.submit(createProjectFoo);
        Future<RestResponse> r6 = executor.submit(createProjectFoo);
        Future<RestResponse> r7 = executor.submit(createProjectFoo);
        Future<RestResponse> r8 = executor.submit(createProjectFoo);
        Future<RestResponse> r9 = executor.submit(createProjectFoo);
        Future<RestResponse> r10 = executor.submit(createProjectFoo);
        assertThat(
                ImmutableList.of(
                    r1.get().getStatusCode(),
                    r2.get().getStatusCode(),
                    r3.get().getStatusCode(),
                    r4.get().getStatusCode(),
                    r5.get().getStatusCode(),
                    r6.get().getStatusCode(),
                    r7.get().getStatusCode(),
                    r8.get().getStatusCode(),
                    r9.get().getStatusCode(),
                    r10.get().getStatusCode()))
            .containsAllOf(HttpStatus.SC_CREATED, HttpStatus.SC_CONFLICT);
      }
    } finally {
      executor.shutdown();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  public void createSameProjectFromTenConcurrentRequests5() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(10);
    try {
      for (int i = 0; i < 10; i++) {
        String newProjectName = name("foo" + i);
        CyclicBarrier sync = new CyclicBarrier(10);
        Callable<RestResponse> createProjectFoo =
            () -> {
              sync.await();
              return adminRestSession.put("/projects/" + newProjectName);
            };

        Future<RestResponse> r1 = executor.submit(createProjectFoo);
        Future<RestResponse> r2 = executor.submit(createProjectFoo);
        Future<RestResponse> r3 = executor.submit(createProjectFoo);
        Future<RestResponse> r4 = executor.submit(createProjectFoo);
        Future<RestResponse> r5 = executor.submit(createProjectFoo);
        Future<RestResponse> r6 = executor.submit(createProjectFoo);
        Future<RestResponse> r7 = executor.submit(createProjectFoo);
        Future<RestResponse> r8 = executor.submit(createProjectFoo);
        Future<RestResponse> r9 = executor.submit(createProjectFoo);
        Future<RestResponse> r10 = executor.submit(createProjectFoo);
        assertThat(
                ImmutableList.of(
                    r1.get().getStatusCode(),
                    r2.get().getStatusCode(),
                    r3.get().getStatusCode(),
                    r4.get().getStatusCode(),
                    r5.get().getStatusCode(),
                    r6.get().getStatusCode(),
                    r7.get().getStatusCode(),
                    r8.get().getStatusCode(),
                    r9.get().getStatusCode(),
                    r10.get().getStatusCode()))
            .containsAllOf(HttpStatus.SC_CREATED, HttpStatus.SC_CONFLICT);
      }
    } finally {
      executor.shutdown();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  public void createSameProjectFromTenConcurrentRequests6() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(10);
    try {
      for (int i = 0; i < 10; i++) {
        String newProjectName = name("foo" + i);
        CyclicBarrier sync = new CyclicBarrier(10);
        Callable<RestResponse> createProjectFoo =
            () -> {
              sync.await();
              return adminRestSession.put("/projects/" + newProjectName);
            };

        Future<RestResponse> r1 = executor.submit(createProjectFoo);
        Future<RestResponse> r2 = executor.submit(createProjectFoo);
        Future<RestResponse> r3 = executor.submit(createProjectFoo);
        Future<RestResponse> r4 = executor.submit(createProjectFoo);
        Future<RestResponse> r5 = executor.submit(createProjectFoo);
        Future<RestResponse> r6 = executor.submit(createProjectFoo);
        Future<RestResponse> r7 = executor.submit(createProjectFoo);
        Future<RestResponse> r8 = executor.submit(createProjectFoo);
        Future<RestResponse> r9 = executor.submit(createProjectFoo);
        Future<RestResponse> r10 = executor.submit(createProjectFoo);
        assertThat(
                ImmutableList.of(
                    r1.get().getStatusCode(),
                    r2.get().getStatusCode(),
                    r3.get().getStatusCode(),
                    r4.get().getStatusCode(),
                    r5.get().getStatusCode(),
                    r6.get().getStatusCode(),
                    r7.get().getStatusCode(),
                    r8.get().getStatusCode(),
                    r9.get().getStatusCode(),
                    r10.get().getStatusCode()))
            .containsAllOf(HttpStatus.SC_CREATED, HttpStatus.SC_CONFLICT);
      }
    } finally {
      executor.shutdown();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  public void createSameProjectFromTenConcurrentRequests7() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(10);
    try {
      for (int i = 0; i < 10; i++) {
        String newProjectName = name("foo" + i);
        CyclicBarrier sync = new CyclicBarrier(10);
        Callable<RestResponse> createProjectFoo =
            () -> {
              sync.await();
              return adminRestSession.put("/projects/" + newProjectName);
            };

        Future<RestResponse> r1 = executor.submit(createProjectFoo);
        Future<RestResponse> r2 = executor.submit(createProjectFoo);
        Future<RestResponse> r3 = executor.submit(createProjectFoo);
        Future<RestResponse> r4 = executor.submit(createProjectFoo);
        Future<RestResponse> r5 = executor.submit(createProjectFoo);
        Future<RestResponse> r6 = executor.submit(createProjectFoo);
        Future<RestResponse> r7 = executor.submit(createProjectFoo);
        Future<RestResponse> r8 = executor.submit(createProjectFoo);
        Future<RestResponse> r9 = executor.submit(createProjectFoo);
        Future<RestResponse> r10 = executor.submit(createProjectFoo);
        assertThat(
                ImmutableList.of(
                    r1.get().getStatusCode(),
                    r2.get().getStatusCode(),
                    r3.get().getStatusCode(),
                    r4.get().getStatusCode(),
                    r5.get().getStatusCode(),
                    r6.get().getStatusCode(),
                    r7.get().getStatusCode(),
                    r8.get().getStatusCode(),
                    r9.get().getStatusCode(),
                    r10.get().getStatusCode()))
            .containsAllOf(HttpStatus.SC_CREATED, HttpStatus.SC_CONFLICT);
      }
    } finally {
      executor.shutdown();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  public void createSameProjectFromTenConcurrentRequests8() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(10);
    try {
      for (int i = 0; i < 10; i++) {
        String newProjectName = name("foo" + i);
        CyclicBarrier sync = new CyclicBarrier(10);
        Callable<RestResponse> createProjectFoo =
            () -> {
              sync.await();
              return adminRestSession.put("/projects/" + newProjectName);
            };

        Future<RestResponse> r1 = executor.submit(createProjectFoo);
        Future<RestResponse> r2 = executor.submit(createProjectFoo);
        Future<RestResponse> r3 = executor.submit(createProjectFoo);
        Future<RestResponse> r4 = executor.submit(createProjectFoo);
        Future<RestResponse> r5 = executor.submit(createProjectFoo);
        Future<RestResponse> r6 = executor.submit(createProjectFoo);
        Future<RestResponse> r7 = executor.submit(createProjectFoo);
        Future<RestResponse> r8 = executor.submit(createProjectFoo);
        Future<RestResponse> r9 = executor.submit(createProjectFoo);
        Future<RestResponse> r10 = executor.submit(createProjectFoo);
        assertThat(
                ImmutableList.of(
                    r1.get().getStatusCode(),
                    r2.get().getStatusCode(),
                    r3.get().getStatusCode(),
                    r4.get().getStatusCode(),
                    r5.get().getStatusCode(),
                    r6.get().getStatusCode(),
                    r7.get().getStatusCode(),
                    r8.get().getStatusCode(),
                    r9.get().getStatusCode(),
                    r10.get().getStatusCode()))
            .containsAllOf(HttpStatus.SC_CREATED, HttpStatus.SC_CONFLICT);
      }
    } finally {
      executor.shutdown();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  public void createSameProjectFromTenConcurrentRequests9() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(10);
    try {
      for (int i = 0; i < 10; i++) {
        String newProjectName = name("foo" + i);
        CyclicBarrier sync = new CyclicBarrier(10);
        Callable<RestResponse> createProjectFoo =
            () -> {
              sync.await();
              return adminRestSession.put("/projects/" + newProjectName);
            };

        Future<RestResponse> r1 = executor.submit(createProjectFoo);
        Future<RestResponse> r2 = executor.submit(createProjectFoo);
        Future<RestResponse> r3 = executor.submit(createProjectFoo);
        Future<RestResponse> r4 = executor.submit(createProjectFoo);
        Future<RestResponse> r5 = executor.submit(createProjectFoo);
        Future<RestResponse> r6 = executor.submit(createProjectFoo);
        Future<RestResponse> r7 = executor.submit(createProjectFoo);
        Future<RestResponse> r8 = executor.submit(createProjectFoo);
        Future<RestResponse> r9 = executor.submit(createProjectFoo);
        Future<RestResponse> r10 = executor.submit(createProjectFoo);
        assertThat(
                ImmutableList.of(
                    r1.get().getStatusCode(),
                    r2.get().getStatusCode(),
                    r3.get().getStatusCode(),
                    r4.get().getStatusCode(),
                    r5.get().getStatusCode(),
                    r6.get().getStatusCode(),
                    r7.get().getStatusCode(),
                    r8.get().getStatusCode(),
                    r9.get().getStatusCode(),
                    r10.get().getStatusCode()))
            .containsAllOf(HttpStatus.SC_CREATED, HttpStatus.SC_CONFLICT);
      }
    } finally {
      executor.shutdown();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  public void createSameProjectFromTenConcurrentRequests10() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(10);
    try {
      for (int i = 0; i < 10; i++) {
        String newProjectName = name("foo" + i);
        CyclicBarrier sync = new CyclicBarrier(10);
        Callable<RestResponse> createProjectFoo =
            () -> {
              sync.await();
              return adminRestSession.put("/projects/" + newProjectName);
            };

        Future<RestResponse> r1 = executor.submit(createProjectFoo);
        Future<RestResponse> r2 = executor.submit(createProjectFoo);
        Future<RestResponse> r3 = executor.submit(createProjectFoo);
        Future<RestResponse> r4 = executor.submit(createProjectFoo);
        Future<RestResponse> r5 = executor.submit(createProjectFoo);
        Future<RestResponse> r6 = executor.submit(createProjectFoo);
        Future<RestResponse> r7 = executor.submit(createProjectFoo);
        Future<RestResponse> r8 = executor.submit(createProjectFoo);
        Future<RestResponse> r9 = executor.submit(createProjectFoo);
        Future<RestResponse> r10 = executor.submit(createProjectFoo);
        assertThat(
                ImmutableList.of(
                    r1.get().getStatusCode(),
                    r2.get().getStatusCode(),
                    r3.get().getStatusCode(),
                    r4.get().getStatusCode(),
                    r5.get().getStatusCode(),
                    r6.get().getStatusCode(),
                    r7.get().getStatusCode(),
                    r8.get().getStatusCode(),
                    r9.get().getStatusCode(),
                    r10.get().getStatusCode()))
            .containsAllOf(HttpStatus.SC_CREATED, HttpStatus.SC_CONFLICT);
      }
    } finally {
      executor.shutdown();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  public void createSameProjectFromTenConcurrentRequests11() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(10);
    try {
      for (int i = 0; i < 10; i++) {
        String newProjectName = name("foo" + i);
        CyclicBarrier sync = new CyclicBarrier(10);
        Callable<RestResponse> createProjectFoo =
            () -> {
              sync.await();
              return adminRestSession.put("/projects/" + newProjectName);
            };

        Future<RestResponse> r1 = executor.submit(createProjectFoo);
        Future<RestResponse> r2 = executor.submit(createProjectFoo);
        Future<RestResponse> r3 = executor.submit(createProjectFoo);
        Future<RestResponse> r4 = executor.submit(createProjectFoo);
        Future<RestResponse> r5 = executor.submit(createProjectFoo);
        Future<RestResponse> r6 = executor.submit(createProjectFoo);
        Future<RestResponse> r7 = executor.submit(createProjectFoo);
        Future<RestResponse> r8 = executor.submit(createProjectFoo);
        Future<RestResponse> r9 = executor.submit(createProjectFoo);
        Future<RestResponse> r10 = executor.submit(createProjectFoo);
        assertThat(
                ImmutableList.of(
                    r1.get().getStatusCode(),
                    r2.get().getStatusCode(),
                    r3.get().getStatusCode(),
                    r4.get().getStatusCode(),
                    r5.get().getStatusCode(),
                    r6.get().getStatusCode(),
                    r7.get().getStatusCode(),
                    r8.get().getStatusCode(),
                    r9.get().getStatusCode(),
                    r10.get().getStatusCode()))
            .containsAllOf(HttpStatus.SC_CREATED, HttpStatus.SC_CONFLICT);
      }
    } finally {
      executor.shutdown();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  public void createSameProjectFromTenConcurrentRequests12() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(10);
    try {
      for (int i = 0; i < 10; i++) {
        String newProjectName = name("foo" + i);
        CyclicBarrier sync = new CyclicBarrier(10);
        Callable<RestResponse> createProjectFoo =
            () -> {
              sync.await();
              return adminRestSession.put("/projects/" + newProjectName);
            };

        Future<RestResponse> r1 = executor.submit(createProjectFoo);
        Future<RestResponse> r2 = executor.submit(createProjectFoo);
        Future<RestResponse> r3 = executor.submit(createProjectFoo);
        Future<RestResponse> r4 = executor.submit(createProjectFoo);
        Future<RestResponse> r5 = executor.submit(createProjectFoo);
        Future<RestResponse> r6 = executor.submit(createProjectFoo);
        Future<RestResponse> r7 = executor.submit(createProjectFoo);
        Future<RestResponse> r8 = executor.submit(createProjectFoo);
        Future<RestResponse> r9 = executor.submit(createProjectFoo);
        Future<RestResponse> r10 = executor.submit(createProjectFoo);
        assertThat(
                ImmutableList.of(
                    r1.get().getStatusCode(),
                    r2.get().getStatusCode(),
                    r3.get().getStatusCode(),
                    r4.get().getStatusCode(),
                    r5.get().getStatusCode(),
                    r6.get().getStatusCode(),
                    r7.get().getStatusCode(),
                    r8.get().getStatusCode(),
                    r9.get().getStatusCode(),
                    r10.get().getStatusCode()))
            .containsAllOf(HttpStatus.SC_CREATED, HttpStatus.SC_CONFLICT);
      }
    } finally {
      executor.shutdown();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  public void createSameProjectFromTenConcurrentRequests13() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(10);
    try {
      for (int i = 0; i < 10; i++) {
        String newProjectName = name("foo" + i);
        CyclicBarrier sync = new CyclicBarrier(10);
        Callable<RestResponse> createProjectFoo =
            () -> {
              sync.await();
              return adminRestSession.put("/projects/" + newProjectName);
            };

        Future<RestResponse> r1 = executor.submit(createProjectFoo);
        Future<RestResponse> r2 = executor.submit(createProjectFoo);
        Future<RestResponse> r3 = executor.submit(createProjectFoo);
        Future<RestResponse> r4 = executor.submit(createProjectFoo);
        Future<RestResponse> r5 = executor.submit(createProjectFoo);
        Future<RestResponse> r6 = executor.submit(createProjectFoo);
        Future<RestResponse> r7 = executor.submit(createProjectFoo);
        Future<RestResponse> r8 = executor.submit(createProjectFoo);
        Future<RestResponse> r9 = executor.submit(createProjectFoo);
        Future<RestResponse> r10 = executor.submit(createProjectFoo);
        assertThat(
                ImmutableList.of(
                    r1.get().getStatusCode(),
                    r2.get().getStatusCode(),
                    r3.get().getStatusCode(),
                    r4.get().getStatusCode(),
                    r5.get().getStatusCode(),
                    r6.get().getStatusCode(),
                    r7.get().getStatusCode(),
                    r8.get().getStatusCode(),
                    r9.get().getStatusCode(),
                    r10.get().getStatusCode()))
            .containsAllOf(HttpStatus.SC_CREATED, HttpStatus.SC_CONFLICT);
      }
    } finally {
      executor.shutdown();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}

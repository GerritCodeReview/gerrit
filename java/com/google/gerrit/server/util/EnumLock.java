// Copyright (c) 2024 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
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

package com.google.gerrit.server.util;

import java.util.EnumMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A Enum based lock for mutually exclusive operations
 *
 * <p>EnumLock must be associated with an Enum, and creates a lock for each value in the Enum. Each
 * value in the Enum is meant to represent an operation which is mutually exclusive from all other
 * operations represented by the Enum. This is similar to how the Read and Write operations in a
 * ReentrantReadWriteLock are exclusive to each other. However, unlike the ReentrantReadWriteLock,
 * there are no Write operations, all the operations in an Enum lock are similar to the Read
 * operations in that the Lock for the same operation can be acquired more than once at a time.
 *
 * <p>Use this Lock similary to how the Read Lock is used in a ReentrantReadWriteLock, get the Lock
 * for the operation you wish to lock by using the operation's Enum, and then lock that Lock,
 * perform the operation, and then finally unlock the Lock.
 */
public class EnumLock<E extends Enum<E>> {
  public interface Lock {
    void lock();

    void unlock();
  }

  protected class CrossLock implements Lock {
    protected int count;

    @Override
    public void lock() {
      lock.lock();
      try {
        if (!isLocked()) {
          count++;
          return;
        }
        while (isLocked()) {
          unlocked.awaitUninterruptibly();
        }
        count++;
        return;
      } finally {
        lock.unlock();
      }
    }

    @Override
    public void unlock() {
      lock.lock();
      try {
        if (count == 0) {
          throw new RuntimeException("Not currently locked");
        }
        count--;
        if (count == 0) {
          unlocked.signal();
        }
      } finally {
        lock.unlock();
      }
    }
  }

  ReentrantLock lock = new ReentrantLock();
  Condition unlocked = lock.newCondition();
  EnumMap<E, CrossLock> crossLocks;

  private boolean isLocked() {
    for (CrossLock l : crossLocks.values()) {
      if (l.count != 0) {
        return false;
      }
    }
    return true;
  }

  public Lock lock(E key) {
    if (crossLocks == null) {
      @SuppressWarnings("unchecked")
      Class<E> cls = (Class<E>) key.getDeclaringClass();
      crossLocks = new EnumMap<E, CrossLock>(cls);
      for (E e : cls.getEnumConstants()) {
        crossLocks.put(e, new CrossLock());
      }
    }
    return crossLocks.get(key);
  }
}

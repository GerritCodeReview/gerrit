/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import '../test/common-test-setup-karma.js';
import {SpecialFilePath} from '../constants/constants.js';
import {
  addUnmodifiedFiles,
  computeDisplayPath,
  isMagicPath,
  specialFilePathCompare, truncatePath,
} from './path-list-util.js';

suite('path-list-utl tests', () => {
  test('special sort', () => {
    const testFiles = [
      '/a.h',
      '/MERGE_LIST',
      '/a.cpp',
      '/COMMIT_MSG',
      '/asdasd',
      '/mrPeanutbutter.py',
    ];
    assert.deepEqual(
        testFiles.sort(specialFilePathCompare),
        [
          '/COMMIT_MSG',
          '/MERGE_LIST',
          '/a.h',
          '/a.cpp',
          '/asdasd',
          '/mrPeanutbutter.py',
        ]);
  });

  test('special file path sorting', () => {
    assert.deepEqual(
        ['.b', '/COMMIT_MSG', '.a', 'file'].sort(
            specialFilePathCompare),
        ['/COMMIT_MSG', '.a', '.b', 'file']);

    assert.deepEqual(
        ['.b', '/COMMIT_MSG', 'foo/bar/baz.cc', 'foo/bar/baz.h'].sort(
            specialFilePathCompare),
        ['/COMMIT_MSG', '.b', 'foo/bar/baz.h', 'foo/bar/baz.cc']);

    assert.deepEqual(
        ['.b', '/COMMIT_MSG', 'foo/bar/baz.cc', 'foo/bar/baz.hpp'].sort(
            specialFilePathCompare),
        ['/COMMIT_MSG', '.b', 'foo/bar/baz.hpp', 'foo/bar/baz.cc']);

    assert.deepEqual(
        ['.b', '/COMMIT_MSG', 'foo/bar/baz.cc', 'foo/bar/baz.hxx'].sort(
            specialFilePathCompare),
        ['/COMMIT_MSG', '.b', 'foo/bar/baz.hxx', 'foo/bar/baz.cc']);

    assert.deepEqual(
        ['foo/bar.h', 'foo/bar.hxx', 'foo/bar.hpp'].sort(
            specialFilePathCompare),
        ['foo/bar.h', 'foo/bar.hpp', 'foo/bar.hxx']);

    // Regression test for Issue 4448.
    assert.deepEqual(
        [
          'minidump/minidump_memory_writer.cc',
          'minidump/minidump_memory_writer.h',
          'minidump/minidump_thread_writer.cc',
          'minidump/minidump_thread_writer.h',
        ].sort(specialFilePathCompare),
        [
          'minidump/minidump_memory_writer.h',
          'minidump/minidump_memory_writer.cc',
          'minidump/minidump_thread_writer.h',
          'minidump/minidump_thread_writer.cc',
        ]);

    // Regression test for Issue 4545.
    assert.deepEqual(
        [
          'task_test.go',
          'task.go',
        ].sort(specialFilePathCompare),
        [
          'task.go',
          'task_test.go',
        ]);
  });

  test('file display name', () => {
    assert.equal(computeDisplayPath('/foo/bar/baz'), '/foo/bar/baz');
    assert.equal(computeDisplayPath('/foobarbaz'), '/foobarbaz');
    assert.equal(computeDisplayPath('/COMMIT_MSG'), 'Commit message');
    assert.equal(computeDisplayPath('/MERGE_LIST'), 'Merge list');
  });

  test('isMagicPath', () => {
    assert.isFalse(isMagicPath(undefined));
    assert.isFalse(isMagicPath('/foo.cc'));
    assert.isTrue(isMagicPath('/COMMIT_MSG'));
    assert.isTrue(isMagicPath('/MERGE_LIST'));
  });

  test('patchset level comments are hidden', () => {
    const commentedPaths = {
      [SpecialFilePath.PATCHSET_LEVEL_COMMENTS]: true,
      'file1.txt': true,
    };

    const files = {'file2.txt': {status: 'M'}};
    addUnmodifiedFiles(files, commentedPaths);
    assert.equal(files['file1.txt'].status, 'U');
    assert.equal(files['file2.txt'].status, 'M');
    assert.isFalse(files.hasOwnProperty(
        SpecialFilePath.PATCHSET_LEVEL_COMMENTS));
  });

  test('truncatePath with long path should add ellipsis', () => {
    let path = 'level1/level2/level3/level4/file.js';
    let shortenedPath = truncatePath(path);
    // The expected path is truncated with an ellipsis.
    const expectedPath = '\u2026/file.js';
    assert.equal(shortenedPath, expectedPath);

    path = 'level2/file.js';
    shortenedPath = truncatePath(path);
    assert.equal(shortenedPath, expectedPath);
  });

  test('truncatePath with opt_threshold', () => {
    let path = 'level1/level2/level3/level4/file.js';
    let shortenedPath = truncatePath(path, 2);
    // The expected path is truncated with an ellipsis.
    const expectedPath = '\u2026/level4/file.js';
    assert.equal(shortenedPath, expectedPath);

    path = 'level2/file.js';
    shortenedPath = truncatePath(path, 2);
    assert.equal(shortenedPath, path);
  });

  test('truncatePath with short path should not add ellipsis', () => {
    const path = 'file.js';
    const expectedPath = 'file.js';
    const shortenedPath = truncatePath(path);
    assert.equal(shortenedPath, expectedPath);
  });
});


/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup';
import {FileInfoStatus, SpecialFilePath} from '../constants/constants';
import {
  addUnmodifiedFiles,
  computeDisplayPath,
  isMagicPath,
  specialFilePathCompare,
  truncatePath,
} from './path-list-util';
import {hasOwnProperty} from './common-util';
import {assert} from '@open-wc/testing';
import {FileNameToFileInfoMap} from '../types/common';

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
    assert.deepEqual(testFiles.sort(specialFilePathCompare), [
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
      ['.b', '/COMMIT_MSG', '.a', 'file'].sort(specialFilePathCompare),
      ['/COMMIT_MSG', '.a', '.b', 'file']
    );

    assert.deepEqual(
      ['.b', '/COMMIT_MSG', 'foo/bar/baz.cc', 'foo/bar/baz.h'].sort(
        specialFilePathCompare
      ),
      ['/COMMIT_MSG', '.b', 'foo/bar/baz.h', 'foo/bar/baz.cc']
    );

    assert.deepEqual(
      ['.b', '/COMMIT_MSG', 'foo/bar/baz.cc', 'foo/bar/baz.hpp'].sort(
        specialFilePathCompare
      ),
      ['/COMMIT_MSG', '.b', 'foo/bar/baz.hpp', 'foo/bar/baz.cc']
    );

    assert.deepEqual(
      ['.b', '/COMMIT_MSG', 'foo/bar/baz.cc', 'foo/bar/baz.hxx'].sort(
        specialFilePathCompare
      ),
      ['/COMMIT_MSG', '.b', 'foo/bar/baz.hxx', 'foo/bar/baz.cc']
    );

    assert.deepEqual(
      ['foo/bar.h', 'foo/bar.hxx', 'foo/bar.hpp'].sort(specialFilePathCompare),
      ['foo/bar.h', 'foo/bar.hpp', 'foo/bar.hxx']
    );

    // Regression test for Issue 15635
    assert.deepEqual(
      ['manager.cc', 'manager.hh'].sort(specialFilePathCompare),
      ['manager.hh', 'manager.cc']
    );

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
      ]
    );

    // Regression test for Issue 4545.
    assert.deepEqual(['task_test.go', 'task.go'].sort(specialFilePathCompare), [
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

    const files: FileNameToFileInfoMap = {
      'file2.txt': {
        status: FileInfoStatus.REWRITTEN,
        size_delta: 10,
        size: 10,
      },
    };
    addUnmodifiedFiles(files, commentedPaths);
    assert.equal(files['file1.txt'].status, FileInfoStatus.UNMODIFIED);
    assert.equal(files['file2.txt'].status, FileInfoStatus.REWRITTEN);
    assert.isFalse(
      hasOwnProperty(files, SpecialFilePath.PATCHSET_LEVEL_COMMENTS)
    );
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

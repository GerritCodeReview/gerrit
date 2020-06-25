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







import '../../test/common-test-setup-karma.js';
import {PathListBehavior} from './gr-path-list-behavior.js';
import {SpecialFilePath} from '../../constants/constants.js';

suite('gr-path-list-behavior tests', () => {
  test('special sort', () => {
    const sort = PathListBehavior.specialFilePathCompare;
    const testFiles = [
      '/a.h',
      '/MERGE_LIST',
      '/a.cpp',
      '/COMMIT_MSG',
      '/asdasd',
      '/mrPeanutbutter.py',
    ];
    assert.deepEqual(
        testFiles.sort(sort),
        [
          '/COMMIT_MSG',
          '/MERGE_LIST',
          '/a.h',
          '/a.cpp',
          '/asdasd',
          '/mrPeanutbutter.py',
        ]);
  });

  test('file display name', () => {
    const name = PathListBehavior.computeDisplayPath;
    assert.equal(name('/foo/bar/baz'), '/foo/bar/baz');
    assert.equal(name('/foobarbaz'), '/foobarbaz');
    assert.equal(name('/COMMIT_MSG'), 'Commit message');
    assert.equal(name('/MERGE_LIST'), 'Merge list');
  });

  test('isMagicPath', () => {
    const isMagic = PathListBehavior.isMagicPath;
    assert.isFalse(isMagic(undefined));
    assert.isFalse(isMagic('/foo.cc'));
    assert.isTrue(isMagic('/COMMIT_MSG'));
    assert.isTrue(isMagic('/MERGE_LIST'));
  });

  test('patchset level comments are hidden', () => {
    const commentedPaths = {
      [SpecialFilePath.PATCHSET_LEVEL_COMMENTS]: true,
      'file1.txt': true,
    };

    const files = {'file2.txt': {status: 'M'}};
    PathListBehavior.addUnmodifiedFiles(files, commentedPaths);
    assert.equal(files['file1.txt'].status, 'U');
    assert.equal(files['file2.txt'].status, 'M');
    assert.isFalse(files.hasOwnProperty(
        SpecialFilePath.PATCHSET_LEVEL_COMMENTS));
  });

  test('truncatePath with long path should add ellipsis', () => {
    const truncatePath = PathListBehavior.truncatePath;
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
    const truncatePath = PathListBehavior.truncatePath;
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
    const truncatePath = PathListBehavior.truncatePath;
    const path = 'file.js';
    const expectedPath = 'file.js';
    const shortenedPath = truncatePath(path);
    assert.equal(shortenedPath, expectedPath);
  });
});


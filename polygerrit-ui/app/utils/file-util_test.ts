/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import '../test/common-test-setup';
import {expandFileMode, FileMode, fileModeToString} from './file-util';

suite('file-util tests', () => {
  test('fileModeToString', () => {
    const check = (
      mode: number | undefined,
      str: string,
      includeNumber = true
    ) => assert.equal(fileModeToString(mode, includeNumber), str);

    check(undefined, '');
    check(0, '');
    check(1, '');
    check(FileMode.REGULAR_FILE, 'regular', false);
    check(FileMode.EXECUTABLE_FILE, 'executable', false);
    check(FileMode.SYMLINK, 'symlink', false);
    check(FileMode.GITLINK, 'gitlink', false);
    check(FileMode.REGULAR_FILE, 'regular (100644)');
    check(FileMode.EXECUTABLE_FILE, 'executable (100755)');
    check(FileMode.SYMLINK, 'symlink (120000)');
    check(FileMode.GITLINK, 'gitlink (160000)');
  });

  test('expandFileMode', () => {
    assert.deepEqual(['asdf'].map(expandFileMode), ['asdf']);
    assert.deepEqual(
      ['old mode 100644', 'new mode 100755'].map(expandFileMode),
      ['old mode regular (100644)', 'new mode executable (100755)']
    );
  });
});

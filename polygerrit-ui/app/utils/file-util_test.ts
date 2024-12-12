/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup';
import {assert} from '@open-wc/testing';
import {
  expandFileMode,
  FileMode,
  fileModeToString,
  formatBytes,
  getFileExtension,
} from './file-util';

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

  test('formatBytes function', () => {
    const table = {
      '64': '+64 B',
      '1023': '+1023 B',
      '1024': '+1 KiB',
      '4096': '+4 KiB',
      '1073741824': '+1 GiB',
      '-64': '-64 B',
      '-1023': '-1023 B',
      '-1024': '-1 KiB',
      '-4096': '-4 KiB',
      '-1073741824': '-1 GiB',
      '0': '+/-0 B',
    };
    for (const [bytes, expected] of Object.entries(table)) {
      assert.equal(formatBytes(Number(bytes)), expected);
    }
  });

  test('formatBytes function with prepend disabled', () => {
    const table = {
      '64': '64 B',
      '1023': '1023 B',
      '1024': '1 KiB',
      '4096': '4 KiB',
      '1073741824': '1 GiB',
      '0': '0 B',
    };
    for (const [bytes, expected] of Object.entries(table)) {
      assert.equal(formatBytes(Number(bytes), false), expected);
    }
  });

  suite('getFileExtension', () => {
    test('returns an empty string when the file name does not have an extension', () => {
      assert.equal(getFileExtension('my_file'), '');
    });
    test('returns the extension when the file name has an extension', () => {
      assert.equal(getFileExtension('my_file.txt'), 'txt');
      assert.equal(getFileExtension('folder/my_file.java'), 'java');
      assert.equal(getFileExtension('.hidden_file.ts'), 'ts');
    });
  });
});

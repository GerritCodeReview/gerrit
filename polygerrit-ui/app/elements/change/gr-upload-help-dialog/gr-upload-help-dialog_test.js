/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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

import '../../../test/common-test-setup-karma.js';
import './gr-upload-help-dialog.js';

const basicFixture = fixtureFromElement('gr-upload-help-dialog');

suite('gr-upload-help-dialog tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('constructs push command from branch', () => {
    element.targetBranch = 'foo';
    assert.equal(element._pushCommand, 'git push origin HEAD:refs/for/foo');

    element.targetBranch = 'master';
    assert.equal(element._pushCommand,
        'git push origin HEAD:refs/for/master');
  });

  suite('fetch command', () => {
    const testRev = {
      fetch: {
        http: {
          commands: {
            Checkout: 'http checkout',
            Pull: 'http pull',
          },
        },
        ssh: {
          commands: {
            Pull: 'ssh pull',
          },
        },
      },
    };

    test('null cases', () => {
      assert.isUndefined(element._computeFetchCommand());
      assert.isUndefined(element._computeFetchCommand({}));
      assert.isUndefined(element._computeFetchCommand({fetch: null}));
      assert.isUndefined(element._computeFetchCommand({fetch: {}}));
    });

    test('not all defined', () => {
      assert.isUndefined(
          element._computeFetchCommand(testRev, undefined, ''));
      assert.isUndefined(
          element._computeFetchCommand(testRev, '', undefined));
      assert.isUndefined(
          element._computeFetchCommand(undefined, '', ''));
    });

    test('insufficiently defined scheme', () => {
      assert.isUndefined(
          element._computeFetchCommand(testRev, '', 'badscheme'));

      const rev = {...testRev};
      rev.fetch = {...testRev.fetch, nocmds: {commands: {}}};
      assert.isUndefined(
          element._computeFetchCommand(rev, '', 'nocmds'));

      rev.fetch.nocmds.commands.unsupported = 'unsupported';
      assert.isUndefined(
          element._computeFetchCommand(rev, '', 'nocmds'));
    });

    test('default scheme and command', () => {
      const cmd = element._computeFetchCommand(testRev, '', '');
      assert.isTrue(cmd === 'http checkout' || cmd === 'ssh pull');
    });

    test('default command', () => {
      assert.strictEqual(
          element._computeFetchCommand(testRev, '', 'http'),
          'http checkout');
      assert.strictEqual(
          element._computeFetchCommand(testRev, '', 'ssh'),
          'ssh pull');
    });

    test('user preferred scheme and command', () => {
      assert.strictEqual(
          element._computeFetchCommand(testRev, 'PULL', 'http'),
          'http pull');
      assert.strictEqual(
          element._computeFetchCommand(testRev, 'badcmd', 'http'),
          'http checkout');
    });
  });
});


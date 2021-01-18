/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import './gr-download-commands.js';
import {isHidden, stubRestApi} from '../../../test/test-utils.js';

const basicFixture = fixtureFromElement('gr-download-commands');

suite('gr-download-commands', () => {
  let element;

  const SCHEMES = ['http', 'repo', 'ssh'];
  const COMMANDS = [{
    title: 'Checkout',
    command: `git fetch http://andybons@localhost:8080/a/test-project
        refs/changes/05/5/1 && git checkout FETCH_HEAD`,
  }, {
    title: 'Cherry Pick',
    command: `git fetch http://andybons@localhost:8080/a/test-project
        refs/changes/05/5/1 && git cherry-pick FETCH_HEAD`,
  }, {
    title: 'Format Patch',
    command: `git fetch http://andybons@localhost:8080/a/test-project
        refs/changes/05/5/1 && git format-patch -1 --stdout FETCH_HEAD`,
  }, {
    title: 'Pull',
    command: `git pull http://andybons@localhost:8080/a/test-project
        refs/changes/05/5/1`,
  }];
  const SELECTED_SCHEME = 'http';

  setup(() => {

  });

  suite('unauthenticated', () => {
    setup(async () => {
      stubRestApi('getLoggedIn').returns(Promise.resolve(false));
      element = basicFixture.instantiate();
      element.schemes = SCHEMES;
      element.commands = COMMANDS;
      element.selectedScheme = SELECTED_SCHEME;
      await flush();
    });

    test('focusOnCopy', () => {
      const focusStub = sinon.stub(element.shadowRoot
          .querySelector('gr-shell-command'),
      'focusOnCopy');
      element.focusOnCopy();
      assert.isTrue(focusStub.called);
    });

    test('element visibility', () => {
      assert.isFalse(isHidden(element.shadowRoot.querySelector('paper-tabs')));
      assert.isFalse(isHidden(element.shadowRoot.querySelector('.commands')));

      element.schemes = [];
      assert.isTrue(isHidden(element.shadowRoot.querySelector('paper-tabs')));
      assert.isTrue(isHidden(element.shadowRoot.querySelector('.commands')));
    });

    test('tab selection', () => {
      assert.equal(element.$.downloadTabs.selected, '0');
      MockInteractions.tap(element.shadowRoot
          .querySelector('[data-scheme="ssh"]'));
      flush();
      assert.equal(element.selectedScheme, 'ssh');
      assert.equal(element.$.downloadTabs.selected, '2');
    });

    test('loads scheme from preferences', async () => {
      const stub = stubRestApi('getPreferences').returns(
          Promise.resolve({download_scheme: 'repo'}));
      element._loggedIn = true;
      await flush();
      assert.isTrue(stub.called);
      await stub.lastCall.returnValue;
      assert.equal(element.selectedScheme, 'repo');
    });

    test('normalize scheme from preferences', async () => {
      const stub = stubRestApi('getPreferences').returns(
          Promise.resolve({download_scheme: 'REPO'}));
      element._loggedIn = true;
      await flush();
      assert.isTrue(stub.called);
      await stub.lastCall.returnValue;
      assert.equal(element.selectedScheme, 'repo');
    });

    test('saves scheme to preferences', () => {
      element._loggedIn = true;
      const savePrefsStub = stubRestApi('savePreferences').returns(
          Promise.resolve());

      flush();

      const repoTab = element.shadowRoot
          .querySelector('paper-tab[data-scheme="repo"]');

      MockInteractions.tap(repoTab);

      assert.isTrue(savePrefsStub.called);
      assert.equal(savePrefsStub.lastCall.args[0].download_scheme,
          repoTab.getAttribute('data-scheme'));
    });
  });
});


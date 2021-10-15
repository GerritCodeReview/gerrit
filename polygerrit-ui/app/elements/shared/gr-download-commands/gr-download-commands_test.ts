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

import '../../../test/common-test-setup-karma';
import './gr-download-commands';
import {GrDownloadCommands} from './gr-download-commands';
import {isHidden, queryAndAssert, stubRestApi} from '../../../test/test-utils';
import {updatePreferences} from '../../../services/user/user-model';
import {createPreferences} from '../../../test/test-data-generators';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {GrShellCommand} from '../gr-shell-command/gr-shell-command';
import {createDefaultPreferences} from '../../../constants/constants';

const basicFixture = fixtureFromElement('gr-download-commands');

suite('gr-download-commands', () => {
  let element: GrDownloadCommands;

  const SCHEMES = ['http', 'repo', 'ssh'];
  const COMMANDS = [
    {
      title: 'Checkout',
      command: `git fetch http://andybons@localhost:8080/a/test-project
        refs/changes/05/5/1 && git checkout FETCH_HEAD`,
    },
    {
      title: 'Cherry Pick',
      command: `git fetch http://andybons@localhost:8080/a/test-project
        refs/changes/05/5/1 && git cherry-pick FETCH_HEAD`,
    },
    {
      title: 'Format Patch',
      command: `git fetch http://andybons@localhost:8080/a/test-project
        refs/changes/05/5/1 && git format-patch -1 --stdout FETCH_HEAD`,
    },
    {
      title: 'Pull',
      command: `git pull http://andybons@localhost:8080/a/test-project
        refs/changes/05/5/1`,
    },
  ];
  const SELECTED_SCHEME = 'http';

  setup(() => {});

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
      const focusStub = sinon.stub(
        queryAndAssert<GrShellCommand>(element, 'gr-shell-command'),
        'focusOnCopy'
      );
      element.focusOnCopy();
      assert.isTrue(focusStub.called);
    });

    test('element visibility', () => {
      assert.isFalse(isHidden(queryAndAssert(element, 'paper-tabs')));
      assert.isFalse(isHidden(queryAndAssert(element, '.commands')));

      element.schemes = [];
      assert.isTrue(isHidden(queryAndAssert(element, 'paper-tabs')));
      assert.isTrue(isHidden(queryAndAssert(element, '.commands')));
    });

    test('tab selection', () => {
      assert.equal(element.$.downloadTabs.selected, '0');
      MockInteractions.tap(queryAndAssert(element, '[data-scheme="ssh"]'));
      flush();
      assert.equal(element.selectedScheme, 'ssh');
      assert.equal(element.$.downloadTabs.selected, '2');
    });

    test('saves scheme to preferences', () => {
      element._loggedIn = true;
      const savePrefsStub = stubRestApi('savePreferences').returns(
        Promise.resolve(createDefaultPreferences())
      );

      flush();

      const repoTab = queryAndAssert(element, 'paper-tab[data-scheme="repo"]');

      MockInteractions.tap(repoTab);

      assert.isTrue(savePrefsStub.called);
      assert.equal(
        savePrefsStub.lastCall.args[0].download_scheme,
        repoTab.getAttribute('data-scheme')
      );
    });
  });
  suite('authenticated', () => {
    test('loads scheme from preferences', async () => {
      updatePreferences({
        ...createPreferences(),
        download_scheme: 'repo',
      });
      const element = basicFixture.instantiate();
      await flush();
      assert.equal(element.selectedScheme, 'repo');
    });

    test('normalize scheme from preferences', async () => {
      updatePreferences({
        ...createPreferences(),
        download_scheme: 'REPO',
      });
      const element = basicFixture.instantiate();
      await flush();
      assert.equal(element.selectedScheme, 'repo');
    });
  });
});

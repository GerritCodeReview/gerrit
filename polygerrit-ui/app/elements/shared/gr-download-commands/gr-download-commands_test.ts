/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-download-commands';
import {GrDownloadCommands} from './gr-download-commands';
import {
  isHidden,
  query,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import {createPreferences} from '../../../test/test-data-generators';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {GrShellCommand} from '../gr-shell-command/gr-shell-command';
import {createDefaultPreferences} from '../../../constants/constants';
import {PaperTabsElement} from '@polymer/paper-tabs/paper-tabs';

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
      await element.updateComplete;
    });

    test('focusOnCopy', async () => {
      const focusStub = sinon.stub(
        queryAndAssert<GrShellCommand>(element, 'gr-shell-command'),
        'focusOnCopy'
      );
      await element.focusOnCopy();
      assert.isTrue(focusStub.called);
    });

    test('element visibility', async () => {
      assert.isFalse(isHidden(queryAndAssert(element, 'paper-tabs')));
      assert.isFalse(isHidden(queryAndAssert(element, '.commands')));
      assert.isTrue(Boolean(query(element, '#downloadTabs')));

      element.schemes = [];
      await element.updateComplete;
      assert.isTrue(isHidden(queryAndAssert(element, 'paper-tabs')));
      assert.isTrue(Boolean(query(element, '.commands')));
      assert.isTrue(isHidden(queryAndAssert(element, '.commands')));
      // Should still be present but hidden
      assert.isTrue(Boolean(query(element, '#downloadTabs')));
      assert.isTrue(isHidden(queryAndAssert(element, '#downloadTabs')));
    });

    test('tab selection', async () => {
      assert.equal(
        queryAndAssert<PaperTabsElement>(element, '#downloadTabs').selected,
        '0'
      );
      MockInteractions.tap(queryAndAssert(element, '[data-scheme="ssh"]'));
      await element.updateComplete;
      assert.equal(element.selectedScheme, 'ssh');
      assert.equal(
        queryAndAssert<PaperTabsElement>(element, '#downloadTabs').selected,
        '2'
      );
    });

    test('saves scheme to preferences', async () => {
      element.loggedIn = true;
      const savePrefsStub = stubRestApi('savePreferences').returns(
        Promise.resolve(createDefaultPreferences())
      );

      await element.updateComplete;

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
      const element = basicFixture.instantiate();
      await element.updateComplete;
      element.userModel.setPreferences({
        ...createPreferences(),
        download_scheme: 'repo',
      });
      assert.equal(element.selectedScheme, 'repo');
    });

    test('normalize scheme from preferences', async () => {
      const element = basicFixture.instantiate();
      await element.updateComplete;
      element.userModel.setPreferences({
        ...createPreferences(),
        download_scheme: 'REPO',
      });
      assert.equal(element.selectedScheme, 'repo');
    });
  });
});

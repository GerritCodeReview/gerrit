/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-download-commands';
import {GrDownloadCommands} from './gr-download-commands';
import {
  isHidden,
  query,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import {createPreferences} from '../../../test/test-data-generators';
import {GrShellCommand} from '../gr-shell-command/gr-shell-command';
import {createDefaultPreferences} from '../../../constants/constants';
import {assert, fixture, html} from '@open-wc/testing';
import {UserModel, userModelToken} from '../../../models/user/user-model';
import {testResolver} from '../../../test/common-test-setup';
import {MdTabs} from '@material/web/tabs/tabs';
import {MdSecondaryTab} from '@material/web/tabs/secondary-tab';

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

  suite('unauthenticated', () => {
    setup(async () => {
      stubRestApi('getLoggedIn').returns(Promise.resolve(false));
      element = await fixture(
        html`<gr-download-commands></gr-download-commands>`
      );
      element.schemes = SCHEMES;
      element.commands = COMMANDS;
      element.selectedScheme = SELECTED_SCHEME;
      await element.updateComplete;
    });

    test('render', () => {
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <div class="schemes">
            <md-tabs id="downloadTabs">
              <md-secondary-tab data-scheme="http" md-tab="" tabindex="0">
                http
              </md-secondary-tab>
              <md-secondary-tab data-scheme="repo" md-tab="" tabindex="-1">
                repo
              </md-secondary-tab>
              <md-secondary-tab data-scheme="ssh" md-tab="" tabindex="-1">
                ssh
              </md-secondary-tab>
            </md-tabs>
          </div>
          <div class="commands"></div>
          <gr-shell-command class="_label_checkout"> </gr-shell-command>
          <gr-shell-command class="_label_cherrypick"> </gr-shell-command>
          <gr-shell-command class="_label_formatpatch"> </gr-shell-command>
          <gr-shell-command class="_label_pull"> </gr-shell-command>
        `
      );
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
      assert.isFalse(isHidden(queryAndAssert(element, 'md-tabs')));
      assert.isFalse(isHidden(queryAndAssert(element, '.commands')));
      assert.isTrue(Boolean(query(element, '#downloadTabs')));

      element.schemes = [];
      await element.updateComplete;
      assert.isUndefined(query(element, 'md-tabs'));
      assert.isTrue(Boolean(query(element, '.commands')));
      assert.isFalse(Boolean(query(element, '#downloadTabs')));
    });

    test('tab selection', async () => {
      assert.equal(
        queryAndAssert<MdTabs>(element, '#downloadTabs').activeTabIndex,
        0
      );
      queryAndAssert<MdSecondaryTab>(element, '[data-scheme="ssh"]').click();
      await element.updateComplete;
      assert.equal(element.selectedScheme, 'ssh');
      assert.equal(
        queryAndAssert<MdTabs>(element, '#downloadTabs').activeTabIndex,
        2
      );
    });

    test('saves scheme to preferences', async () => {
      element.loggedIn = true;
      const savePrefsStub = stubRestApi('savePreferences').returns(
        Promise.resolve(createDefaultPreferences())
      );

      await element.updateComplete;

      const repoTab = queryAndAssert<MdSecondaryTab>(
        element,
        'md-secondary-tab[data-scheme="repo"]'
      );

      repoTab.click();

      await element.updateComplete;

      assert.isTrue(savePrefsStub.called);
      assert.equal(
        savePrefsStub.lastCall.args[0].download_scheme,
        repoTab.getAttribute('data-scheme')
      );
    });
  });
  suite('authenticated', () => {
    let element: GrDownloadCommands;
    let userModel: UserModel;
    setup(async () => {
      userModel = testResolver(userModelToken);
      element = await fixture(
        html`<gr-download-commands></gr-download-commands>`
      );
    });
    test('loads scheme from preferences', async () => {
      userModel.setPreferences({
        ...createPreferences(),
        download_scheme: 'repo',
      });
      assert.equal(element.selectedScheme, 'repo');
    });

    test('normalize scheme from preferences', async () => {
      userModel.setPreferences({
        ...createPreferences(),
        download_scheme: 'REPO',
      });
      assert.equal(element.selectedScheme, 'repo');
    });
  });
});

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

import '../../../test/common-test-setup-karma';
import './gr-settings-view';
import {GrSettingsView} from './gr-settings-view';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom';
import {GerritView} from '../../../services/router/router-model';
import {queryAll, queryAndAssert, stubRestApi} from '../../../test/test-utils';
import {
  AuthInfo,
  AccountDetailInfo,
  EmailAddress,
  PreferencesInfo,
  ServerInfo,
  TopMenuItemInfo,
} from '../../../types/common';
import {
  createDefaultPreferences,
  DateFormat,
  DefaultBase,
  DiffViewMode,
  EmailFormat,
  EmailStrategy,
  TimeFormat,
} from '../../../constants/constants';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {
  createAccountDetailWithId,
  createPreferences,
  createServerInfo,
} from '../../../test/test-data-generators';
import {GrSelect} from '../../shared/gr-select/gr-select';
import {AppElementSettingsParam} from '../../gr-app-types';

const basicFixture = fixtureFromElement('gr-settings-view');
const blankFixture = fixtureFromElement('div');

suite('gr-settings-view tests', () => {
  let element: GrSettingsView;
  let account: AccountDetailInfo;
  let preferences: PreferencesInfo;
  let config: ServerInfo;

  function valueOf(title: string, id: string) {
    const sections = element.root?.querySelectorAll(`#${id} section`) ?? [];
    let titleEl;
    for (let i = 0; i < sections.length; i++) {
      titleEl = sections[i].querySelector('.title');
      if (titleEl?.textContent?.trim() === title) {
        const el = sections[i].querySelector('.value');
        if (el) return el;
      }
    }
    assert.fail(`element with title ${title} not found`);
  }

  // Because deepEqual isn't behaving in Safari.
  function assertMenusEqual(
    actual?: TopMenuItemInfo[],
    expected?: TopMenuItemInfo[]
  ) {
    if (actual === undefined) {
      assert.fail("assertMenusEqual 'actual' param is undefined");
    } else if (expected === undefined) {
      assert.fail("assertMenusEqual 'expected' param is undefined");
    }
    assert.equal(actual.length, expected.length);
    for (let i = 0; i < actual.length; i++) {
      assert.equal(actual[i].name, expected[i].name);
      assert.equal(actual[i].url, expected[i].url);
    }
  }

  function stubAddAccountEmail(statusCode: number) {
    return stubRestApi('addAccountEmail').callsFake(() =>
      Promise.resolve({status: statusCode} as Response)
    );
  }

  setup(async () => {
    account = {
      ...createAccountDetailWithId(123),
      name: 'user name',
      email: 'user@email' as EmailAddress,
      username: 'user username',
    };
    preferences = {
      ...createPreferences(),
      changes_per_page: 25,
      date_format: DateFormat.UK,
      time_format: TimeFormat.HHMM_12,
      diff_view: DiffViewMode.UNIFIED,
      email_strategy: EmailStrategy.ENABLED,
      email_format: EmailFormat.HTML_PLAINTEXT,
      default_base_for_merges: DefaultBase.FIRST_PARENT,
      relative_date_in_change_table: false,
      size_bar_in_change_table: true,
      my: [
        {url: '/first/url', name: 'first name', target: '_blank'},
        {url: '/second/url', name: 'second name', target: '_blank'},
      ] as TopMenuItemInfo[],
      change_table: [],
    };
    config = createServerInfo();

    stubRestApi('getAccount').returns(Promise.resolve(account));
    stubRestApi('getPreferences').returns(Promise.resolve(preferences));
    stubRestApi('getAccountEmails').returns(Promise.resolve(undefined));
    stubRestApi('getConfig').returns(Promise.resolve(config));
    element = basicFixture.instantiate();

    // Allow the element to render.
    if (element._testOnly_loadingPromise)
      await element._testOnly_loadingPromise;
  });

  test('theme changing', async () => {
    const reloadStub = sinon.stub(element, 'reloadPage');

    window.localStorage.removeItem('dark-theme');
    assert.isFalse(window.localStorage.getItem('dark-theme') === 'true');
    const themeToggle = queryAndAssert(
      element,
      '.darkToggle paper-toggle-button'
    );
    MockInteractions.tap(themeToggle);
    assert.isTrue(window.localStorage.getItem('dark-theme') === 'true');
    assert.isTrue(reloadStub.calledOnce);

    element._isDark = true;
    await flush();
    MockInteractions.tap(themeToggle);
    assert.isFalse(window.localStorage.getItem('dark-theme') === 'true');
    assert.isTrue(reloadStub.calledTwice);
  });

  test('calls the title-change event', () => {
    const titleChangedStub = sinon.stub();

    // Create a new view.
    const newElement = document.createElement('gr-settings-view');
    newElement.addEventListener('title-change', titleChangedStub);

    const blank = blankFixture.instantiate();
    blank.appendChild(newElement);

    flush();

    assert.isTrue(titleChangedStub.called);
    assert.equal(titleChangedStub.getCall(0).args[0].detail.title, 'Settings');
  });

  test('user preferences', async () => {
    // Rendered with the expected preferences selected.
    assert.equal(
      Number(
        (
          valueOf('Changes per page', 'preferences')!
            .firstElementChild as GrSelect
        ).bindValue
      ),
      preferences.changes_per_page
    );
    assert.equal(
      (
        valueOf('Date/time format', 'preferences')!
          .firstElementChild as GrSelect
      ).bindValue,
      preferences.date_format
    );
    assert.equal(
      (valueOf('Date/time format', 'preferences')!.lastElementChild as GrSelect)
        .bindValue,
      preferences.time_format
    );
    assert.equal(
      (
        valueOf('Email notifications', 'preferences')!
          .firstElementChild as GrSelect
      ).bindValue,
      preferences.email_strategy
    );
    assert.equal(
      (valueOf('Email format', 'preferences')!.firstElementChild as GrSelect)
        .bindValue,
      preferences.email_format
    );
    assert.equal(
      (
        valueOf('Default Base For Merges', 'preferences')!
          .firstElementChild as GrSelect
      ).bindValue,
      preferences.default_base_for_merges
    );
    assert.equal(
      (
        valueOf('Show Relative Dates In Changes Table', 'preferences')!
          .firstElementChild as HTMLInputElement
      ).checked,
      false
    );
    assert.equal(
      (valueOf('Diff view', 'preferences')!.firstElementChild as GrSelect)
        .bindValue,
      preferences.diff_view
    );
    assert.equal(
      (
        valueOf('Show size bars in file list', 'preferences')!
          .firstElementChild as HTMLInputElement
      ).checked,
      true
    );
    assert.equal(
      (
        valueOf('Publish comments on push', 'preferences')!
          .firstElementChild as HTMLInputElement
      ).checked,
      false
    );
    assert.equal(
      (
        valueOf(
          'Set new changes to "work in progress" by default',
          'preferences'
        )!.firstElementChild as HTMLInputElement
      ).checked,
      false
    );
    assert.equal(
      (
        valueOf('Disable token highlighting on hover', 'preferences')!
          .firstElementChild as HTMLInputElement
      ).checked,
      false
    );
    assert.equal(
      (
        valueOf(
          'Insert Signed-off-by Footer For Inline Edit Changes',
          'preferences'
        )!.firstElementChild as HTMLInputElement
      ).checked,
      false
    );

    assert.isFalse(element._prefsChanged);
    assert.isFalse(element._menuChanged);

    const publishOnPush = valueOf('Publish comments on push', 'preferences')!
      .firstElementChild!;

    MockInteractions.tap(publishOnPush);

    assert.isTrue(element._prefsChanged);
    assert.isFalse(element._menuChanged);

    stubRestApi('savePreferences').callsFake(prefs => {
      assertMenusEqual(prefs.my, preferences.my);
      assert.equal(prefs.publish_comments_on_push, true);
      return Promise.resolve(createDefaultPreferences());
    });

    // Save the change.
    await element._handleSavePreferences();
    assert.isFalse(element._prefsChanged);
    assert.isFalse(element._menuChanged);
  });

  test('publish comments on push', async () => {
    const publishCommentsOnPush = valueOf(
      'Publish comments on push',
      'preferences'
    )!.firstElementChild!;
    MockInteractions.tap(publishCommentsOnPush);

    assert.isFalse(element._menuChanged);
    assert.isTrue(element._prefsChanged);

    stubRestApi('savePreferences').callsFake(prefs => {
      assert.equal(prefs.publish_comments_on_push, true);
      return Promise.resolve(createDefaultPreferences());
    });

    // Save the change.
    await element._handleSavePreferences();
    assert.isFalse(element._prefsChanged);
    assert.isFalse(element._menuChanged);
  });

  test('set new changes work-in-progress', async () => {
    const newChangesWorkInProgress = valueOf(
      'Set new changes to "work in progress" by default',
      'preferences'
    )!.firstElementChild!;
    MockInteractions.tap(newChangesWorkInProgress);

    assert.isFalse(element._menuChanged);
    assert.isTrue(element._prefsChanged);

    stubRestApi('savePreferences').callsFake(prefs => {
      assert.equal(prefs.work_in_progress_by_default, true);
      return Promise.resolve(createDefaultPreferences());
    });

    // Save the change.
    await element._handleSavePreferences();
    assert.isFalse(element._prefsChanged);
    assert.isFalse(element._menuChanged);
  });

  test('menu', async () => {
    assert.isFalse(element._menuChanged);
    assert.isFalse(element._prefsChanged);

    assertMenusEqual(element._localMenu, preferences.my);

    const menu = element.$.menu.firstElementChild!;
    let tableRows = queryAll(menu, 'tbody tr');
    // let tableRows = menu.root.querySelectorAll('tbody tr');
    assert.equal(tableRows.length, preferences.my.length);

    // Add a menu item:
    element.splice('_localMenu', 1, 0, {name: 'foo', url: 'bar', target: ''});
    flush();

    // tableRows = menu.root.querySelectorAll('tbody tr');
    tableRows = queryAll(menu, 'tbody tr');
    assert.equal(tableRows.length, preferences.my.length + 1);

    assert.isTrue(element._menuChanged);
    assert.isFalse(element._prefsChanged);

    stubRestApi('savePreferences').callsFake(prefs => {
      assertMenusEqual(prefs.my, element._localMenu);
      return Promise.resolve(createDefaultPreferences());
    });

    await element._handleSaveMenu();
    assert.isFalse(element._menuChanged);
    assert.isFalse(element._prefsChanged);
    assertMenusEqual(element.prefs.my, element._localMenu);
  });

  test('add email validation', () => {
    assert.isFalse(element._isNewEmailValid('invalid email'));
    assert.isTrue(element._isNewEmailValid('vaguely@valid.email'));

    assert.isFalse(
      element._computeAddEmailButtonEnabled('invalid email', true)
    );
    assert.isFalse(
      element._computeAddEmailButtonEnabled('vaguely@valid.email', true)
    );
    assert.isTrue(
      element._computeAddEmailButtonEnabled('vaguely@valid.email', false)
    );
  });

  test('add email does not save invalid', () => {
    const addEmailStub = stubAddAccountEmail(201);

    assert.isFalse(element._addingEmail);
    assert.isNotOk(element._lastSentVerificationEmail);
    element._newEmail = 'invalid email';

    element._handleAddEmailButton();

    assert.isFalse(element._addingEmail);
    assert.isFalse(addEmailStub.called);
    assert.isNotOk(element._lastSentVerificationEmail);

    assert.isFalse(addEmailStub.called);
  });

  test('add email does save valid', async () => {
    const addEmailStub = stubAddAccountEmail(201);

    assert.isFalse(element._addingEmail);
    assert.isNotOk(element._lastSentVerificationEmail);
    element._newEmail = 'valid@email.com';

    element._handleAddEmailButton();

    assert.isTrue(element._addingEmail);
    assert.isTrue(addEmailStub.called);

    assert.isTrue(addEmailStub.called);
    await addEmailStub.lastCall.returnValue;
    assert.isOk(element._lastSentVerificationEmail);
  });

  test('add email does not set last-email if error', async () => {
    const addEmailStub = stubAddAccountEmail(500);

    assert.isNotOk(element._lastSentVerificationEmail);
    element._newEmail = 'valid@email.com';

    element._handleAddEmailButton();

    assert.isTrue(addEmailStub.called);
    await addEmailStub.lastCall.returnValue;
    assert.isNotOk(element._lastSentVerificationEmail);
  });

  test('emails are loaded without emailToken', () => {
    const emailEditorLoadDataStub = sinon.stub(
      element.$.emailEditor,
      'loadData'
    );
    element.params = {
      view: GerritView.SETTINGS,
    } as AppElementSettingsParam;
    element.connectedCallback();
    assert.isTrue(emailEditorLoadDataStub.calledOnce);
  });

  test('_handleSaveChangeTable', () => {
    let newColumns = ['Owner', 'Project', 'Branch'];
    element._localChangeTableColumns = newColumns.slice(0);
    element._showNumber = false;
    element._handleSaveChangeTable();
    assert.deepEqual(element.prefs.change_table, newColumns);
    assert.isNotOk(element.prefs.legacycid_in_change_table);

    newColumns = ['Size'];
    element._localChangeTableColumns = newColumns;
    element._showNumber = true;
    element._handleSaveChangeTable();
    assert.deepEqual(element.prefs.change_table, newColumns);
    assert.isTrue(element.prefs.legacycid_in_change_table);
  });

  test('reset menu item back to default', async () => {
    const originalMenu = {
      ...createDefaultPreferences(),
      my: [
        {url: '/first/url', name: 'first name', target: '_blank'},
        {url: '/second/url', name: 'second name', target: '_blank'},
        {url: '/third/url', name: 'third name', target: '_blank'},
      ] as TopMenuItemInfo[],
    };

    stubRestApi('getDefaultPreferences').returns(Promise.resolve(originalMenu));

    const updatedMenu = [
      {url: '/first/url', name: 'first name', target: '_blank'},
      {url: '/second/url', name: 'second name', target: '_blank'},
      {url: '/third/url', name: 'third name', target: '_blank'},
      {url: '/fourth/url', name: 'fourth name', target: '_blank'},
    ];

    element.set('_localMenu', updatedMenu);

    await element._handleResetMenuButton();
    assertMenusEqual(element._localMenu, originalMenu.my);
  });

  test('test that reset button is called', () => {
    const overlayOpen = sinon.stub(element, '_handleResetMenuButton');

    MockInteractions.tap(element.$.resetButton);

    assert.isTrue(overlayOpen.called);
  });

  test('_showHttpAuth', () => {
    const serverConfig: ServerInfo = {
      ...createServerInfo(),
      auth: {
        git_basic_auth_policy: 'HTTP',
      } as AuthInfo,
    };

    assert.isTrue(element._showHttpAuth(serverConfig));

    serverConfig.auth.git_basic_auth_policy = 'HTTP_LDAP';
    assert.isTrue(element._showHttpAuth(serverConfig));

    serverConfig.auth.git_basic_auth_policy = 'LDAP';
    assert.isFalse(element._showHttpAuth(serverConfig));

    serverConfig.auth.git_basic_auth_policy = 'OAUTH';
    assert.isFalse(element._showHttpAuth(serverConfig));

    assert.isFalse(element._showHttpAuth(undefined));
  });

  suite('_getFilterDocsLink', () => {
    test('with http: docs base URL', () => {
      const base = 'http://example.com/';
      const result = element._getFilterDocsLink(base);
      assert.equal(result, 'http://example.com/user-notify.html');
    });

    test('with http: docs base URL without slash', () => {
      const base = 'http://example.com';
      const result = element._getFilterDocsLink(base);
      assert.equal(result, 'http://example.com/user-notify.html');
    });

    test('with https: docs base URL', () => {
      const base = 'https://example.com/';
      const result = element._getFilterDocsLink(base);
      assert.equal(result, 'https://example.com/user-notify.html');
    });

    test('without docs base URL', () => {
      const result = element._getFilterDocsLink(null);
      assert.equal(
        result,
        'https://gerrit-review.googlesource.com/' +
          'Documentation/user-notify.html'
      );
    });

    test('ignores non HTTP links', () => {
      const base = 'javascript://alert("evil");';
      const result = element._getFilterDocsLink(base);
      assert.equal(
        result,
        'https://gerrit-review.googlesource.com/' +
          'Documentation/user-notify.html'
      );
    });
  });

  suite('when email verification token is provided', () => {
    let resolveConfirm: (
      value: string | PromiseLike<string | null> | null
    ) => void;
    let confirmEmailStub: sinon.SinonStub;
    let emailEditorLoadDataStub: sinon.SinonStub;

    setup(() => {
      emailEditorLoadDataStub = sinon.stub(element.$.emailEditor, 'loadData');
      confirmEmailStub = stubRestApi('confirmEmail').returns(
        new Promise(resolve => {
          resolveConfirm = resolve;
        })
      );

      element.params = {view: GerritView.SETTINGS, emailToken: 'foo'};
      element.connectedCallback();
    });

    test('it is used to confirm email via rest API', () => {
      assert.isTrue(confirmEmailStub.calledOnce);
      assert.isTrue(confirmEmailStub.calledWith('foo'));
    });

    test('emails are not loaded initially', () => {
      assert.isFalse(emailEditorLoadDataStub.called);
    });

    test('user emails are loaded after email confirmed', async () => {
      resolveConfirm('bar');
      await element._testOnly_loadingPromise;
      assert.isTrue(emailEditorLoadDataStub.calledOnce);
    });

    test('show-alert is fired when email is confirmed', async () => {
      const dispatchEventSpy = sinon.spy(element, 'dispatchEvent');
      resolveConfirm('bar');

      await element._testOnly_loadingPromise;
      assert.equal(
        (dispatchEventSpy.lastCall.args[0] as CustomEvent).type,
        'show-alert'
      );
      assert.deepEqual(
        (dispatchEventSpy.lastCall.args[0] as CustomEvent).detail,
        {message: 'bar'}
      );
    });
  });
});

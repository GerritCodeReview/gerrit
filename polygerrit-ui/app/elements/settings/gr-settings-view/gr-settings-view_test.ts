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
import {GerritView} from '../../../services/router/router-model';
import {queryAll, stubRestApi} from '../../../test/test-utils';
import {
  AuthInfo,
  AccountDetailInfo,
  EmailAddress,
  PreferencesInfo,
  ServerInfo,
  TopMenuItemInfo,
} from '../../../types/common';
import {
  AppTheme,
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
import {getComputedStyleValue} from '../../../utils/dom-util';

const basicFixture = fixtureFromElement('gr-settings-view');
const blankFixture = fixtureFromElement('div');

suite('gr-settings-view tests', () => {
  let element: GrSettingsView;
  let account: AccountDetailInfo;
  let preferences: PreferencesInfo;
  let config: ServerInfo;

  function valueOf(title: string, id: string) {
    const sections = queryAll(element, `#${id} section`);
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
      theme: AppTheme.LIGHT,
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
    await element.updateComplete;

    // Allow the element to render.
    if (element._testOnly_loadingPromise)
      await element._testOnly_loadingPromise;
    await element.updateComplete;
  });

  test('renders', async () => {
    sinon
      .stub(element, 'getFilterDocsLink')
      .returns('https://test.com/user-notify.html');
    element.docsBaseUrl = 'https://test.com';
    await element.updateComplete;
    // this cannot be formatted with /* HTML */, because it breaks test
    expect(element).shadowDom.to.equal(/* HTML*/ `<div
        class="loading"
        hidden=""
      >
        Loading...
      </div>
      <div>
        <gr-page-nav class="navStyles">
          <ul>
            <li><a href="#Profile"> Profile </a></li>
            <li><a href="#Preferences"> Preferences </a></li>
            <li><a href="#DiffPreferences"> Diff Preferences </a></li>
            <li><a href="#EditPreferences"> Edit Preferences </a></li>
            <li><a href="#Menu"> Menu </a></li>
            <li><a href="#ChangeTableColumns"> Change Table Columns </a></li>
            <li><a href="#Notifications"> Notifications </a></li>
            <li><a href="#EmailAddresses"> Email Addresses </a></li>
            <li><a href="#Groups"> Groups </a></li>
            <li><a href="#Identities"> Identities </a></li>
            <li><a href="#MailFilters"> Mail Filters </a></li>
            <gr-endpoint-decorator name="settings-menu-item">
            </gr-endpoint-decorator>
          </ul>
        </gr-page-nav>
        <div class="gr-form-styles main">
          <h1 class="heading-1">User Settings</h1>
          <h2 id="Profile">Profile</h2>
          <fieldset id="profile">
            <gr-account-info id="accountInfo"> </gr-account-info>
            <gr-button
              aria-disabled="true"
              disabled=""
              role="button"
              tabindex="-1"
            >
              Save changes
            </gr-button>
          </fieldset>
          <h2 id="Preferences">Preferences</h2>
          <fieldset id="preferences">
            <section>
              <label class="title" for="themeSelect">
                Theme
              </label>
              <span class="value">
                <gr-select>
                  <select id="themeSelect">
                    <option value="DARK">Dark</option>
                    <option value="LIGHT">Light</option>
                  </select>
                </gr-select>
              </span>
            </section>
            <section>
              <label class="title" for="changesPerPageSelect">
                Changes per page
              </label>
              <span class="value">
                <gr-select>
                  <select id="changesPerPageSelect">
                    <option value="10">10 rows per page</option>
                    <option value="25">25 rows per page</option>
                    <option value="50">50 rows per page</option>
                    <option value="100">100 rows per page</option>
                  </select>
                </gr-select>
              </span>
            </section>
            <section>
              <label class="title" for="dateTimeFormatSelect">
                Date/time format
              </label>
              <span class="value">
                <gr-select>
                  <select id="dateTimeFormatSelect">
                    <option value="STD">Jun 3 ; Jun 3, 2016</option>
                    <option value="US">06/03 ; 06/03/16</option>
                    <option value="ISO">06-03 ; 2016-06-03</option>
                    <option value="EURO">3. Jun ; 03.06.2016</option>
                    <option value="UK">03/06 ; 03/06/2016</option>
                  </select>
                </gr-select>
                <gr-select aria-label="Time Format">
                  <select id="timeFormatSelect">
                    <option value="HHMM_12">4:10 PM</option>
                    <option value="HHMM_24">16:10</option>
                  </select>
                </gr-select>
              </span>
            </section>
            <section>
              <label class="title" for="emailNotificationsSelect">
                Email notifications
              </label>
              <span class="value">
                <gr-select>
                  <select id="emailNotificationsSelect">
                    <option value="CC_ON_OWN_COMMENTS">Every comment</option>
                    <option value="ENABLED">
                      Only comments left by others
                    </option>
                    <option value="ATTENTION_SET_ONLY">
                      Only when I am in the attention set
                    </option>
                    <option value="DISABLED">None</option>
                  </select>
                </gr-select>
              </span>
            </section>
            <section>
              <label class="title" for="emailFormatSelect">
                Email format
              </label>
              <span class="value">
                <gr-select>
                  <select id="emailFormatSelect">
                    <option value="HTML_PLAINTEXT">HTML and plaintext</option>
                    <option value="PLAINTEXT">Plaintext only</option>
                  </select>
                </gr-select>
              </span>
            </section>
            <section>
              <span class="title"> Default Base For Merges </span>
              <span class="value">
                <gr-select>
                  <select id="defaultBaseForMergesSelect">
                    <option value="AUTO_MERGE">Auto Merge</option>
                    <option value="FIRST_PARENT">First Parent</option>
                  </select>
                </gr-select>
              </span>
            </section>
            <section>
              <label class="title" for="relativeDateInChangeTable">
                Show Relative Dates In Changes Table
              </label>
              <span class="value">
                <input id="relativeDateInChangeTable" type="checkbox" />
              </span>
            </section>
            <section>
              <span class="title"> Diff view </span>
              <span class="value">
                <gr-select>
                  <select id="diffViewSelect">
                    <option value="SIDE_BY_SIDE">Side by side</option>
                    <option value="UNIFIED_DIFF">Unified diff</option>
                  </select>
                </gr-select>
              </span>
            </section>
            <section>
              <label class="title" for="showSizeBarsInFileList">
                Show size bars in file list
              </label>
              <span class="value">
                <input
                  checked=""
                  id="showSizeBarsInFileList"
                  type="checkbox"
                />
              </span>
            </section>
            <section>
              <label class="title" for="publishCommentsOnPush">
                Publish comments on push
              </label>
              <span class="value">
                <input id="publishCommentsOnPush" type="checkbox" />
              </span>
            </section>
            <section>
              <label class="title" for="workInProgressByDefault">
                Set new changes to "work in progress" by default
              </label>
              <span class="value">
                <input id="workInProgressByDefault" type="checkbox" />
              </span>
            </section>
            <section>
              <label class="title" for="disableKeyboardShortcuts">
                Disable all keyboard shortcuts
              </label>
              <span class="value">
                <input id="disableKeyboardShortcuts" type="checkbox" />
              </span>
            </section>
            <section>
              <label class="title" for="disableTokenHighlighting">
                Disable token highlighting on hover
              </label>
              <span class="value">
                <input id="disableTokenHighlighting" type="checkbox" />
              </span>
            </section>
            <section>
              <label class="title" for="insertSignedOff">
                Insert Signed-off-by Footer For Inline Edit Changes
              </label>
              <span class="value">
                <input id="insertSignedOff" type="checkbox" />
              </span>
            </section>
            <gr-button
              aria-disabled="true"
              disabled=""
              id="savePrefs"
              role="button"
              tabindex="-1"
            >
              Save changes
            </gr-button>
          </fieldset>
          <h2 id="DiffPreferences">Diff Preferences</h2>
          <fieldset id="diffPreferences">
            <gr-diff-preferences id="diffPrefs"> </gr-diff-preferences>
            <gr-button
              aria-disabled="true"
              disabled=""
              id="saveDiffPrefs"
              role="button"
              tabindex="-1"
            >
              Save changes
            </gr-button>
          </fieldset>
          <gr-edit-preferences id="EditPreferences"> </gr-edit-preferences>
          <gr-menu-editor id="Menu"> </gr-menu-editor>
          <h2 id="ChangeTableColumns">Change Table Columns</h2>
          <fieldset id="changeTableColumns">
            <gr-change-table-editor> </gr-change-table-editor>
            <gr-button
              aria-disabled="true"
              disabled=""
              id="saveChangeTable"
              role="button"
              tabindex="-1"
            >
              Save changes
            </gr-button>
          </fieldset>
          <h2 id="Notifications">Notifications</h2>
          <fieldset id="watchedProjects">
            <gr-watched-projects-editor id="watchedProjectsEditor">
            </gr-watched-projects-editor>
            <gr-button
              aria-disabled="true"
              disabled=""
              id="_handleSaveWatchedProjects"
              role="button"
              tabindex="-1"
            >
              Save changes
            </gr-button>
          </fieldset>
          <h2 id="EmailAddresses">Email Addresses</h2>
          <fieldset id="email">
            <gr-email-editor id="emailEditor"> </gr-email-editor>
            <gr-button
              aria-disabled="true"
              disabled=""
              role="button"
              tabindex="-1"
            >
              Save changes
            </gr-button>
          </fieldset>
          <fieldset id="newEmail">
            <section>
              <span class="title"> New email address </span>
              <span class="value">
                <iron-input class="newEmailInput">
                  <input
                    class="newEmailInput"
                    placeholder="email@example.com"
                    type="text"
                  />
                </iron-input>
              </span>
            </section>
            <section hidden="" id="verificationSentMessage">
              <p>
                A verification email was sent to <em>
                </em>
               . Please check your
                inbox.
              </p>
            </section>
            <gr-button
              aria-disabled="true"
              disabled=""
              role="button"
              tabindex="-1"
            >
              Send verification
            </gr-button>
          </fieldset> 
          <h2 id="Groups">Groups</h2>
          <fieldset><gr-group-list id="groupList"> </gr-group-list></fieldset>
          <h2 id="Identities">Identities</h2>
          <fieldset>
            <gr-identities id="identities"> </gr-identities>
          </fieldset>
          <h2 id="MailFilters">Mail Filters</h2>
          <fieldset class="filters">
            <p>
              Gerrit emails include metadata about the change to support writing
              mail filters.
            </p>
            <p>
              Here are some example Gmail queries that can be used for filters
              or for searching through archived messages. View the
              <a
                href="https://test.com/user-notify.html"
                rel="nofollow"
                target="_blank"
              >
                Gerrit documentation
              </a>
              for the complete set of footers.
            </p>
            <table>
              <tbody>
                <tr>
                  <th>Name</th>
                  <th>Query</th>
                </tr>
                <tr>
                  <td>Changes requesting my review</td>
                  <td>
                    <code class="queryExample">
                      "Gerrit-Reviewer: <em> Your Name </em> <
                      <em> your.email@example.com </em> >"
                    </code>
                  </td>
                </tr>
                <tr>
                  <td>Changes requesting my attention</td>
                  <td>
                    <code class="queryExample">
                      "Gerrit-Attention: <em> Your Name </em> <
                      <em> your.email@example.com </em> >"
                    </code>
                  </td>
                </tr>
                <tr>
                  <td>Changes from a specific owner</td>
                  <td>
                    <code class="queryExample">
                      "Gerrit-Owner: <em> Owner name </em> <
                      <em> owner.email@example.com </em> >"
                    </code>
                  </td>
                </tr>
                <tr>
                  <td>Changes targeting a specific branch</td>
                  <td>
                    <code class="queryExample">
                      "Gerrit-Branch: <em> branch-name </em> "
                    </code>
                  </td>
                </tr>
                <tr>
                  <td>Changes in a specific project</td>
                  <td>
                    <code class="queryExample">
                      "Gerrit-Project: <em> project-name </em> "
                    </code>
                  </td>
                </tr>
                <tr>
                  <td>Messages related to a specific Change ID</td>
                  <td>
                    <code class="queryExample">
                      "Gerrit-Change-Id: <em> Change ID </em> "
                    </code>
                  </td>
                </tr>
                <tr>
                  <td>Messages related to a specific change number</td>
                  <td>
                    <code class="queryExample">
                      "Gerrit-Change-Number: <em> change number </em> "
                    </code>
                  </td>
                </tr>
              </tbody>
            </table>
          </fieldset>
          <gr-endpoint-decorator name="settings-screen">
          </gr-endpoint-decorator>
        </div>
      </div>`);
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
      (valueOf('Theme', 'preferences').firstElementChild as GrSelect).bindValue,
      preferences.theme
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

    assert.isFalse(element.prefsChanged);

    const themeSelect = valueOf('Theme', 'preferences')
      .firstElementChild as GrSelect;
    themeSelect.bindValue = 'DARK';

    themeSelect.dispatchEvent(
      new CustomEvent('change', {
        composed: true,
        bubbles: true,
      })
    );

    const publishOnPush = valueOf('Publish comments on push', 'preferences')!
      .firstElementChild!;

    MockInteractions.tap(publishOnPush);

    assert.isTrue(element.prefsChanged);

    stubRestApi('savePreferences').callsFake(prefs => {
      assertMenusEqual(prefs.my, preferences.my);
      assert.equal(prefs.publish_comments_on_push, true);
      assert.equal(prefs.theme, AppTheme.DARK);
      return Promise.resolve(createDefaultPreferences());
    });

    // Save the change.
    await element.handleSavePreferences();
    assert.isFalse(element.prefsChanged);
    assert.equal(
      getComputedStyleValue('--primary-text-color', document.body),
      '#e8eaed'
    );
  });

  test('publish comments on push', async () => {
    const publishCommentsOnPush = valueOf(
      'Publish comments on push',
      'preferences'
    )!.firstElementChild!;
    MockInteractions.tap(publishCommentsOnPush);

    assert.isTrue(element.prefsChanged);

    stubRestApi('savePreferences').callsFake(prefs => {
      assert.equal(prefs.publish_comments_on_push, true);
      return Promise.resolve(createDefaultPreferences());
    });

    // Save the change.
    await element.handleSavePreferences();
    assert.isFalse(element.prefsChanged);
  });

  test('set new changes work-in-progress', async () => {
    const newChangesWorkInProgress = valueOf(
      'Set new changes to "work in progress" by default',
      'preferences'
    )!.firstElementChild!;
    MockInteractions.tap(newChangesWorkInProgress);

    assert.isTrue(element.prefsChanged);

    stubRestApi('savePreferences').callsFake(prefs => {
      assert.equal(prefs.work_in_progress_by_default, true);
      return Promise.resolve(createDefaultPreferences());
    });

    // Save the change.
    await element.handleSavePreferences();
    assert.isFalse(element.prefsChanged);
  });

  test('add email validation', async () => {
    assert.isFalse(element.isNewEmailValid('invalid email'));
    assert.isTrue(element.isNewEmailValid('vaguely@valid.email'));

    element.newEmail = 'invalid email';
    element.addingEmail = true;
    await element.updateComplete;
    assert.isFalse(element.computeAddEmailButtonEnabled());
    element.newEmail = 'vaguely@valid.email';
    element.addingEmail = true;
    await element.updateComplete;
    assert.isFalse(element.computeAddEmailButtonEnabled());
    element.newEmail = 'vaguely@valid.email';
    element.addingEmail = false;
    await element.updateComplete;
    assert.isTrue(element.computeAddEmailButtonEnabled());
  });

  test('add email does not save invalid', () => {
    const addEmailStub = stubAddAccountEmail(201);

    assert.isFalse(element.addingEmail);
    assert.isNotOk(element.lastSentVerificationEmail);
    element.newEmail = 'invalid email';

    element.handleAddEmailButton();

    assert.isFalse(element.addingEmail);
    assert.isFalse(addEmailStub.called);
    assert.isNotOk(element.lastSentVerificationEmail);

    assert.isFalse(addEmailStub.called);
  });

  test('add email does save valid', async () => {
    const addEmailStub = stubAddAccountEmail(201);

    assert.isFalse(element.addingEmail);
    assert.isNotOk(element.lastSentVerificationEmail);
    element.newEmail = 'valid@email.com';

    element.handleAddEmailButton();

    assert.isTrue(element.addingEmail);
    assert.isTrue(addEmailStub.called);

    assert.isTrue(addEmailStub.called);
    await addEmailStub.lastCall.returnValue;
    assert.isOk(element.lastSentVerificationEmail);
  });

  test('add email does not set last-email if error', async () => {
    const addEmailStub = stubAddAccountEmail(500);

    assert.isNotOk(element.lastSentVerificationEmail);
    element.newEmail = 'valid@email.com';

    element.handleAddEmailButton();

    assert.isTrue(addEmailStub.called);
    await addEmailStub.lastCall.returnValue;
    assert.isNotOk(element.lastSentVerificationEmail);
  });

  test('emails are loaded without emailToken', () => {
    const emailEditorLoadDataStub = sinon.stub(element.emailEditor, 'loadData');
    element.params = {
      view: GerritView.SETTINGS,
    } as AppElementSettingsParam;
    element.firstUpdated();
    assert.isTrue(emailEditorLoadDataStub.calledOnce);
  });

  test('handleSaveChangeTable', () => {
    let newColumns = ['Owner', 'Project', 'Branch'];
    element.localChangeTableColumns = newColumns.slice(0);
    element.showNumber = false;
    element.handleSaveChangeTable();
    assert.deepEqual(element.prefs.change_table, newColumns);
    assert.isNotOk(element.prefs.legacycid_in_change_table);

    newColumns = ['Size'];
    element.localChangeTableColumns = newColumns;
    element.showNumber = true;
    element.handleSaveChangeTable();
    assert.deepEqual(element.prefs.change_table, newColumns);
    assert.isTrue(element.prefs.legacycid_in_change_table);
  });

  test('showHttpAuth', async () => {
    const serverConfig: ServerInfo = {
      ...createServerInfo(),
      auth: {
        git_basic_auth_policy: 'HTTP',
      } as AuthInfo,
    };

    element.serverConfig = serverConfig;
    await element.updateComplete;
    assert.isTrue(element.showHttpAuth());

    element.serverConfig.auth.git_basic_auth_policy = 'HTTP_LDAP';
    await element.updateComplete;
    assert.isTrue(element.showHttpAuth());

    element.serverConfig.auth.git_basic_auth_policy = 'LDAP';
    await element.updateComplete;
    assert.isFalse(element.showHttpAuth());

    element.serverConfig.auth.git_basic_auth_policy = 'OAUTH';
    await element.updateComplete;
    assert.isFalse(element.showHttpAuth());

    element.serverConfig = undefined;
    await element.updateComplete;
    assert.isFalse(element.showHttpAuth());
  });

  suite('getFilterDocsLink', () => {
    test('with http: docs base URL', () => {
      const base = 'http://example.com/';
      const result = element.getFilterDocsLink(base);
      assert.equal(result, 'http://example.com/user-notify.html');
    });

    test('with http: docs base URL without slash', () => {
      const base = 'http://example.com';
      const result = element.getFilterDocsLink(base);
      assert.equal(result, 'http://example.com/user-notify.html');
    });

    test('with https: docs base URL', () => {
      const base = 'https://example.com/';
      const result = element.getFilterDocsLink(base);
      assert.equal(result, 'https://example.com/user-notify.html');
    });

    test('without docs base URL', () => {
      const result = element.getFilterDocsLink(null);
      assert.equal(
        result,
        'https://gerrit-review.googlesource.com/' +
          'Documentation/user-notify.html'
      );
    });

    test('ignores non HTTP links', () => {
      const base = 'javascript://alert("evil");';
      const result = element.getFilterDocsLink(base);
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
      emailEditorLoadDataStub = sinon.stub(element.emailEditor, 'loadData');
      confirmEmailStub = stubRestApi('confirmEmail').returns(
        new Promise(resolve => {
          resolveConfirm = resolve;
        })
      );

      element.params = {view: GerritView.SETTINGS, emailToken: 'foo'};
      element.firstUpdated();
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
        {message: 'bar', showDismiss: true}
      );
    });
  });
});

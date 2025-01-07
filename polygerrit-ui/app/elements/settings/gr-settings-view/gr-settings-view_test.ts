/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-settings-view';
import {GrSettingsView} from './gr-settings-view';
import {stubRestApi, waitEventLoop} from '../../../test/test-utils';
import {
  AuthInfo,
  AccountDetailInfo,
  EmailAddress,
  PreferencesInfo,
  ServerInfo,
  TopMenuItemInfo,
} from '../../../types/common';
import {
  DateFormat,
  DefaultBase,
  DiffViewMode,
  EmailFormat,
  EmailStrategy,
  AppTheme,
  TimeFormat,
} from '../../../constants/constants';
import {
  createAccountDetailWithId,
  createPreferences,
  createServerInfo,
} from '../../../test/test-data-generators';
import {fixture, html, assert} from '@open-wc/testing';

suite('gr-settings-view tests', () => {
  let element: GrSettingsView;
  let account: AccountDetailInfo;
  let preferences: PreferencesInfo;
  let config: ServerInfo;

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
    element = await fixture(html`<gr-settings-view></gr-settings-view>`);

    // Allow the element to render.
    if (element._testOnly_loadingPromise)
      await element._testOnly_loadingPromise;
    await element.updateComplete;
  });

  test('renders', async () => {
    await element.updateComplete;
    // this cannot be formatted with /* HTML */, because it breaks test
    assert.shadowDom.equal(
      element,
      /* HTML*/ `<div
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
            <gr-button
              aria-disabled="false"
              class="account-button"
              role="button"
              tabindex="0"
            >
              Delete Account
            </gr-button>
            <gr-button
              aria-disabled="false"
              class="account-button"
              role="button"
              tabindex="0"
            >
              Dump Account State
            </gr-button>
            <dialog id="confirm-account-deletion">
              <gr-dialog role="dialog">
                <div
                  class="confirm-account-deletion-header"
                  slot="header"
                >
                Are you sure you wish to delete your account?
                </div>
                <div
                  class="confirm-account-deletion-main"
                  slot="main"
                >
                  <ul>
                    <li>
                      Deleting your account is not reversible.
                    </li>
                    <li>
                      Deleting your account will not delete your changes.
                    </li>
                  </ul>
                </div>
              </gr-dialog>
            </dialog>
            <dialog id="dump-account-state">
              <gr-dialog
                cancel-label=""
                confirm-label="OK"
                confirm-on-enter=""
                role="dialog"
              >
                <div slot="header">
                  Account State:
                </div>
                <div slot="main">
                  <textarea
                    class="account-state-output"
                    readonly=""
                  >
                    <!----><!---->
                  </textarea>
                  <p class="account-state-note">
                    Note: The account state may contain sensitive data (e.g.
                    deadnames). Share it with others only on a need to know
                    basis (e.g. for debugging account or permission issues).
                  </p>
                </div>
              </gr-dialog>
            </dialog>
          </fieldset>
          <gr-preferences id="Preferences"> </gr-preferences>
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
          <gr-endpoint-decorator name="settings-screen">
          </gr-endpoint-decorator>
        </div>
      </div>`
    );
  });

  test('calls the title-change event', async () => {
    const titleChangedStub = sinon.stub();
    const newElement = document.createElement('gr-settings-view');
    document.addEventListener('title-change', titleChangedStub);

    const div = await fixture(html`<div></div>`);
    div.appendChild(newElement);

    await waitEventLoop();

    assert.isTrue(titleChangedStub.called);
    assert.equal(titleChangedStub.getCall(0).args[0].detail.title, 'Settings');
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

  suite('when email verification token is provided', () => {
    let resolveConfirm: (
      value: string | PromiseLike<string | null> | null
    ) => void;
    let confirmEmailStub: sinon.SinonStub;

    setup(() => {
      confirmEmailStub = stubRestApi('confirmEmail').returns(
        new Promise(resolve => {
          resolveConfirm = resolve;
        })
      );

      element.emailToken = 'foo';
      element.confirmEmail();
    });

    test('it is used to confirm email via rest API', () => {
      assert.isTrue(confirmEmailStub.calledOnce);
      assert.isTrue(confirmEmailStub.calledWith('foo'));
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

/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-registration-dialog';
import {GrRegistrationDialog} from './gr-registration-dialog';
import {queryAndAssert, stubRestApi} from '../../../test/test-utils';
import {AccountDetailInfo, Timestamp} from '../../../types/common';
import {AuthType, EditableAccountField} from '../../../constants/constants';
import {
  createAccountWithId,
  createServerInfo,
} from '../../../test/test-data-generators';
import {fixture, html} from '@open-wc/testing-helpers';
import {GrButton} from '../../shared/gr-button/gr-button';

suite('gr-registration-dialog tests', () => {
  let element: GrRegistrationDialog;
  let account: AccountDetailInfo;

  let _listeners: {[key: string]: EventListenerOrEventListenerObject};

  setup(async () => {
    _listeners = {};

    account = {
      name: 'name',
      display_name: 'display name',
      registered_on: '2018-02-08 18:49:18.000000000' as Timestamp,
    };

    stubRestApi('getAccount').returns(
      Promise.resolve({
        ...account,
      })
    );
    stubRestApi('setAccountName').callsFake(name => {
      account.name = name;
      return Promise.resolve();
    });
    stubRestApi('setAccountUsername').callsFake(username => {
      account.username = username;
      return Promise.resolve();
    });
    stubRestApi('setAccountDisplayName').callsFake(displayName => {
      account.display_name = displayName;
      return Promise.resolve();
    });
    stubRestApi('getConfig').returns(
      Promise.resolve({
        ...createServerInfo(),
        auth: {
          auth_type: AuthType.HTTP,
          editable_account_fields: [
            EditableAccountField.USER_NAME,
            EditableAccountField.FULL_NAME,
          ],
        },
      })
    );

    element = await fixture<GrRegistrationDialog>(
      html`<gr-registration-dialog></gr-registration-dialog>`
    );

    await element.loadData();
    await element.updateComplete;
  });

  teardown(() => {
    for (const [eventType, listeners] of Object.entries(_listeners)) {
      element.removeEventListener(eventType, listeners);
    }
  });

  function listen(eventType: string): Promise<void> {
    return new Promise(resolve => {
      _listeners[eventType] = function () {
        resolve();
      };
      element.addEventListener(eventType, _listeners[eventType]);
    });
  }

  function save() {
    const promise = listen('account-detail-update');
    queryAndAssert<GrButton>(element, '#saveButton').click();
    return promise;
  }

  function close(opt_action?: Function) {
    const promise = listen('close');
    if (opt_action) {
      opt_action();
    } else {
      queryAndAssert<GrButton>(element, '#closeButton').click();
    }
    return promise;
  }

  test('renders', () => {
    // cannot format with /* HTML */, because it breaks test
    expect(element).shadowDom.to.equal(/* HTML*/ `<div
      class="container gr-form-styles"
    >
      <header>Please confirm your contact information</header>
      <div class="loadingMessage">Loading...</div>
      <main>
        <p>
        The following contact information was automatically obtained when you
          signed in to the site. This information is used to display who you are
          to others, and to send updates to code reviews you have either started
          or subscribed to.
        </p>
        <hr />
        <section>
          <span class="title"> Full Name </span>
          <span class="value">
            <iron-input>
              <input id="name">
            </iron-input>
          </span>
        </section>
        <section>
          <span class="title"> Display Name </span>
          <span class="value">
            <iron-input> <input id="displayName" /> </iron-input>
          </span>
        </section>
        <section>
          <span class="title"> Username </span>
          <span class="value">
            <iron-input>
              <input id="username">
            </iron-input>
          </span>
        </section>
        <hr />
        <p>
          More configuration options for Gerrit may be found in the
          <a> settings </a> .
        </p>
      </main>
      <footer>
        <gr-button
          aria-disabled="false"
          id="closeButton"
          link=""
          role="button"
          tabindex="0"
        >
          Close
        </gr-button>
        <gr-button
          aria-disabled="false"
          id="saveButton"
          link=""
          primary=""
          role="button"
          tabindex="0"
        >
          Save
        </gr-button>
      </footer>
    </div>`);
  });

  test('fires the close event on close', async () => {
    await close();
  });

  test('fires the close event on save', async () => {
    await close(() => {
      queryAndAssert<GrButton>(element, '#saveButton').click();
    });
  });

  test('saves account details', async () => {
    await element.updateComplete;

    element.account.username = '';
    element.hasUsernameChange = false;
    await element.updateComplete;
    assert.isTrue(element.usernameMutable);

    element.username = 'new username';
    element.hasUsernameChange = true;
    element.account.name = 'new name';
    element.hasNameChange = true;
    element.account.display_name = 'new display name';
    element.hasDisplayNameChange = true;
    await element.updateComplete;

    // Nothing should be committed yet.
    assert.equal(account.name, 'name');
    assert.isNotOk(account.username);
    assert.equal(account.display_name, 'display name');

    // Save and verify new values are committed.
    await save();
    assert.equal(account.name, 'new name');
    assert.equal(account.username, 'new username');
    assert.equal(account.display_name, 'new display name');
  });

  test('save btn disabled', async () => {
    element.account = {};
    element.saving = false;
    await element.updateComplete;
    assert.isTrue(element.computeSaveDisabled());
    element.account = {
      ...createAccountWithId(),
      display_name: 'test',
      name: 'test',
    };
    element.username = 'test';
    element.saving = true;
    await element.updateComplete;
    assert.isTrue(element.computeSaveDisabled());
    element.saving = false;
    await element.updateComplete;
    assert.isFalse(element.computeSaveDisabled());
  });

  test('_computeUsernameEditable', async () => {
    element.serverConfig = {
      ...createServerInfo(),
      auth: {
        auth_type: AuthType.HTTP,
        editable_account_fields: [EditableAccountField.USER_NAME],
      },
    };
    await element.updateComplete;
    assert.isTrue(element.computeUsernameEditable());
    element.serverConfig = {
      ...createServerInfo(),
      auth: {
        auth_type: AuthType.HTTP,
        editable_account_fields: [],
      },
    };
    await element.updateComplete;
    assert.isFalse(element.computeUsernameEditable());
  });
});

/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-account-info';
import {query, queryAll, stubRestApi} from '../../../test/test-utils';
import {GrAccountInfo} from './gr-account-info';
import {AccountDetailInfo, ServerInfo} from '../../../types/common';
import {
  createAccountDetailWithId,
  createAccountWithIdNameAndEmail,
  createAuth,
  createPreferences,
  createServerInfo,
} from '../../../test/test-data-generators';
import {IronInputElement} from '@polymer/iron-input';
import {SinonStubbedMember} from 'sinon';
import {RestApiService} from '../../../services/gr-rest-api/gr-rest-api';
import {EditableAccountField} from '../../../api/rest-api';

const basicFixture = fixtureFromElement('gr-account-info');

suite('gr-account-info tests', () => {
  let element!: GrAccountInfo;
  let account: AccountDetailInfo;
  let config: ServerInfo;

  function queryIronInput(selector: string): IronInputElement {
    const input = query<IronInputElement>(element, selector);
    if (!input) assert.fail(`<iron-input> with id ${selector} not found.`);
    return input;
  }

  function valueOf(title: string): Element {
    const sections = queryAll<HTMLElement>(element, 'section') ?? [];
    let titleEl;
    for (let i = 0; i < sections.length; i++) {
      titleEl = sections[i].querySelector('.title');
      if (titleEl?.textContent === title) {
        const el = sections[i].querySelector('.value');
        if (el) return el;
      }
    }
    assert.fail(`element with title ${title} not found`);
  }

  setup(async () => {
    account = createAccountWithIdNameAndEmail(123) as AccountDetailInfo;
    config = createServerInfo();

    stubRestApi('getAccount').resolves(account);
    stubRestApi('getConfig').resolves(config);
    stubRestApi('getPreferences').resolves(createPreferences());

    element = basicFixture.instantiate();
    await element.loadData();
    await element.updateComplete;
  });

  test('renders', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `
      <div class="gr-form-styles">
        <section>
          <span class="title"></span>
          <span class="value">
            <gr-avatar hidden="" imagesize="120"></gr-avatar>
          </span>
        </section>
        <section>
          <span class="title">ID</span>
          <span class="value">123</span>
        </section>
        <section>
          <span class="title">Email</span>
          <span class="value">user-123@</span>
        </section>
        <section>
          <span class="title">Registered</span>
          <span class="value">
            <gr-date-formatter withtooltip=""></gr-date-formatter>
          </span>
        </section>
        <section id="usernameSection">
          <span class="title">Username</span>
          <span class="value"></span>
        </section>
        <section id="nameSection">
          <label class="title" for="nameInput">Full name</label>
          <span class="value">User-123</span>
        </section>
        <section>
          <label class="title" for="displayNameInput">Display name</label>
          <span class="value">
            <iron-input>
              <input id="displayNameInput" />
            </iron-input>
          </span>
        </section>
        <section>
          <label class="title" for="statusInput">
            About me (e.g. employer)
          </label>
          <span class="value">
            <iron-input id="statusIronInput">
              <input id="statusInput" />
            </iron-input>
          </span>
        </section>
      </div>
    `);
  });

  test('basic account info render', () => {
    assert.isFalse(element.loading);

    assert.equal(valueOf('ID').textContent, `${account._account_id}`);
    assert.equal(valueOf('Email').textContent, account.email);
    assert.equal(valueOf('Username').textContent, account.username);
  });

  test('full name render (immutable)', () => {
    const section = query<HTMLElement>(element, '#nameSection')!;
    const displaySpan = section.querySelectorAll('.value')[0];
    const inputSpan = section.querySelectorAll('.value')[1];

    assert.isFalse(element.nameMutable);
    assert.isFalse(displaySpan.hasAttribute('hidden'));
    assert.equal(displaySpan.textContent, account.name);
    assert.isUndefined(inputSpan);
  });

  test('full name render (mutable)', async () => {
    element.serverConfig = {
      ...createServerInfo(),
      auth: {
        ...createAuth(),
        editable_account_fields: [EditableAccountField.FULL_NAME],
      },
    };

    await element.updateComplete;
    const section = query<HTMLElement>(element, '#nameSection')!;
    const inputSpan = section.querySelectorAll('.value')[0];

    assert.isTrue(element.nameMutable);
    assert.equal(queryIronInput('#nameIronInput').bindValue, account.name);
    assert.isFalse(inputSpan.hasAttribute('hidden'));
  });

  test('username render (immutable)', () => {
    const section = query<HTMLElement>(element, '#usernameSection')!;
    const displaySpan = section.querySelectorAll('.value')[0];
    const inputSpan = section.querySelectorAll('.value')[1];

    assert.isFalse(element.computeUsernameEditable());
    assert.isFalse(displaySpan.hasAttribute('hidden'));
    assert.equal(displaySpan.textContent, account.username);
    assert.isUndefined(inputSpan);
  });

  test('username render (mutable)', async () => {
    element.serverConfig = {
      ...createServerInfo(),
      auth: {
        ...createAuth(),
        editable_account_fields: [EditableAccountField.USER_NAME],
      },
    };
    element.account!.username = '';
    element.username = '';

    await element.updateComplete;

    const section = query<HTMLElement>(element, '#usernameSection')!;
    const inputSpan = section.querySelectorAll('.value')[0];

    assert.isTrue(element.computeUsernameEditable());
    assert.equal(
      queryIronInput('#usernameIronInput').bindValue,
      account.username
    );
    assert.isFalse(inputSpan.hasAttribute('hidden'));
  });

  suite('account info edit', () => {
    let nameStub: SinonStubbedMember<RestApiService['setAccountName']>;
    let usernameStub: SinonStubbedMember<RestApiService['setAccountUsername']>;
    let statusStub: SinonStubbedMember<RestApiService['setAccountStatus']>;

    setup(async () => {
      element.serverConfig = {
        ...createServerInfo(),
        auth: {
          ...createAuth(),
          editable_account_fields: [
            EditableAccountField.FULL_NAME,
            EditableAccountField.USER_NAME,
          ],
        },
      };

      await element.updateComplete;
      nameStub = stubRestApi('setAccountName').resolves();
      usernameStub = stubRestApi('setAccountUsername').resolves();
      statusStub = stubRestApi('setAccountStatus').resolves();
    });

    test('name', async () => {
      assert.isTrue(element.nameMutable);
      assert.isFalse(element.hasUnsavedChanges);

      const statusInputEl = queryIronInput('#nameIronInput');
      statusInputEl.bindValue = 'new name';
      await element.updateComplete;
      assert.isTrue(element.hasNameChange);
      assert.isFalse(element.hasStatusChange);
      assert.isTrue(element.hasUnsavedChanges);

      await element.save();
      assert.isFalse(usernameStub.called);
      assert.isTrue(nameStub.called);
      assert.isFalse(statusStub.called);
      await nameStub.lastCall.returnValue;
      assert.equal(nameStub.lastCall.args[0], 'new name');
    });

    test('username', async () => {
      element.account!.username = '';
      element.username = 't';
      element.hasUsernameChange = false;
      element.serverConfig = {
        ...createServerInfo(),
        auth: {
          ...createAuth(),
          editable_account_fields: [EditableAccountField.USER_NAME],
        },
      };
      await element.updateComplete;
      assert.isTrue(element.computeUsernameEditable());

      const statusInputEl = queryIronInput('#usernameIronInput');
      statusInputEl.bindValue = 'new username';
      await element.updateComplete;
      assert.isTrue(element.hasUsernameChange);
      assert.isFalse(element.hasNameChange);
      assert.isFalse(element.hasStatusChange);
      assert.isTrue(element.hasUnsavedChanges);

      await element.save();
      assert.isTrue(usernameStub.called);
      assert.isFalse(nameStub.called);
      assert.isFalse(statusStub.called);
      await usernameStub.lastCall.returnValue;
      assert.equal(usernameStub.lastCall.args[0], 'new username');
    });

    test('status', async () => {
      assert.isFalse(element.hasUnsavedChanges);

      const statusInputEl = queryIronInput('#statusIronInput');
      statusInputEl.bindValue = 'new status';
      await element.updateComplete;
      assert.isFalse(element.hasNameChange);
      assert.isTrue(element.hasStatusChange);
      assert.isTrue(element.hasUnsavedChanges);

      await element.save();
      assert.isFalse(usernameStub.called);
      assert.isTrue(statusStub.called);
      assert.isFalse(nameStub.called);
      await statusStub.lastCall.returnValue;
      assert.equal(statusStub.lastCall.args[0], 'new status');
    });
  });

  suite('edit name and status', () => {
    let nameStub: SinonStubbedMember<RestApiService['setAccountName']>;
    let statusStub: SinonStubbedMember<RestApiService['setAccountStatus']>;

    setup(async () => {
      element.serverConfig = {
        ...createServerInfo(),
        auth: {
          ...createAuth(),
          editable_account_fields: [EditableAccountField.FULL_NAME],
        },
      };
      await element.updateComplete;

      nameStub = stubRestApi('setAccountName').resolves();
      statusStub = stubRestApi('setAccountStatus').resolves();
      stubRestApi('setAccountUsername').resolves();
    });

    test('set name and status', async () => {
      assert.isTrue(element.nameMutable);
      assert.isFalse(element.hasUnsavedChanges);

      const inputEl = queryIronInput('#nameIronInput');
      inputEl.bindValue = 'new name';
      await element.updateComplete;
      assert.isTrue(element.hasNameChange);

      const statusInputEl = queryIronInput('#statusIronInput');
      statusInputEl.bindValue = 'new status';
      await element.updateComplete;
      assert.isTrue(element.hasStatusChange);

      assert.isTrue(element.hasUnsavedChanges);

      await element.save();
      assert.isTrue(statusStub.called);
      assert.isTrue(nameStub.called);

      assert.equal(nameStub.lastCall.args[0], 'new name');

      assert.equal(statusStub.lastCall.args[0], 'new status');
    });
  });

  suite('set status but read name', () => {
    let statusStub: SinonStubbedMember<RestApiService['setAccountStatus']>;

    setup(async () => {
      element.serverConfig = {
        ...createServerInfo(),
        auth: {
          ...createAuth(),
          editable_account_fields: [],
        },
      };
      await element.updateComplete;

      statusStub = stubRestApi('setAccountStatus').resolves();
    });

    test('read full name but set status', async () => {
      const section = query<HTMLElement>(element, '#nameSection')!;
      const displaySpan = section.querySelectorAll('.value')[0];
      const inputSpan = section.querySelectorAll('.value')[1];

      assert.isFalse(element.nameMutable);

      assert.isFalse(element.hasUnsavedChanges);

      assert.isFalse(displaySpan.hasAttribute('hidden'));
      assert.equal(displaySpan.textContent, account.name);
      assert.isUndefined(inputSpan);

      const inputEl = queryIronInput('#statusIronInput');
      inputEl.bindValue = 'new status';
      await element.updateComplete;
      assert.isTrue(element.hasStatusChange);

      assert.isTrue(element.hasUnsavedChanges);

      await element.save();
      assert.isTrue(statusStub.called);
      await statusStub.lastCall.returnValue;
      assert.equal(statusStub.lastCall.args[0], 'new status');
    });
  });

  test('_usernameChanged compares usernames with loose equality', async () => {
    element.serverConfig = {
      ...createServerInfo(),
      auth: {
        ...createAuth(),
        editable_account_fields: [EditableAccountField.USER_NAME],
      },
    };
    element.account = createAccountDetailWithId();
    element.username = 't';
    element.hasUsernameChange = false;
    element.loading = false;
    // usernameChanged is an observer, but call it here after setting
    // hasUsernameChange in the test to force recomputation.
    await element.updateComplete;
    assert.isFalse(element.hasUsernameChange);

    const inputEl = queryIronInput('#usernameIronInput');
    inputEl.bindValue = 'test';
    await element.updateComplete;

    assert.isTrue(element.hasUsernameChange);
  });
});

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
    await flush();
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
            <iron-input>
              <input id="statusInput" />
            </iron-input>
          </span>
        </section>
      </div>
    `);
  });

  test('basic account info render', () => {
    assert.isFalse(element._loading);

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
    element._serverConfig = {
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

    assert.isFalse(element.usernameMutable);
    assert.isFalse(displaySpan.hasAttribute('hidden'));
    assert.equal(displaySpan.textContent, account.username);
    assert.isUndefined(inputSpan);
  });

  test('username render (mutable)', async () => {
    element._serverConfig = {
      ...createServerInfo(),
      auth: {
        ...createAuth(),
        editable_account_fields: [EditableAccountField.USER_NAME],
      },
    };
    element._account!.username = '';
    element._username = '';

    await element.updateComplete;

    const section = query<HTMLElement>(element, '#usernameSection')!;
    const inputSpan = section.querySelectorAll('.value')[0];

    assert.isTrue(element.usernameMutable);
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
      // nameChangedSpy = sinon.spy(element, '_nameChanged');
      // usernameChangedSpy = sinon.spy(element, '_usernameChanged');
      // statusChangedSpy = sinon.spy(element, '_statusChanged');
      element._serverConfig = {
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

      element._account!.name = 'new name';

      // assert.isTrue(nameChangedSpy.called);
      // assert.isFalse(statusChangedSpy.called);
      assert.isTrue(element.hasUnsavedChanges);

      await element.save();
      assert.isFalse(usernameStub.called);
      assert.isTrue(nameStub.called);
      assert.isFalse(statusStub.called);
      await nameStub.lastCall.returnValue;
      assert.equal(nameStub.lastCall.args[0], 'new name');
    });

    test('username', async () => {
      element._account!.username = '';
      element._hasUsernameChange = false;
      assert.isTrue(element.usernameMutable);

      element._username = 'new username';

      // assert.isTrue(usernameChangedSpy.called);
      // assert.isFalse(statusChangedSpy.called);
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

      element._account!.status = 'new status';

      // assert.isFalse(nameChangedSpy.called);
      // assert.isTrue(statusChangedSpy.called);
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

    setup(() => {
      element._serverConfig = {
        ...createServerInfo(),
        auth: {
          ...createAuth(),
          editable_account_fields: [EditableAccountField.FULL_NAME],
        },
      };

      nameStub = stubRestApi('setAccountName').resolves();
      statusStub = stubRestApi('setAccountStatus').resolves();
      stubRestApi('setAccountUsername').resolves();
    });

    test('set name and status', async () => {
      assert.isTrue(element.nameMutable);
      assert.isFalse(element.hasUnsavedChanges);

      element._account!.name = 'new name';

      // assert.isTrue(nameChangedSpy.called);

      element._account!.status = 'new status';

      // assert.isTrue(statusChangedSpy.called);

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

    setup(() => {
      element._serverConfig = {
        ...createServerInfo(),
        auth: {
          ...createAuth(),
          editable_account_fields: [],
        },
      };

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
      assert.isTrue(inputSpan.hasAttribute('hidden'));

      element._account!.status = 'new status';

      // assert.isTrue(statusChangedSpy.called);

      assert.isTrue(element.hasUnsavedChanges);

      await element.save();
      assert.isTrue(statusStub.called);
      await statusStub.lastCall.returnValue;
      assert.equal(statusStub.lastCall.args[0], 'new status');
    });
  });

  test('_usernameChanged compares usernames with loose equality', async () => {
    element._serverConfig = {
      ...createServerInfo(),
      auth: {
        ...createAuth(),
        editable_account_fields: [EditableAccountField.USER_NAME],
      },
    };
    element._account = createAccountDetailWithId();
    element._username = 't';
    element._hasUsernameChange = false;
    element._loading = false;
    // _usernameChanged is an observer, but call it here after setting
    // _hasUsernameChange in the test to force recomputation.
    await element.updateComplete;
    assert.isFalse(element._hasUsernameChange);

    const inputEl = queryIronInput('#usernameIronInput');
    inputEl.bindValue = 'test';
    // element._username = 'test';
    await element.updateComplete;

    assert.isTrue(element._hasUsernameChange);
  });
});

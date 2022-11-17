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
import {SinonSpyMember, stubRestApi} from '../../../test/test-utils';
import {GrAccountInfo} from './gr-account-info';
import {AccountDetailInfo, ServerInfo} from '../../../types/common';
import {
  createAccountDetailWithId,
  createAccountWithIdNameEmailAndDisplayname,
  createPreferences,
  createServerInfo,
} from '../../../test/test-data-generators';
import {IronInputElement} from '@polymer/iron-input';
import {SinonStubbedMember} from 'sinon';
import {RestApiService} from '../../../services/gr-rest-api/gr-rest-api';

const basicFixture = fixtureFromElement('gr-account-info');

suite('gr-account-info tests', () => {
  let element!: GrAccountInfo;
  let account: AccountDetailInfo;
  let config: ServerInfo;

  function queryIronInput(selector: string): IronInputElement {
    const input = element.root?.querySelector<IronInputElement>(selector);
    if (!input) assert.fail(`<iron-input> with id ${selector} not found.`);
    return input;
  }

  function valueOf(title: string): Element {
    const sections = element.root?.querySelectorAll('section') ?? [];
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
    account = createAccountWithIdNameEmailAndDisplayname(123) as AccountDetailInfo;
    config = createServerInfo();

    stubRestApi('getAccount').returns(Promise.resolve(account));
    stubRestApi('getConfig').returns(Promise.resolve(config));
    stubRestApi('getPreferences').returns(Promise.resolve(createPreferences()));

    element = basicFixture.instantiate();
    await element.loadData();
    await flush();
  });

  test('basic account info render', () => {
    assert.isFalse(element._loading);

    assert.equal(valueOf('ID').textContent, `${account._account_id}`);
    assert.equal(valueOf('Email').textContent, account.email);
    assert.equal(valueOf('Username').textContent, account.username);
  });

  test('full name render (immutable)', () => {
    const section = element.$.nameSection;
    const displaySpan = section.querySelectorAll('.value')[0];
    const inputSpan = section.querySelectorAll('.value')[1];

    assert.isFalse(element.nameMutable);
    assert.isFalse(displaySpan.hasAttribute('hidden'));
    assert.equal(displaySpan.textContent, account.name);
    assert.isTrue(inputSpan.hasAttribute('hidden'));
  });

  test('full name render (mutable)', () => {
    element.set('_serverConfig', {
      auth: {editable_account_fields: ['FULL_NAME']},
    });

    const section = element.$.nameSection;
    const displaySpan = section.querySelectorAll('.value')[0];
    const inputSpan = section.querySelectorAll('.value')[1];

    assert.isTrue(element.nameMutable);
    assert.isTrue(displaySpan.hasAttribute('hidden'));
    assert.equal(queryIronInput('#nameIronInput').bindValue, account.name);
    assert.isFalse(inputSpan.hasAttribute('hidden'));
  });

  test('username render (immutable)', () => {
    const section = element.$.usernameSection;
    const displaySpan = section.querySelectorAll('.value')[0];
    const inputSpan = section.querySelectorAll('.value')[1];

    assert.isFalse(element.usernameMutable);
    assert.isFalse(displaySpan.hasAttribute('hidden'));
    assert.equal(displaySpan.textContent, account.username);
    assert.isTrue(inputSpan.hasAttribute('hidden'));
  });

  test('username render (mutable)', () => {
    element.set('_serverConfig', {
      auth: {editable_account_fields: ['USER_NAME']},
    });
    element.set('_account.username', '');
    element.set('_username', '');

    const section = element.$.usernameSection;
    const displaySpan = section.querySelectorAll('.value')[0];
    const inputSpan = section.querySelectorAll('.value')[1];

    assert.isTrue(element.usernameMutable);
    assert.isTrue(displaySpan.hasAttribute('hidden'));
    assert.equal(
      queryIronInput('#usernameIronInput').bindValue,
      account.username
    );
    assert.isFalse(inputSpan.hasAttribute('hidden'));
  });

  test('displayname render (immutable)', () => {
    const section = element.$.displayNameSection;
    const displaySpan = section.querySelectorAll('.value')[0];
    const inputSpan = section.querySelectorAll('.value')[1];

    assert.isFalse(element.displayNameMutable);
    assert.isFalse(displaySpan.hasAttribute('hidden'));
    assert.equal(displaySpan.textContent, account.display_name);
    assert.isTrue(inputSpan.hasAttribute('hidden'));
  });

  test('displayname render (mutable)', () => {
    element.set('_serverConfig', {
      auth: {editable_account_fields: ['DISPLAY_NAME']},
    });
    element.set('_account.display_name', '');
    element.set('_display_name', '');

    const section = element.$.displayNameSection;
    const displaySpan = section.querySelectorAll('.value')[0];
    const inputSpan = section.querySelectorAll('.value')[1];

    assert.isTrue(element.displayNameMutable);
    assert.isTrue(displaySpan.hasAttribute('hidden'));
    assert.equal(
      queryIronInput('#displayNameIronInput').bindValue,
      account.display_name
    );
    assert.isFalse(inputSpan.hasAttribute('hidden'));
  });

  suite('account info edit', () => {
    let nameChangedSpy: SinonSpyMember<typeof element._nameChanged>;
    let usernameChangedSpy: SinonSpyMember<typeof element._usernameChanged>;
    let statusChangedSpy: SinonSpyMember<typeof element._statusChanged>;
    let nameStub: SinonStubbedMember<RestApiService['setAccountName']>;
    let usernameStub: SinonStubbedMember<RestApiService['setAccountUsername']>;
    let statusStub: SinonStubbedMember<RestApiService['setAccountStatus']>;

    setup(() => {
      nameChangedSpy = sinon.spy(element, '_nameChanged');
      usernameChangedSpy = sinon.spy(element, '_usernameChanged');
      statusChangedSpy = sinon.spy(element, '_statusChanged');
      element.set('_serverConfig', {
        auth: {editable_account_fields: ['FULL_NAME', 'USER_NAME']},
      });

      nameStub = stubRestApi('setAccountName').returns(Promise.resolve());
      usernameStub = stubRestApi('setAccountUsername').returns(
        Promise.resolve()
      );
      statusStub = stubRestApi('setAccountStatus').returns(Promise.resolve());
    });

    test('name', async () => {
      assert.isTrue(element.nameMutable);
      assert.isFalse(element.hasUnsavedChanges);

      element.set('_account.name', 'new name');

      assert.isTrue(nameChangedSpy.called);
      assert.isFalse(statusChangedSpy.called);
      assert.isTrue(element.hasUnsavedChanges);

      await element.save();
      assert.isFalse(usernameStub.called);
      assert.isTrue(nameStub.called);
      assert.isFalse(statusStub.called);
      await nameStub.lastCall.returnValue;
      assert.equal(nameStub.lastCall.args[0], 'new name');
    });

    test('username', async () => {
      element.set('_account.username', '');
      element._hasUsernameChange = false;
      assert.isTrue(element.usernameMutable);

      element.set('_username', 'new username');

      assert.isTrue(usernameChangedSpy.called);
      assert.isFalse(statusChangedSpy.called);
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

      element.set('_account.status', 'new status');

      assert.isFalse(nameChangedSpy.called);
      assert.isTrue(statusChangedSpy.called);
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
    let nameChangedSpy: SinonSpyMember<typeof element._nameChanged>;
    let statusChangedSpy: SinonSpyMember<typeof element._statusChanged>;
    let nameStub: SinonStubbedMember<RestApiService['setAccountName']>;
    let statusStub: SinonStubbedMember<RestApiService['setAccountStatus']>;

    setup(() => {
      nameChangedSpy = sinon.spy(element, '_nameChanged');
      statusChangedSpy = sinon.spy(element, '_statusChanged');
      element.set('_serverConfig', {
        auth: {editable_account_fields: ['FULL_NAME']},
      });

      nameStub = stubRestApi('setAccountName').returns(Promise.resolve());
      statusStub = stubRestApi('setAccountStatus').returns(Promise.resolve());
      stubRestApi('setAccountUsername').returns(Promise.resolve());
    });

    test('set name and status', async () => {
      assert.isTrue(element.nameMutable);
      assert.isFalse(element.hasUnsavedChanges);

      element.set('_account.name', 'new name');

      assert.isTrue(nameChangedSpy.called);

      element.set('_account.status', 'new status');

      assert.isTrue(statusChangedSpy.called);

      assert.isTrue(element.hasUnsavedChanges);

      await element.save();
      assert.isTrue(statusStub.called);
      assert.isTrue(nameStub.called);

      assert.equal(nameStub.lastCall.args[0], 'new name');

      assert.equal(statusStub.lastCall.args[0], 'new status');
    });
  });

  suite('set status but read name', () => {
    let statusChangedSpy: SinonSpyMember<typeof element._statusChanged>;
    let statusStub: SinonStubbedMember<RestApiService['setAccountStatus']>;

    setup(() => {
      statusChangedSpy = sinon.spy(element, '_statusChanged');
      element.set('_serverConfig', {auth: {editable_account_fields: []}});

      statusStub = stubRestApi('setAccountStatus').returns(Promise.resolve());
    });

    test('read full name but set status', async () => {
      const section = element.$.nameSection;
      const displaySpan = section.querySelectorAll('.value')[0];
      const inputSpan = section.querySelectorAll('.value')[1];

      assert.isFalse(element.nameMutable);

      assert.isFalse(element.hasUnsavedChanges);

      assert.isFalse(displaySpan.hasAttribute('hidden'));
      assert.equal(displaySpan.textContent, account.name);
      assert.isTrue(inputSpan.hasAttribute('hidden'));

      element.set('_account.status', 'new status');

      assert.isTrue(statusChangedSpy.called);

      assert.isTrue(element.hasUnsavedChanges);

      await element.save();
      assert.isTrue(statusStub.called);
      await statusStub.lastCall.returnValue;
      assert.equal(statusStub.lastCall.args[0], 'new status');
    });
  });

  test('_usernameChanged compares usernames with loose equality', () => {
    element._account = createAccountDetailWithId();
    element._username = '';
    element._hasUsernameChange = false;
    element._loading = false;
    // _usernameChanged is an observer, but call it here after setting
    // _hasUsernameChange in the test to force recomputation.
    element._usernameChanged();
    flush();

    assert.isFalse(element._hasUsernameChange);

    element.set('_username', 'test');
    flush();

    assert.isTrue(element._hasUsernameChange);
  });

  test('_hideAvatarChangeUrl', () => {
    assert.equal(element._hideAvatarChangeUrl(''), 'hide');

    assert.equal(element._hideAvatarChangeUrl('https://example.com'), '');
  });
});

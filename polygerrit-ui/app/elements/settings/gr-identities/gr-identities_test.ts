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
import './gr-identities';
import {GrIdentities} from './gr-identities';
import {AuthType} from '../../../constants/constants';
import {stubRestApi} from '../../../test/test-utils';
import {ServerInfo} from '../../../types/common';
import {createServerInfo} from '../../../test/test-data-generators';
import {queryAll, queryAndAssert} from '../../../test/test-utils';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';

const basicFixture = fixtureFromElement('gr-identities');

suite('gr-identities tests', () => {
  let element: GrIdentities;

  const ids = [
    {
      identity: 'username:john',
      email_address: 'john.doe@example.com',
      trusted: true,
    },
    {
      identity: 'gerrit:gerrit',
      email_address: 'gerrit@example.com',
    },
    {
      identity: 'mailto:gerrit2@example.com',
      email_address: 'gerrit2@example.com',
      trusted: true,
      can_delete: true,
    },
  ];

  setup(async () => {
    stubRestApi('getExternalIds').returns(Promise.resolve(ids));

    element = basicFixture.instantiate();
    await element.loadData();
    await flush();
  });

  test('renders', () => {
    const rows = Array.from(queryAll(element, 'tbody tr'));

    assert.equal(rows.length, 2);

    const nameCells = rows.map(row => queryAll(row, 'td')[2].textContent);

    assert.equal(nameCells[0]!.trim(), 'gerrit:gerrit');
    assert.equal(nameCells[1]!.trim(), '');
  });

  test('renders email', () => {
    const rows = Array.from(queryAll(element, 'tbody tr'));

    assert.equal(rows.length, 2);

    const nameCells = rows.map(row => queryAll(row, 'td')[1]!.textContent);

    assert.equal(nameCells[0]!, 'gerrit@example.com');
    assert.equal(nameCells[1]!, 'gerrit2@example.com');
  });

  test('_computeIdentity', () => {
    assert.equal(element._computeIdentity(ids[0].identity), 'username:john');
    assert.equal(element._computeIdentity(ids[2].identity), '');
  });

  test('filterIdentities', () => {
    assert.isFalse(element.filterIdentities(ids[0]));

    assert.isTrue(element.filterIdentities(ids[1]));
  });

  test('delete id', done => {
    element._idName = 'mailto:gerrit2@example.com';
    const loadDataStub = sinon.stub(element, 'loadData');
    element._handleDeleteItemConfirm().then(() => {
      assert.isTrue(loadDataStub.called);
      done();
    });
  });

  test('_handleDeleteItem opens modal', () => {
    const deleteBtn = queryAndAssert(element, '.deleteButton');
    const deleteItem = sinon.stub(element, '_handleDeleteItem');
    MockInteractions.tap(deleteBtn);
    assert.isTrue(deleteItem.called);
  });

  test('_computeShowLinkAnotherIdentity', () => {
    const config: ServerInfo = {
      ...createServerInfo(),
    };

    config.auth.auth_type = AuthType.OAUTH;
    assert.isTrue(element._computeShowLinkAnotherIdentity(config));

    config.auth.auth_type = AuthType.OPENID;
    assert.isTrue(element._computeShowLinkAnotherIdentity(config));

    config.auth.auth_type = AuthType.HTTP_LDAP;
    assert.isFalse(element._computeShowLinkAnotherIdentity(config));

    config.auth.auth_type = AuthType.LDAP;
    assert.isFalse(element._computeShowLinkAnotherIdentity(config));

    config.auth.auth_type = AuthType.HTTP;
    assert.isFalse(element._computeShowLinkAnotherIdentity(config));

    assert.isFalse(element._computeShowLinkAnotherIdentity(undefined));
  });

  test('_showLinkAnotherIdentity', () => {
    let config: ServerInfo = {
      ...createServerInfo(),
    };
    config.auth.auth_type = AuthType.OAUTH;

    element.serverConfig = config;

    assert.isTrue(element._showLinkAnotherIdentity);

    config = {
      ...createServerInfo(),
    };
    config.auth.auth_type = AuthType.LDAP;
    element.serverConfig = config;

    assert.isFalse(element._showLinkAnotherIdentity);
  });
});

/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../test/common-test-setup-karma';
import './gr-identities';
import {GrIdentities} from './gr-identities';
import {AuthType} from '../../../constants/constants';
import {stubRestApi} from '../../../test/test-utils';
import {ServerInfo} from '../../../types/common';
import {createServerInfo} from '../../../test/test-data-generators';
import {queryAll, queryAndAssert} from '../../../test/test-utils';
import {GrButton} from '../../shared/gr-button/gr-button';
import {fixture, html} from '@open-wc/testing-helpers';

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

    element = await fixture<GrIdentities>(
      html`<gr-identities></gr-identities>`
    );
    await element.loadData();
    await element.updateComplete;
  });

  test('renders', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `<div class="gr-form-styles">
        <fieldset class="space">
          <table>
            <thead>
              <tr>
                <th class="statusHeader">Status</th>
                <th class="emailAddressHeader">Email Address</th>
                <th class="identityHeader">Identity</th>
                <th class="deleteHeader"></th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td class="statusColumn">Untrusted</td>
                <td class="emailAddressColumn">gerrit@example.com</td>
                <td class="identityColumn">gerrit:gerrit</td>
                <td class="deleteColumn">
                  <gr-button
                    aria-disabled="false"
                    class="deleteButton"
                    data-index="0"
                    role="button"
                    tabindex="0"
                  >
                    Delete
                  </gr-button>
                </td>
              </tr>
              <tr>
                <td class="statusColumn"></td>
                <td class="emailAddressColumn">gerrit2@example.com</td>
                <td class="identityColumn"></td>
                <td class="deleteColumn">
                  <gr-button
                    aria-disabled="false"
                    class="deleteButton show"
                    data-index="1"
                    role="button"
                    tabindex="0"
                  >
                    Delete
                  </gr-button>
                </td>
              </tr>
            </tbody>
          </table>
        </fieldset>
      </div>
      <gr-overlay
        aria-hidden="true"
        id="overlay"
        style="outline: none; display: none;"
        tabindex="-1"
        with-backdrop=""
      >
        <gr-confirm-delete-item-dialog class="confirmDialog" itemtypename="ID">
        </gr-confirm-delete-item-dialog
      ></gr-overlay>`);
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

  test('filterIdentities', () => {
    assert.notInclude(element.getIdentities(), ids[0]);
    assert.include(element.getIdentities(), ids[1]);
  });

  test('delete id', async () => {
    element.idName = 'mailto:gerrit2@example.com';
    const loadDataStub = sinon.stub(element, 'loadData');
    await element.handleDeleteItemConfirm();
    assert.isTrue(loadDataStub.called);
  });

  test('handleDeleteItem opens modal', async () => {
    const deleteBtn = queryAndAssert<GrButton>(element, '.deleteButton');
    deleteBtn.click();
    await element.updateComplete;
    assert.isTrue(element.overlay?.opened);
  });

  test('computeShowLinkAnotherIdentity', () => {
    const config: ServerInfo = {
      ...createServerInfo(),
    };

    config.auth.auth_type = AuthType.OAUTH;
    element.serverConfig = config;
    assert.isTrue(element.computeShowLinkAnotherIdentity());

    config.auth.auth_type = AuthType.OPENID;
    element.serverConfig = config;
    assert.isTrue(element.computeShowLinkAnotherIdentity());

    config.auth.auth_type = AuthType.HTTP_LDAP;
    element.serverConfig = config;
    assert.isFalse(element.computeShowLinkAnotherIdentity());

    config.auth.auth_type = AuthType.LDAP;
    element.serverConfig = config;
    assert.isFalse(element.computeShowLinkAnotherIdentity());

    config.auth.auth_type = AuthType.HTTP;
    element.serverConfig = config;
    assert.isFalse(element.computeShowLinkAnotherIdentity());

    element.serverConfig = undefined;
    assert.isFalse(element.computeShowLinkAnotherIdentity());
  });

  test('showLinkAnotherIdentity', async () => {
    let config: ServerInfo = {
      ...createServerInfo(),
    };
    config.auth.auth_type = AuthType.OAUTH;
    element.serverConfig = config;
    await element.updateComplete;

    assert.isTrue(element.showLinkAnotherIdentity);

    config = {
      ...createServerInfo(),
    };
    config.auth.auth_type = AuthType.LDAP;
    element.serverConfig = config;
    await element.updateComplete;

    assert.isFalse(element.showLinkAnotherIdentity);
  });
});

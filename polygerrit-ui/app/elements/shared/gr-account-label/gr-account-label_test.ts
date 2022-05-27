/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-account-label';
import {
  query,
  queryAndAssert,
  spyRestApi,
  stubRestApi,
} from '../../../test/test-utils';
import {GrAccountLabel} from './gr-account-label';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {AccountDetailInfo, ServerInfo} from '../../../types/common';
import {
  createAccountDetailWithIdNameAndEmail,
  createChange,
  createPluginConfig,
  createServerInfo,
} from '../../../test/test-data-generators';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';

const basicFixture = fixtureFromElement('gr-account-label');

suite('gr-account-label tests', () => {
  let element: GrAccountLabel;
  const kermit: AccountDetailInfo = {
    ...createAccountDetailWithIdNameAndEmail(31),
    name: 'kermit',
  };

  setup(async () => {
    sinon.stub(GerritNav, 'getUrlForOwner').callsFake(() => 'test');
    stubRestApi('getAccount').resolves(kermit);
    stubRestApi('getLoggedIn').resolves(false);
    stubRestApi('getConfig').resolves({
      ...createServerInfo(),
      plugin: {
        ...createPluginConfig(),
        has_avatars: true,
      },
      user: {
        anonymous_coward_name: 'Anonymous Coward',
      },
    });
    element = basicFixture.instantiate();
    await element.updateComplete;
  });

  test('renders', async () => {
    element.account = kermit;
    await element.updateComplete;
    expect(element).shadowDom.to.equal(/* HTML */ `
      <div class="container">
        <gr-hovercard-account for="hovercardTarget"></gr-hovercard-account>
        <span class="hovercardTargetWrapper">
          <gr-avatar hidden="" imagesize="32"> </gr-avatar>
          <span
            class="name"
            id="hovercardTarget"
            part="gr-account-label-text"
            role="button"
            tabindex="0"
          >
            kermit
          </span>
          <gr-endpoint-decorator
            class="accountStatusDecorator"
            name="account-status-icon"
          >
            <gr-endpoint-param name="accountId"></gr-endpoint-param>
            <span class="rightSidePadding"></span>
          </gr-endpoint-decorator>
        </span>
      </div>
    `);
  });

  test('renders clickable', async () => {
    element.account = kermit;
    element.clickable = true;
    await element.updateComplete;
    expect(element).shadowDom.to.equal(/* HTML */ `
      <div class="container">
        <gr-hovercard-account for="hovercardTarget"></gr-hovercard-account>
        <a class="ownerLink" href="test" tabindex="-1">
          <span class="hovercardTargetWrapper">
            <gr-avatar hidden="" imagesize="32"> </gr-avatar>
            <span
              class="name"
              id="hovercardTarget"
              part="gr-account-label-text"
              role="button"
              tabindex="0"
            >
              kermit
            </span>
            <gr-endpoint-decorator
              class="accountStatusDecorator"
              name="account-status-icon"
            >
              <gr-endpoint-param name="accountId"></gr-endpoint-param>
              <span class="rightSidePadding"></span>
            </gr-endpoint-decorator>
          </span>
        </a>
      </div>
    `);
  });

  suite('_computeName', () => {
    test('not showing anonymous', () => {
      const account = {name: 'Wyatt'};
      assert.deepEqual(element.computeName(account, false), 'Wyatt');
    });

    test('showing anonymous but no config', () => {
      const account = {};
      assert.deepEqual(element.computeName(account, false), 'Anonymous');
    });

    test('test for Anonymous Coward user and replace with Anonymous', () => {
      const config: ServerInfo = {
        ...createServerInfo(),
        user: {
          anonymous_coward_name: 'Anonymous Coward',
        },
      };
      const account = {};
      assert.deepEqual(
        element.computeName(account, false, config),
        'Anonymous'
      );
    });

    test('test for anonymous_coward_name', () => {
      const config = {
        ...createServerInfo(),
        user: {
          anonymous_coward_name: 'TestAnon',
        },
      };
      const account = {};
      assert.deepEqual(element.computeName(account, false, config), 'TestAnon');
    });
  });

  suite('attention set', () => {
    setup(async () => {
      element.highlightAttention = true;
      element._config = {
        ...createServerInfo(),
        user: {anonymous_coward_name: 'Anonymous Coward'},
      };
      element._selfAccount = kermit;
      element.account = {
        ...createAccountDetailWithIdNameAndEmail(42),
        name: 'ernie',
      };
      element.change = {
        ...createChange(),
        attention_set: {
          42: {
            account: createAccountDetailWithIdNameAndEmail(42),
          },
        },
        owner: kermit,
        reviewers: {},
      };
      await flush();
    });

    test('show attention button', () => {
      const button = queryAndAssert(element, '#attentionButton');
      assert.ok(button);
      assert.isNull(button.getAttribute('disabled'));
    });

    test('tap attention button', async () => {
      const apiSpy = spyRestApi('removeFromAttentionSet');
      const button = queryAndAssert(element, '#attentionButton');
      assert.ok(button);
      assert.isNull(button.getAttribute('disabled'));
      MockInteractions.tap(button);
      assert.isTrue(apiSpy.calledOnce);
      assert.equal(apiSpy.lastCall.args[1], 42);
    });

    test('no status icons attribute', async () => {
      queryAndAssert(
        element,
        'gr-endpoint-decorator[name="account-status-icon"]'
      );

      element.noStatusIcons = true;
      await element.updateComplete;

      assert.notExists(
        query(element, 'gr-endpoint-decorator[name="account-status-icon"]')
      );
    });
  });
});

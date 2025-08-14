/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-auth-token';
import {GrAuthToken} from './gr-auth-token';
import {stubRestApi, waitEventLoop} from '../../../test/test-utils';
import {
  createAccountDetailWithId,
  createServerInfo,
} from '../../../test/test-data-generators';
import {AccountDetailInfo, ServerInfo} from '../../../types/common';
import {queryAndAssert} from '../../../test/test-utils';
import {GrButton} from '../../shared/gr-button/gr-button';
import {assert, fixture, html, waitUntil} from '@open-wc/testing';
import {AuthTokenInfo} from '../../../types/common';

suite('gr-auth-token tests', () => {
  let element: GrAuthToken;
  let account: AccountDetailInfo;
  let config: ServerInfo;

  setup(async () => {
    account = {...createAccountDetailWithId(), username: 'user name'};
    config = createServerInfo();

    stubRestApi('getAccount').returns(Promise.resolve(account));
    stubRestApi('getConfig').returns(Promise.resolve(config));

    element = await fixture(html`<gr-auth-token></gr-auth-token>`);
    await waitUntil(
      () => element.getUserModel().getState().account === account
    );
    await waitUntil(
      () => element.getConfigModel().getState().serverConfig === config
    );
    await waitEventLoop();
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      `
        <div class="gr-form-styles">
          <div>
            <section>
              <span class="title"> Username </span>
              <span class="value"> user name </span>
            </section>
            <fieldset id="existing">
              <table>
                <thead>
                  <tr>
                    <th class="idColumn">ID</th>
                    <th class="expirationColumn">Expiration Date</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody></tbody>
                <tfoot>
                  <tr>
                    <th style="vertical-align: top;">
                    <md-outlined-text-field
                      autocomplete=""
                      class="showBlueFocusBorder"
                      id="newToken"
                      inputmode=""
                      placeholder="New Token ID"
                      type="text"
                    >
                    </md-outlined-text-field>
                    </th>
                    <th style="vertical-align: top;">
                    <md-outlined-text-field
                      autocomplete=""
                      class="lifeTimeInput showBlueFocusBorder"
                      id="lifetime"
                      inputmode=""
                      placeholder="Lifetime (e.g. 30d)"
                      supporting-text="Max. allowed lifetime: unlimited. Leave empty for unlimited lifetime."
                      type="text"
                    >
                    </md-outlined-text-field>
                    </th>
                    <th>
                      <gr-button
                        aria-disabled="true"
                        id="generateButton"
                        link=""
                        disabled=""
                        role="button"
                        tabindex="-1"
                        >Generate</gr-button
                      >
                    </th>
                  </tr>
                </tfoot>
              </table>
            </fieldset>
          </div>
          <span hidden="">
            <a href="" target="_blank" rel="noopener noreferrer">
              Obtain password
            </a>
            (opens in a new tab)
          </span>
        </div>
        <dialog tabindex="-1" id="generatedAuthTokenModal">
          <div class="gr-form-styles">
            <section id="generatedAuthTokenDisplay">
              <span class="title"> New Token: </span>
              <span class="value"> </span>
              <gr-copy-clipboard
                buttontitle="Copy token to clipboard"
                hastooltip=""
                hideinput=""
              >
              </gr-copy-clipboard>
            </section>
            <section hidden="" id="authTokenWarning">
              This token will be valid until
              <gr-date-formatter showdateandtime="" withtooltip="">
              </gr-date-formatter>
              .
            </section>
            <section id="authTokenWarning">
              This token will not be displayed again.
              <br />
              If you lose it, you will need to generate a new one.
            </section>
            <gr-button
              aria-disabled="false"
              class="closeButton"
              link=""
              role="button"
              tabindex="0"
            >
              Close
            </gr-button>
          </div>
        </dialog>
        <dialog id="deleteAuthTokenModal" tabindex="-1">
          <gr-dialog
            class="confirmDialog"
            confirm-label="Delete"
            confirm-on-enter=""
            id="deleteDialog"
          >
            <div class="header" slot="header">Delete Authentication Token</div>
            <div class="main" slot="main">
              <section>
                Do you really want to delete the token? The deletion cannot be
              reverted.
              </section>
            </div>
          </gr-dialog>
        </dialog>
      `
    );
  });

  test('generate token', async () => {
    const button = queryAndAssert<GrButton>(element, '#generateButton');
    const nextToken = {id: 'next-token-id', token: 'next-token'};
    let generateResolve: (
      value: AuthTokenInfo | PromiseLike<AuthTokenInfo>
    ) => void;
    const generateStub = stubRestApi('generateAccountAuthToken').callsFake(
      () =>
        new Promise(resolve => {
          generateResolve = resolve;
        })
    );

    assert.isNotOk(element.generatedAuthToken);

    element.tokenInput.value = nextToken.id;
    element.tokenInput.dispatchEvent(new Event('input', {bubbles: true}));

    await element.updateComplete;

    assert.isFalse(button.disabled);
    button.click();

    assert.isTrue(generateStub.called);
    assert.equal(element.status, 'Generating...');

    generateStub.lastCall.returnValue.then(() => {
      generateResolve(nextToken);
      assert.equal(element.generatedAuthToken, nextToken);
    });
  });

  test('without http_password_url', () => {
    assert.isNull(element.passwordUrl);
  });

  test('with http_password_url', async () => {
    config.auth.http_password_url = 'http://example.com/';
    element.passwordUrl = config.auth.http_password_url;
    await element.updateComplete;
    assert.isNotNull(element.passwordUrl);
    assert.equal(element.passwordUrl, config.auth.http_password_url);
  });
});

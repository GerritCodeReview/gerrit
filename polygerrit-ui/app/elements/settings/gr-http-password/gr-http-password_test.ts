/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-http-password';
import {GrHttpPassword} from './gr-http-password';
import {stubRestApi, waitEventLoop} from '../../../test/test-utils';
import {
  createAccountDetailWithId,
  createServerInfo,
} from '../../../test/test-data-generators';
import {AccountDetailInfo, ServerInfo} from '../../../types/common';
import {queryAndAssert} from '../../../test/test-utils';
import {GrButton} from '../../shared/gr-button/gr-button';
import {fixture, html, assert} from '@open-wc/testing';

suite('gr-http-password tests', () => {
  let element: GrHttpPassword;
  let account: AccountDetailInfo;
  let config: ServerInfo;

  setup(async () => {
    account = {...createAccountDetailWithId(), username: 'user name'};
    config = createServerInfo();

    stubRestApi('getAccount').returns(Promise.resolve(account));
    stubRestApi('getConfig').returns(Promise.resolve(config));

    element = await fixture(html`<gr-http-password></gr-http-password>`);
    await element.loadData();
    await waitEventLoop();
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="gr-form-styles">
          <div>
            <section>
              <span class="title"> Username </span>
              <span class="value"> user name </span>
            </section>
            <gr-button
              aria-disabled="false"
              id="generateButton"
              role="button"
              tabindex="0"
            >
              Generate new password
            </gr-button>
          </div>
          <span hidden="">
            <a href="" target="_blank" rel="noopener noreferrer">
              Obtain password
            </a>
            (opens in a new tab)
          </span>
        </div>
        <dialog tabindex="-1" id="generatedPasswordModal">
          <div class="gr-form-styles">
            <section id="generatedPasswordDisplay">
              <span class="title"> New Password: </span>
              <span class="value"> </span>
              <gr-copy-clipboard
                buttontitle="Copy password to clipboard"
                hastooltip=""
                hideinput=""
              >
              </gr-copy-clipboard>
            </section>
            <section id="passwordWarning">
              This password will not be displayed again.
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
      `
    );
  });

  test('generate password', () => {
    const button = queryAndAssert<GrButton>(element, '#generateButton');
    const nextPassword = 'the new password';
    let generateResolve: (value: string | PromiseLike<string>) => void;
    const generateStub = stubRestApi('generateAccountHttpPassword').callsFake(
      () =>
        new Promise(resolve => {
          generateResolve = resolve;
        })
    );

    assert.isNotOk(element._generatedPassword);

    button.click();

    assert.isTrue(generateStub.called);
    assert.equal(element._generatedPassword, 'Generating...');

    generateStub.lastCall.returnValue.then(() => {
      generateResolve(nextPassword);
      assert.equal(element._generatedPassword, nextPassword);
    });
  });

  test('without http_password_url', () => {
    assert.isNull(element._passwordUrl);
  });

  test('with http_password_url', async () => {
    config.auth.http_password_url = 'http://example.com/';
    await element.loadData();
    assert.isNotNull(element._passwordUrl);
    assert.equal(element._passwordUrl, config.auth.http_password_url);
  });
});

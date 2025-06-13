/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-account-info';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrAccountInfo} from './gr-account-info';
import {
  createAccountDetailWithId,
  createAccountWithIdNameAndEmail,
  createAuth,
  createPreferences,
  createServerInfo,
} from '../../../test/test-data-generators';
import {stubRestApi} from '../../../test/test-utils';
import {EditableAccountField} from '../../../api/rest-api';

suite('gr-account-info screenshot tests', () => {
  let element: GrAccountInfo;

  setup(async () => {
    const account = {
      ...createAccountDetailWithId(),
      ...createAccountWithIdNameAndEmail(123),
    };
    const config = createServerInfo();
    config.auth = {
      ...createAuth(),
      editable_account_fields: [
        EditableAccountField.FULL_NAME,
        EditableAccountField.USER_NAME,
      ],
    };

    stubRestApi('getAccount').resolves(account);
    stubRestApi('getConfig').resolves(config);
    stubRestApi('getPreferences').resolves(createPreferences());
    stubRestApi('getAvatarChangeUrl').resolves('');

    element = await fixture<GrAccountInfo>(
      html`<gr-account-info></gr-account-info>`
    );
    await element.loadData();
    await element.updateComplete;
  });

  test('screenshot', async () => {
    await visualDiff(element, 'gr-account-info');
  });
});

/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-expect-error
import {visualDiff} from '@web/test-runner-visual-regression';
import './user-message';
import {UserMessage} from './user-message';
import {
  UserMessage as UserMessageState,
  UserType,
} from '../../models/chat/chat-model';
import {visualDiffDarkTheme} from '../../test/test-utils';

suite('user-message screenshot tests', () => {
  let element: UserMessage;

  const message: UserMessageState = {
    userType: UserType.USER,
    content:
      'src/com/android/settings/supervision/EnableSupervisionActivity.kt',
    contextItems: [],
  };

  setup(async () => {
    element = await fixture(
      html`<user-message
        style="width: 300px;"
        .message=${message}
      ></user-message>`
    );
    await element.updateComplete;
  });

  test('renders long unbroken file path with break-word wrapping', async () => {
    await visualDiff(element, 'user-message-long-path');
    await visualDiffDarkTheme(element, 'user-message-long-path');
  });
});

/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-notifications-prompt';
import {GrNotificationsPrompt} from './gr-notifications-prompt';
import {fixture, html, assert} from '@open-wc/testing';
import {getAppContext} from '../../../services/app-context';
import {testResolver} from '../../../test/common-test-setup';
import {
  ServiceWorkerInstaller,
  serviceWorkerInstallerToken,
} from '../../../services/service-worker-installer';
import {waitUntilObserved} from '../../../test/test-utils';
import {createDefaultPreferences} from '../../../constants/constants';
import {userModelToken} from '../../../models/user/user-model';

suite('gr-notifications-prompt tests', () => {
  let element: GrNotificationsPrompt;
  let serviceWorkerInstaller: ServiceWorkerInstaller;

  setup(async () => {
    sinon
      .stub(window.navigator.serviceWorker, 'register')
      .returns(Promise.resolve({} as ServiceWorkerRegistration));
    const flagsService = getAppContext().flagsService;
    sinon.stub(flagsService, 'isEnabled').returns(true);
    const userModel = testResolver(userModelToken);
    const prefs = {
      ...createDefaultPreferences(),
      allow_browser_notifications: true,
    };
    userModel.setPreferences(prefs);
    await waitUntilObserved(
      userModel.preferences$,
      pref => pref.allow_browser_notifications === true
    );
    await waitUntilObserved(
      userModel.preferences$,
      pref => pref.allow_browser_notifications === true
    );
    serviceWorkerInstaller = testResolver(serviceWorkerInstallerToken);
    // Since we cannot stub Notification.permission, we stub shouldShowPrompt.
    sinon.stub(serviceWorkerInstaller, 'shouldShowPrompt').returns(true);
    element = await fixture(
      html`<gr-notifications-prompt></gr-notifications-prompt>`
    );
    await waitUntilObserved(
      serviceWorkerInstaller.shouldShowPrompt$,
      shouldShowPrompt => shouldShowPrompt === true
    );
    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element, // cannot format with HTML because test will not pass.
      `<div id="notificationsPrompt" role="dialog">
        <div class="icon"><gr-icon icon="info"> </gr-icon></div>
        <div class="content">
          <h3 class="heading-3">Missing your turn notifications?</h3>
          <div class="message">
            Get notified whenever it's your turn on a change. Gerrit needs
          permission to send notifications. To turn on notifications, click
            <b> Continue </b> and then <b> Allow </b>
            when prompted by your browser.
          </div>
          <div class="buttons">
            <gr-button
              aria-disabled="false"
              primary=""
              role="button"
              tabindex="0"
            >
              Continue
            </gr-button>
            <gr-button aria-disabled="false" role="button" tabindex="0">
              Disable in settings
            </gr-button>
          </div>
        </div>
        <div class="icon">
          <gr-button aria-disabled="false" link="" role="button" tabindex="0">
            <gr-icon icon="close"> </gr-icon>
          </gr-button>
        </div>
      </div>`
    );
  });
});

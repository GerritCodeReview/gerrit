/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, html, css, nothing} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {resolve} from '../../../models/dependency';
import {serviceWorkerInstallerToken} from '../../../services/service-worker-installer';
import {subscribe} from '../../lit/subscription-controller';
import {modalStyles} from '../../../styles/gr-modal-styles';
import {fontStyles} from '../../../styles/gr-font-styles';
import {navigationToken} from '../gr-navigation/gr-navigation';
import {createSettingsUrl} from '../../../models/views/settings';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-icon/gr-icon';

declare global {
  interface HTMLElementTagNameMap {
    'gr-notifications-prompt': GrNotificationsPrompt;
  }
}

@customElement('gr-notifications-prompt')
export class GrNotificationsPrompt extends LitElement {
  @state() private hideNotificationsPrompt = false;

  @state() private shouldShowPrompt = false;

  private readonly serviceWorkerInstaller = resolve(
    this,
    serviceWorkerInstallerToken
  );

  private readonly getNavigation = resolve(this, navigationToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.serviceWorkerInstaller().shouldShowPrompt$,
      shouldShowPrompt => {
        this.shouldShowPrompt = !!shouldShowPrompt;
      }
    );
  }

  static override get styles() {
    return [
      sharedStyles,
      modalStyles,
      fontStyles,
      css`
        #notificationsPrompt {
          position: absolute;
          right: 30px;
          top: 100px;
          z-index: 150; /* Less than gr-hovercard's, higher than rest */
          display: flex;
          background-color: var(--background-color-primary);
          padding: var(--spacing-l);
          border: 1px solid var(--border-color);
          border-radius: var(--border-radius);
          box-shadow: var(--elevation-level-5);
        }
        .icon {
          flex: 0 0 30px;
        }
        .content {
          width: 300px;
        }
        div.section {
          margin: 0 var(--spacing-xl) var(--spacing-m) var(--spacing-xl);
          display: flex;
          align-items: center;
        }
        div.sectionIcon {
          flex: 0 0 30px;
        }
        .message {
          margin: var(--spacing-m) 0;
        }
        div.sectionIcon gr-icon {
          position: relative;
        }
        b {
          font-weight: var(--font-weight-bold);
        }
      `,
    ];
  }

  override render() {
    if (this.hideNotificationsPrompt) return nothing;
    if (!this.shouldShowPrompt) return nothing;
    return html`<div id="notificationsPrompt" role="dialog">
      <div class="icon">
        <gr-icon icon="info"></gr-icon>
      </div>
      <div class="content">
        <h3 class="heading-3">Missing your turn notifications?</h3>
        <div class="message">
          Get notified whenever it’s your turn on a change. Gerrit needs
          permission to send notifications. To turn on notifications, click
          <b>Continue</b> and then <b>Allow</b> when prompted by your browser.
        </div>
        <div class="buttons">
          <gr-button
            primary=""
            @click=${() => {
              this.hideNotificationsPrompt = true;
              this.serviceWorkerInstaller().requestPermission();
            }}
            >Continue</gr-button
          >
          <gr-button
            @click=${() => {
              this.hideNotificationsPrompt = true;
              this.getNavigation().setUrl(createSettingsUrl());
            }}
            >Disable in settings</gr-button
          >
        </div>
      </div>
      <div class="icon">
        <gr-button
          @click=${() => {
            this.hideNotificationsPrompt = true;
          }}
          link
        >
          <gr-icon icon="close"></gr-icon>
        </gr-button>
      </div>
    </div>`;
  }
}

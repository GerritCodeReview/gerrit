/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-input/iron-input';
import '../../shared/gr-button/gr-button';
import {getBaseUrl} from '../../../utils/url-util';
import {
  ServerInfo,
  GroupInfo,
  ContributorAgreementInfo,
} from '../../../types/common';
import {fireAlert, fireTitleChange} from '../../../utils/event-util';
import {getAppContext} from '../../../services/app-context';
import {fontStyles} from '../../../styles/gr-font-styles';
import {formStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, html, css} from 'lit';
import {customElement, state} from 'lit/decorators';
import {BindValueChangeEvent} from '../../../types/events';
import {ifDefined} from 'lit/directives/if-defined';

declare global {
  interface HTMLElementTagNameMap {
    'gr-cla-view': GrClaView;
  }
}

@customElement('gr-cla-view')
export class GrClaView extends LitElement {
  // private but used in test
  @state() groups?: GroupInfo[];

  // private but used in test
  @state() serverConfig?: ServerInfo;

  @state() private agreementsText?: string;

  // private but used in test
  @state() agreementName?: string;

  // private but used in test
  @state() signedAgreements?: ContributorAgreementInfo[];

  @state() private showAgreements = false;

  @state() private agreementsUrl?: string;

  private readonly restApiService = getAppContext().restApiService;

  override connectedCallback() {
    super.connectedCallback();
    this.loadData();

    fireTitleChange(this, 'New Contributor Agreement');
  }

  static override get styles() {
    return [
      fontStyles,
      formStyles,
      sharedStyles,
      css`
        h1 {
          margin-bottom: var(--spacing-m);
        }
        h3 {
          margin-bottom: var(--spacing-m);
        }
        .agreementsUrl {
          border: 1px solid var(--border-color);
          margin-bottom: var(--spacing-xl);
          margin-left: var(--spacing-xl);
          margin-right: var(--spacing-xl);
          padding: var(--spacing-s);
        }
        #claNewAgreementsLabel {
          font-weight: var(--font-weight-bold);
        }
        #claNewAgreement {
          display: none;
        }
        #claNewAgreement.show {
          display: block;
        }
        .contributorAgreementButton {
          font-weight: var(--font-weight-bold);
        }
        .alreadySubmittedText {
          color: var(--error-text-color);
          margin: 0 var(--spacing-xxl);
          padding: var(--spacing-m);
        }
        main {
          margin: var(--spacing-xxl) auto;
          max-width: 50em;
        }
      `,
    ];
  }

  override render() {
    return html`
      <main>
        <h1 class="heading-1">New Contributor Agreement</h1>
        <h3 class="heading-3">Select an agreement type:</h3>
        ${(this.serverConfig?.auth.contributor_agreements ?? [])
          .filter(agreement => agreement.url)
          .map(item => this.renderAgreementsButton(item))}
        ${this.renderNewAgreement()}
      </main>
    `;
  }

  private renderAgreementsButton(item: ContributorAgreementInfo) {
    return html`
      <span class="contributorAgreementButton">
        <input
          id="claNewAgreementsInput${item.name}"
          name="claNewAgreementsRadio"
          type="radio"
          data-name=${ifDefined(item.name)}
          data-url=${ifDefined(item.url)}
          @click=${this.handleShowAgreement}
          ?disabled=${this.disableAgreements(item)}
        />
        <label id="claNewAgreementsLabel">${item.name}</label>
      </span>
      ${this.renderAlreadySubmittedText(item)}
      <div class="agreementsUrl">${item.description}</div>
    `;
  }

  private renderAlreadySubmittedText(item: ContributorAgreementInfo) {
    if (!this.disableAgreements(item)) return;

    return html`
      <div class="alreadySubmittedText">Agreement already submitted.</div>
    `;
  }

  private renderNewAgreement() {
    if (!this.showAgreements) return;
    return html`
      <div id="claNewAgreement">
        <h3 class="heading-3">Review the agreement:</h3>
        <div id="agreementsUrl" class="agreementsUrl">
          <a
            href=${ifDefined(this.agreementsUrl)}
            target="blank"
            rel="noopener"
          >
            Please review the agreement.</a
          >
        </div>
        ${this.renderAgreementsTextBox()} ${this.computeHideAgreementTextbox()}
      </div>
    `;
  }

  private renderAgreementsTextBox() {
    if (this.computeHideAgreementTextbox()) return;
    return html`
      <div class="agreementsTextBox">
        <h3 class="heading-3">Complete the agreement:</h3>
        <iron-input
          .bindValue=${this.agreementsText}
          @bind-value-changed=${this.handleBindValueChanged}
        >
          <input id="input-agreements" placeholder="Enter 'I agree' here" />
        </iron-input>
        <gr-button
          @click=${this.handleSaveAgreements}
          ?disabled=${this.agreementsText?.toLowerCase() !== 'i agree'}
        >
          Submit
        </gr-button>
      </div>
    `;
  }

  loadData() {
    const promises = [];
    promises.push(
      this.restApiService.getConfig(true).then(config => {
        this.serverConfig = config;
      })
    );

    promises.push(
      this.restApiService.getAccountGroups().then(groups => {
        if (!groups) return;
        this.groups = groups.sort((a, b) =>
          (a.name || '').localeCompare(b.name || '')
        );
      })
    );

    promises.push(
      this.restApiService
        .getAccountAgreements()
        .then((agreements: ContributorAgreementInfo[] | undefined) => {
          this.signedAgreements = agreements || [];
        })
    );

    return Promise.all(promises);
  }

  // private but used in test
  getAgreementsUrl(configUrl: string) {
    if (!configUrl) return '';
    let url;
    if (configUrl.startsWith('http:') || configUrl.startsWith('https:')) {
      url = configUrl;
    } else {
      url = getBaseUrl() + '/' + configUrl;
    }

    return url;
  }

  private readonly handleShowAgreement = (e: Event) => {
    this.agreementName = (e.target as HTMLInputElement).getAttribute(
      'data-name'
    )!;
    const url = (e.target as HTMLInputElement).getAttribute('data-url')!;
    this.agreementsUrl = this.getAgreementsUrl(url);
    this.showAgreements = true;
  };

  private readonly handleSaveAgreements = () => {
    this.createToast('Agreement saving...');

    const name = this.agreementName;
    return this.restApiService.saveAccountAgreement({name}).then(res => {
      let message = 'Agreement failed to be submitted, please try again';
      if (res.status === 200) {
        message = 'Agreement has been successfully submitted.';
      }
      this.createToast(message);
      this.loadData();
      this.agreementsText = '';
      this.showAgreements = false;
    });
  };

  private createToast(message: string) {
    fireAlert(this, message);
  }

  // private but used in test
  disableAgreements(item: ContributorAgreementInfo) {
    for (const group of this.groups ?? []) {
      if (
        item?.auto_verify_group?.id === group.id ||
        this.signedAgreements?.find(i => i.name === item.name)
      ) {
        return true;
      }
    }
    return false;
  }

  // This checks for auto_verify_group,
  // if specified it returns 'true' which
  // then hides the text box and submit button.
  // private but used in test
  computeHideAgreementTextbox() {
    const contributorAgreements =
      this.serverConfig?.auth.contributor_agreements;
    if (!this.agreementName || !contributorAgreements) return false;
    return contributorAgreements.some(
      (contributorAgreement: ContributorAgreementInfo) =>
        this.agreementName === contributorAgreement.name &&
        !contributorAgreement.auto_verify_group
    );
  }

  private readonly handleBindValueChanged = (e: BindValueChangeEvent) => {
    this.agreementsText = e.detail.value;
  };
}

import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      h1 {
        margin-bottom: var(--spacing-m);
      }
      h3 {
        margin-bottom: var(--spacing-m);
      }
      .agreementsUrl {
        border: 1px solid #b0bdcc;
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
      .alreadySubmittedText.hide,
      .hideAgreementsTextBox {
        display: none;
      }
      main {
        margin: var(--spacing-xxl) auto;
        max-width: 50em;
      }
    </style>
    <style include="gr-form-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <main>
      <h1>New Contributor Agreement</h1>
      <h3>Select an agreement type:</h3>
      <template is="dom-repeat" items="[[_serverConfig.auth.contributor_agreements]]">
        <span class="contributorAgreementButton">
          <input id\$="claNewAgreementsInput[[item.name]]" name="claNewAgreementsRadio" type="radio" data-name\$="[[item.name]]" data-url\$="[[item.url]]" on-click="_handleShowAgreement" disabled\$="[[_disableAgreements(item, _groups, _signedAgreements)]]">
          <label id="claNewAgreementsLabel">[[item.name]]</label>
        </span>
        <div class\$="alreadySubmittedText [[_hideAgreements(item, _groups, _signedAgreements)]]">
          Agreement already submitted.
        </div>
        <div class="agreementsUrl">
          [[item.description]]
        </div>
      </template>
      <div id="claNewAgreement" class\$="[[_computeShowAgreementsClass(_showAgreements)]]">
        <h3 class="smallHeading">Review the agreement:</h3>
        <div id="agreementsUrl" class="agreementsUrl">
          <a href\$="[[_agreementsUrl]]" target="blank" rel="noopener">
            Please review the agreement.</a>
        </div>
        <div class\$="agreementsTextBox [[_computeHideAgreementClass(_agreementName, _serverConfig.auth.contributor_agreements)]]">
          <h3 class="smallHeading">Complete the agreement:</h3>
          <iron-input bind-value="{{_agreementsText}}" placeholder="Enter 'I agree' here">
            <input id="input-agreements" is="iron-input" bind-value="{{_agreementsText}}" placeholder="Enter 'I agree' here">
          </iron-input>
          <gr-button on-click="_handleSaveAgreements" disabled="[[_disableAgreementsText(_agreementsText)]]">
            Submit
          </gr-button>
        </div>
      </div>
    </main>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;

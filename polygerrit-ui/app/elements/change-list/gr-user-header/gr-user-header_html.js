import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="dashboard-header-styles">
      .name {
        display: inline-block;
      }
      .name hr {
        width: 100%;
      }
      .status.hide,
      .name.hide,
      .dashboardLink.hide {
        display: none;
      }
    </style>
    <gr-avatar account="[[_accountDetails]]" image-size="100" aria-label="Account avatar"></gr-avatar>
    <div class="info">
      <h1 class="name">
        [[_computeDetail(_accountDetails, 'name')]]
      </h1>
      <hr>
      <div class\$="status [[_computeStatusClass(_accountDetails)]]">
        <span>Status:</span> [[_status]]
      </div>
      <div>
        <span>Email:</span>
        <a href="mailto:[[_computeDetail(_accountDetails, 'email')]]"><!--
          -->[[_computeDetail(_accountDetails, 'email')]]</a>
      </div>
      <div>
        <span>Joined:</span>
        <gr-date-formatter date-str="[[_computeDetail(_accountDetails, 'registered_on')]]">
        </gr-date-formatter>
      </div>
      <gr-endpoint-decorator name="user-header">
        <gr-endpoint-param name="accountDetails" value="[[_accountDetails]]">
        </gr-endpoint-param>
        <gr-endpoint-param name="loggedIn" value="[[loggedIn]]">
        </gr-endpoint-param>
      </gr-endpoint-decorator>
    </div>
    <div class="info">
      <div class\$="[[_computeDashboardLinkClass(showDashboardLink, loggedIn)]]">
        <a href\$="[[_computeDashboardUrl(_accountDetails)]]">View dashboard</a>
      </div>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;

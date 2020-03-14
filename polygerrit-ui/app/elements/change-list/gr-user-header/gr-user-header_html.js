<!--
@license
Copyright (C) 2017 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

<link rel="import" href="/bower_components/polymer/polymer.html">
<link rel="import" href="../../../styles/shared-styles.html">
<link rel="import" href="../../core/gr-navigation/gr-navigation.html">
<link rel="import" href="../../plugins/gr-endpoint-decorator/gr-endpoint-decorator.html">
<link rel="import" href="../../plugins/gr-endpoint-param/gr-endpoint-param.html">
<link rel="import" href="../../shared/gr-avatar/gr-avatar.html">
<link rel="import" href="../../shared/gr-date-formatter/gr-date-formatter.html">
<link rel="import" href="../../shared/gr-rest-api-interface/gr-rest-api-interface.html">
<link rel="import" href="../../../styles/dashboard-header-styles.html">

<dom-module id="gr-user-header">
  <template>
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
    <gr-avatar
        account="[[_accountDetails]]"
        image-size="100"
        aria-label="Account avatar"></gr-avatar>
    <div class="info">
      <h1 class="name">
        [[_computeDetail(_accountDetails, 'name')]]
      </h1>
      <hr/>
      <div class$="status [[_computeStatusClass(_accountDetails)]]">
        <span>Status:</span> [[_status]]
      </div>
      <div>
        <span>Email:</span>
        <a href="mailto:[[_computeDetail(_accountDetails, 'email')]]"><!--
          -->[[_computeDetail(_accountDetails, 'email')]]</a>
      </div>
      <div>
        <span>Joined:</span>
        <gr-date-formatter
            date-str="[[_computeDetail(_accountDetails, 'registered_on')]]">
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
      <div class$="[[_computeDashboardLinkClass(showDashboardLink, loggedIn)]]">
        <a href$="[[_computeDashboardUrl(_accountDetails)]]">View dashboard</a>
      </div>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
  </template>
  <script src="gr-user-header.js"></script>
</dom-module>

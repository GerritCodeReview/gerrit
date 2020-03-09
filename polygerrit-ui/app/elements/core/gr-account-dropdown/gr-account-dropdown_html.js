<!--
@license
Copyright (C) 2015 The Android Open Source Project

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

<link rel="import" href="../../../behaviors/gr-display-name-behavior/gr-display-name-behavior.html">
<link rel="import" href="/bower_components/polymer/polymer.html">
<link rel="import" href="../../shared/gr-button/gr-button.html">
<link rel="import" href="../../shared/gr-dropdown/gr-dropdown.html">
<link rel="import" href="../../shared/gr-rest-api-interface/gr-rest-api-interface.html">
<link rel="import" href="../../../styles/shared-styles.html">
<link rel="import" href="../../shared/gr-avatar/gr-avatar.html">

<dom-module id="gr-account-dropdown">
  <template>
    <style include="shared-styles">
      gr-dropdown {
        padding: 0 var(--spacing-m);
        --gr-button: {
          color: var(--header-text-color);
        }
        --gr-dropdown-item: {
          color: var(--primary-text-color);
        }
      }
      gr-avatar {
        height: 2em;
        width: 2em;
        vertical-align: middle;
      }
    </style>
    <gr-dropdown
        link
        items=[[links]]
        top-content=[[topContent]]
        horizontal-align="right">
        <span hidden$="[[_hasAvatars]]" hidden>[[_accountName(account)]]</span>
        <gr-avatar account="[[account]]" hidden$="[[!_hasAvatars]]" hidden
            image-size="56" aria-label="Account avatar"></gr-avatar>
    </gr-dropdown>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
  </template>
  <script src="gr-account-dropdown.js"></script>
</dom-module>

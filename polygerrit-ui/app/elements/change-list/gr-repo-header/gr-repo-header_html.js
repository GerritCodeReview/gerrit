<!--
@license
Copyright (C) 2018 The Android Open Source Project

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
<link rel="import" href="../../../styles/dashboard-header-styles.html">
<link rel="import" href="../../../styles/shared-styles.html">
<link rel="import" href="../../core/gr-navigation/gr-navigation.html">
<link rel="import" href="../../shared/gr-date-formatter/gr-date-formatter.html">
<link rel="import" href="../../shared/gr-rest-api-interface/gr-rest-api-interface.html">

<dom-module id="gr-repo-header">
  <template>
    <style include="shared-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="dashboard-header-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <div class="info">
      <h1 class$="name">
        [[repo]]
        <hr/>
      </h1>
      <div>
        <span>Detail:</span> <a href$="[[_repoUrl]]">Repo settings</a>
      </div>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
  </template>
  <script src="gr-repo-header.js"></script>
</dom-module>

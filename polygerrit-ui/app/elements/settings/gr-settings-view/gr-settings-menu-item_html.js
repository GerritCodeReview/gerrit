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
<link rel="import" href="../../../styles/gr-page-nav-styles.html">

<dom-module id="gr-settings-menu-item">
  <template>
    <style include="shared-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="gr-page-nav-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <div class="navStyles">
      <li><a href$="[[href]]">[[title]]</a></li>
    </div>
  </template>
  <script src="gr-settings-menu-item.js"></script>
</dom-module>

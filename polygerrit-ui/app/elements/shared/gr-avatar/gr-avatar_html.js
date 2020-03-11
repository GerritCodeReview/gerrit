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

<link rel="import" href="/bower_components/polymer/polymer.html">
<link rel="import" href="../../../behaviors/base-url-behavior/base-url-behavior.html">
<link rel="import" href="../../../styles/shared-styles.html">
<link rel="import" href="../../shared/gr-js-api-interface/gr-js-api-interface.html">
<link rel="import" href="../gr-rest-api-interface/gr-rest-api-interface.html">

<dom-module id="gr-avatar">
  <template>
    <style include="shared-styles">
      :host {
        display: inline-block;
        border-radius: 50%;
        background-size: cover;
        background-color: var(--avatar-background-color, #f1f2f3);
      }
    </style>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
  </template>
  <script src="gr-avatar.js"></script>
</dom-module>

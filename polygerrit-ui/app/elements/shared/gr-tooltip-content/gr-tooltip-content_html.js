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
<link rel="import" href="../gr-icons/gr-icons.html">
<link rel="import" href="../../../behaviors/gr-tooltip-behavior/gr-tooltip-behavior.html">

<dom-module id="gr-tooltip-content">
  <template>
    <style>
      iron-icon {
        width: var(--line-height-normal);
        height: var(--line-height-normal);
        vertical-align: top;
      }
    </style>
    <slot></slot><!--
 --><iron-icon icon="gr-icons:info" hidden$="[[!showIcon]]"></iron-icon>
  </template>
  <script src="gr-tooltip-content.js"></script>
</dom-module>

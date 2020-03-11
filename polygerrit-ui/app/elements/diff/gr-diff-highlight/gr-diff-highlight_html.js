<!--
@license
Copyright (C) 2016 The Android Open Source Project

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

<link rel="import" href="../../../behaviors/fire-behavior/fire-behavior.html">
<link rel="import" href="../../../styles/shared-styles.html">
<link rel="import" href="../gr-selection-action-box/gr-selection-action-box.html">
<script src="gr-annotation.js"></script>
<script src="gr-range-normalizer.js"></script>

<dom-module id="gr-diff-highlight">
  <template>
    <style include="shared-styles">
      :host {
        position: relative;
      }
      gr-selection-action-box {
        /**
         * Needs z-index to apear above wrapped content, since it's inseted
         * into DOM before it.
         */
        z-index: 10;
      }
    </style>
    <div class="contentWrapper">
      <slot></slot>
    </div>
  </template>
  <script src="gr-diff-highlight.js"></script>
</dom-module>

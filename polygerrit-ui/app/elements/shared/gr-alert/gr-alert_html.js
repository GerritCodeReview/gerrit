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
<link rel="import" href="../gr-button/gr-button.html">
<link rel="import" href="../../../styles/shared-styles.html">

<script src="../../../scripts/rootElement.js"></script>

<dom-module id="gr-alert">
  <template>
    <style include="shared-styles">
      /**
       * ALERT: DO NOT ADD TRANSITION PROPERTIES WITHOUT PROPERLY UNDERSTANDING
       * HOW THEY ARE USED IN THE CODE.
       */
      :host([toast]) {
        background-color: var(--tooltip-background-color);
        bottom: 1.25rem;
        border-radius: var(--border-radius);
        box-shadow: var(--elevation-level-2);
        color: var(--view-background-color);
        left: 1.25rem;
        position: fixed;
        transform: translateY(5rem);
        transition: transform var(--gr-alert-transition-duration, 80ms) ease-out;
        z-index: 1000;
      }
      :host([shown]) {
        transform: translateY(0);
      }
      /**
       * NOTE: To avoid style being overwritten by outside of the shadow DOM
       * (as outside styles always win), .content-wrapper is introduced as a
       * wrapper around main content to have better encapsulation, styles that
       * may be affected by outside should be defined on it.
       * In this case, `padding:0px` is defined in main.css for all elements
       * with the universal selector: *.
       */
      .content-wrapper {
        padding: var(--spacing-l) var(--spacing-xl);
      }
      .text {
        color: var(--tooltip-text-color);
        display: inline-block;
        max-height: 10rem;
        max-width: 80vw;
        vertical-align: bottom;
        word-break: break-all;
      }
      .action {
        color: var(--link-color);
        font-weight: var(--font-weight-bold);
        margin-left: var(--spacing-l);
        text-decoration: none;
        --gr-button: {
          padding: 0;
        }
      }
    </style>
    <div class="content-wrapper">
      <span class="text">[[text]]</span>
      <gr-button
          link
          class="action"
          hidden$="[[_hideActionButton]]"
          on-click="_handleActionTap">[[actionText]]</gr-button>
    </div>
  </template>
  <script src="gr-alert.js"></script>
</dom-module>


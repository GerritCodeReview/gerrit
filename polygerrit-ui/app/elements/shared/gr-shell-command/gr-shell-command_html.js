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
<link rel="import" href="../../../styles/shared-styles.html">
<link rel="import" href="../../shared/gr-copy-clipboard/gr-copy-clipboard.html">

<dom-module id="gr-shell-command">
  <template>
    <style include="shared-styles">
      .commandContainer {
        margin-bottom: var(--spacing-m);
      }
      .commandContainer {
        background-color: var(--shell-command-background-color);
        /* Should be spacing-m larger than the :before width. */
        padding: var(--spacing-m) var(--spacing-m) var(--spacing-m) calc(3*var(--spacing-m) + 0.5em);
        position: relative;
        width: 100%;
      }
      .commandContainer:before {
        content: '$';
        position: absolute;
        display: block;
        box-sizing: border-box;
        background: var(--shell-command-decoration-background-color);
        top: 0;
        bottom: 0;
        left: 0;
        /* Should be spacing-m smaller than the .commandContainer padding-left. */
        width: calc(2*var(--spacing-m) + 0.5em);
        /* Should vertically match the padding of .commandContainer. */
        padding: var(--spacing-m);
        /* Should roughly match the height of .commandContainer without padding. */
        line-height: 26px;
      }
      .commandContainer gr-copy-clipboard {
        --text-container-style: {
          border: none;
        }
      }
    </style>
    <label>[[label]]</label>
    <div class="commandContainer">
      <gr-copy-clipboard text="[[command]]"></gr-copy-clipboard>
    </div>
  </template>
  <script src="gr-shell-command.js"></script>
</dom-module>

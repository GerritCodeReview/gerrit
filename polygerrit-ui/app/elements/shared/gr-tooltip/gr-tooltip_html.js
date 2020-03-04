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
<link rel="import" href="../../../styles/shared-styles.html">

<dom-module id="gr-tooltip">
  <template>
    <style include="shared-styles">
      :host {
        --gr-tooltip-arrow-size: .5em;
        --gr-tooltip-arrow-center-offset: 0;

        background-color: var(--tooltip-background-color);
        box-shadow: var(--elevation-level-2);
        color: var(--tooltip-text-color);
        font-size: var(--font-size-small);
        position: absolute;
        z-index: 1000;
        max-width: var(--tooltip-max-width);
      }
      :host .tooltip {
        padding: var(--spacing-m) var(--spacing-l);
      }
      :host .arrowPositionBelow,
      :host([position-below]) .arrowPositionAbove  {
        display: none;
      }
      :host([position-below]) .arrowPositionBelow {
        display: initial;
      }
      .arrow {
        border-left: var(--gr-tooltip-arrow-size) solid transparent;
        border-right: var(--gr-tooltip-arrow-size) solid transparent;
        height: 0;
        position: absolute;
        left: calc(50% - var(--gr-tooltip-arrow-size));
        margin-left: var(--gr-tooltip-arrow-center-offset);
        width: 0;
      }
      .arrowPositionAbove {
        border-top: var(--gr-tooltip-arrow-size) solid var(--tooltip-background-color);
        bottom: calc(-1 * var(--gr-tooltip-arrow-size));
      }
      .arrowPositionBelow {
        border-bottom: var(--gr-tooltip-arrow-size) solid var(--tooltip-background-color);
        top: calc(-1 * var(--gr-tooltip-arrow-size));
      }
    </style>
    <div class="tooltip">
      <i class="arrowPositionBelow arrow"></i>
      [[text]]
      <i class="arrowPositionAbove arrow"></i>
    </div>
  </template>
  <script src="gr-tooltip.js"></script>
</dom-module>

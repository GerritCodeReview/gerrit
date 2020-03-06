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
<link rel="import" href="../../../behaviors/gr-tooltip-behavior/gr-tooltip-behavior.html">
<link rel="import" href="/bower_components/polymer/polymer.html">
<link rel="import" href="../../../styles/shared-styles.html">

<dom-module id="gr-page-nav">
  <template>
    <style include="shared-styles">
      #nav {
        background-color: var(--table-header-background-color);
        border: 1px solid var(--border-color);
        border-top: none;
        height: 100%;
        position: absolute;
        top: 0;
        width: 14em;
      }
      #nav.pinned {
        position: fixed;
      }
      @media only screen and (max-width: 53em) {
        #nav {
          display: none;
        }
      }
    </style>
    <nav id="nav">
      <slot></slot>
    </nav>
  </template>
  <script src="gr-page-nav.js"></script>
</dom-module>

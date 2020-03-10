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
<link rel="import" href="/bower_components/iron-input/iron-input.html">
<link rel="import" href="/bower_components/iron-icon/iron-icon.html">

<link rel="import" href="../../../behaviors/base-url-behavior/base-url-behavior.html">
<link rel="import" href="../../../behaviors/fire-behavior/fire-behavior.html">
<link rel="import" href="../../../behaviors/gr-url-encoding-behavior/gr-url-encoding-behavior.html">
<link rel="import" href="../../../styles/shared-styles.html">
<link rel="import" href="../../shared/gr-button/gr-button.html">

<dom-module id="gr-list-view">
  <template>
    <style include="shared-styles">
      #filter {
        max-width: 25em;
      }
      #filter:focus {
        outline: none;
      }
      #topContainer {
        align-items: center;
        display: flex;
        height: 3rem;
        justify-content: space-between;
        margin: 0 var(--spacing-l);
      }
      #createNewContainer:not(.show) {
        display: none;
      }
      a {
        color: var(--primary-text-color);
        text-decoration: none;
      }
      a:hover {
        text-decoration: underline;
      }
      nav {
        align-items: center;
        display: flex;
        height: 3rem;
        justify-content: flex-end;
        margin-right: 20px;
      }
      nav,
      iron-icon {
        color: var(--deemphasized-text-color);
      }
      iron-icon {
        height: 1.85rem;
        margin-left: 16px;
        width: 1.85rem;
      }
    </style>
    <div id="topContainer">
      <div class="filterContainer">
        <label>Filter:</label>
        <iron-input
            type="text"
            bind-value="{{filter}}">
          <input
              is="iron-input"
              type="text"
              id="filter"
              bind-value="{{filter}}">
        </iron-input>
      </div>
      <div id="createNewContainer"
          class$="[[_computeCreateClass(createNew)]]">
        <gr-button primary link id="createNew" on-click="_createNewItem">
          Create New
        </gr-button>
      </div>
    </div>
    <slot></slot>
    <nav>
      Page [[_computePage(offset, itemsPerPage)]]
      <a id="prevArrow"
          href$="[[_computeNavLink(offset, -1, itemsPerPage, filter, path)]]"
          hidden$="[[_hidePrevArrow(loading, offset)]]" hidden>
        <iron-icon icon="gr-icons:chevron-left"></iron-icon>
      </a>
      <a id="nextArrow"
          href$="[[_computeNavLink(offset, 1, itemsPerPage, filter, path)]]"
          hidden$="[[_hideNextArrow(loading, items)]]" hidden>
        <iron-icon icon="gr-icons:chevron-right"></iron-icon>
      </a>
    </nav>
  </template>
  <script src="gr-list-view.js"></script>
</dom-module>

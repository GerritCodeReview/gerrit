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

<dom-module id="dashboard-header-styles">
  <template>
    <style>
      :host {
        background-color: var(--view-background-color);
        display: block;
        min-height: 9em;
        width: 100%;
      }
      gr-avatar {
        display: inline-block;
        height: 7em;
        left: 1em;
        margin: 1em;
        top: 1em;
        width: 7em;
      }
      .info {
        display: inline-block;
        padding: var(--spacing-l);
        vertical-align: top;
      }
      .info > div > span {
        display: inline-block;
        font-weight: var(--font-weight-bold);
        text-align: right;
        width: 4em;
      }
    </style>
  </template>
</dom-module>

/**
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
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../styles/gr-page-nav-styles.js';
const $_documentContainer = document.createElement('template');

$_documentContainer.innerHTML = `<dom-module id="gr-settings-menu-item">
  <style include="shared-styles"></style>
  <style include="gr-page-nav-styles"></style>
  <template>
    <div class="navStyles">
      <li><a href\$="[[href]]">[[title]]</a></li>
    </div>
  </template>
  
</dom-module>`;

document.head.appendChild($_documentContainer.content);

Polymer({
  is: 'gr-settings-menu-item',
  properties: {
    href: String,
    title: String,
  },
});

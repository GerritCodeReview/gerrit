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

<link rel="import" href="../../../behaviors/gr-display-name-behavior/gr-display-name-behavior.html">
<link rel="import" href="../../core/gr-navigation/gr-navigation.html">
<link rel="import" href="../../shared/gr-rest-api-interface/gr-rest-api-interface.html">
<link rel="import" href="../gr-search-bar/gr-search-bar.html">

<dom-module id="gr-smart-search">
  <template>
    <style include="shared-styles">

    </style>
    <gr-search-bar id="search"
        value="{{searchQuery}}"
        on-handle-search="_handleSearch"
        project-suggestions="[[_projectSuggestions]]"
        group-suggestions="[[_groupSuggestions]]"
        account-suggestions="[[_accountSuggestions]]"></gr-search-bar>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
  </template>
  <script src="gr-smart-search.js"></script>
</dom-module>

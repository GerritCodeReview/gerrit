/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import '../styles/shared-styles';
import '../styles/themes/app-theme';
import {
  applyTheme as applyDarkTheme,
  removeTheme as removeDarkTheme,
} from '../styles/themes/dark-theme';
import './admin/gr-admin-view/gr-admin-view';
import '../elements/documentation/gr-documentation-search/gr-documentation-search';
import './change-list/gr-change-list-view/gr-change-list-view';
import './change-list/gr-dashboard-view/gr-dashboard-view';
import './change/gr-change-view/gr-change-view';
import '../elements/core/gr-error-manager/gr-error-manager';
import '../elements/core/gr-keyboard-shortcuts-dialog/gr-keyboard-shortcuts-dialog';
import './core/gr-main-header/gr-main-header';
import '../elements/core/gr-router/gr-router';
import './core/gr-smart-search/gr-smart-search';
import './diff/gr-diff-view/gr-diff-view';
import '../elements/edit/gr-editor-view/gr-editor-view';
import '../elements/plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../elements/plugins/gr-endpoint-param/gr-endpoint-param';
import '../elements/plugins/gr-endpoint-slot/gr-endpoint-slot';
import '../elements/plugins/gr-external-style/gr-external-style';
import '../elements/plugins/gr-plugin-host/gr-plugin-host';
import '../elements/settings/gr-cla-view/gr-cla-view';
import '../elements/settings/gr-registration-dialog/gr-registration-dialog';
import './settings/gr-settings-view/gr-settings-view';
import '../elements/shared/gr-lib-loader/gr-lib-loader';
import '../elements/shared/gr-rest-api-interface/gr-rest-api-interface';

let darkTheme = false;
(window as any).toggleDarkTheme = () => {
  if (!darkTheme) {
    applyDarkTheme();
  } else {
    removeDarkTheme();
  }
  darkTheme = !darkTheme;
};

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

/**
 * A simplified version of gr-app-element.js which includes all entry points needed
 * to include all gr-xx components except the router and navigation.
 *
 * This is useful when gr-xx components are needed without router related stuff.
 */
import '../styles/shared-styles.js';
import '../styles/themes/app-theme.js';
import './admin/gr-admin-view/gr-admin-view.js';
import './documentation/gr-documentation-search/gr-documentation-search.js';
import './change-list/gr-change-list-view/gr-change-list-view.js';
import './change-list/gr-dashboard-view/gr-dashboard-view.js';
import './change/gr-change-view/gr-change-view.js';
import './core/gr-error-manager/gr-error-manager.js';
import './core/gr-keyboard-shortcuts-dialog/gr-keyboard-shortcuts-dialog.js';
import './core/gr-main-header/gr-main-header.js';
import './core/gr-smart-search/gr-smart-search.js';
import './diff/gr-diff-view/gr-diff-view.js';
import './edit/gr-editor-view/gr-editor-view.js';
import './plugins/gr-endpoint-decorator/gr-endpoint-decorator.js';
import './plugins/gr-endpoint-param/gr-endpoint-param.js';
import './plugins/gr-endpoint-slot/gr-endpoint-slot.js';
import './plugins/gr-external-style/gr-external-style.js';
import './plugins/gr-plugin-host/gr-plugin-host.js';
import './settings/gr-cla-view/gr-cla-view.js';
import './settings/gr-registration-dialog/gr-registration-dialog.js';
import './settings/gr-settings-view/gr-settings-view.js';
import './shared/gr-fixed-panel/gr-fixed-panel.js';
import './shared/gr-lib-loader/gr-lib-loader.js';
import './shared/gr-rest-api-interface/gr-rest-api-interface.js';
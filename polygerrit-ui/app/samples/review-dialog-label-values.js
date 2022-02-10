/**
 * @license
 * Copyright (C) 2022 The Android Open Source Project
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
 * This plugin will replace the Code-Review label's -1 and +1 values with
 * thumbs-up and thumbs-down emojis in the reply dialog.
 */
Gerrit.install(plugin => {
  const labelNamesToDisplayValues = new Map([
    [
      'Code-Review', new Map([
        ['-1', '👎'],
        ['+1', '👍'],
      ]),
    ],
  ]);
  plugin.on('reply-label-value',
      labelName => labelNamesToDisplayValues.get(labelName));
});

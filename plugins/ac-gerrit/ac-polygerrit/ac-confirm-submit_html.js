/**
 * @license
 * Copyright (C) 2021 AudioCodes Ltd.
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

export const htmlTemplate = Polymer.html`
<template is="dom-if" if="[[otherUser]]">
  <p style="color:red"><strong>Heads Up!</strong> Submitting [[otherUser]]'s change.</p>
</template>
<template is="dom-if" if="[[otherChange]]">
  <p style="color:red"><strong>Heads Up!</strong> Change was found in SuperModule. This will also submit change [[otherChange]].</p>
</template>
`;
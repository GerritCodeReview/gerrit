/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import '@polymer/iron-icon/iron-icon';
import '@polymer/iron-iconset-svg/iron-iconset-svg';
const $_documentContainer = document.createElement('template');

$_documentContainer.innerHTML = `<iron-iconset-svg name="gr-icons" size="24">
  <svg>
    <defs>
      <!-- This SVG is a copy from iron-icons https://github.com/PolymerElements/iron-icons/blob/master/iron-icons.js -->
      <g id="expand-less"><path d="M12 8l-6 6 1.41 1.41L12 10.83l4.59 4.58L18 14z"></path></g>
      <!-- This SVG is a copy from iron-icons https://github.com/PolymerElements/iron-icons/blob/master/iron-icons.js -->
      <g id="expand-more"><path d="M16.59 8.59L12 13.17 7.41 8.59 6 10l6 6 6-6z"></path></g>
      <!-- This SVG is a copy from material.io https://material.io/icons/#unfold_more -->
      <g id="unfold-more"><path d="M0 0h24v24H0z" fill="none"></path><path d="M12 5.83L15.17 9l1.41-1.41L12 3 7.41 7.59 8.83 9 12 5.83zm0 12.34L8.83 15l-1.41 1.41L12 21l4.59-4.59L15.17 15 12 18.17z"></path></g>
      <!-- This SVG is a copy from iron-icons https://github.com/PolymerElements/iron-icons/blob/master/iron-icons.js -->
      <g id="search"><path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"></path></g>
      <!-- This SVG is a copy from iron-icons https://github.com/PolymerElements/iron-icons/blob/master/iron-icons.js -->
      <g id="settings"><path d="M19.43 12.98c.04-.32.07-.64.07-.98s-.03-.66-.07-.98l2.11-1.65c.19-.15.24-.42.12-.64l-2-3.46c-.12-.22-.39-.3-.61-.22l-2.49 1c-.52-.4-1.08-.73-1.69-.98l-.38-2.65C14.46 2.18 14.25 2 14 2h-4c-.25 0-.46.18-.49.42l-.38 2.65c-.61.25-1.17.59-1.69.98l-2.49-1c-.23-.09-.49 0-.61.22l-2 3.46c-.13.22-.07.49.12.64l2.11 1.65c-.04.32-.07.65-.07.98s.03.66.07.98l-2.11 1.65c-.19.15-.24.42-.12.64l2 3.46c.12.22.39.3.61.22l2.49-1c.52.4 1.08.73 1.69.98l.38 2.65c.03.24.24.42.49.42h4c.25 0 .46-.18.49-.42l.38-2.65c.61-.25 1.17-.59 1.69-.98l2.49 1c.23.09.49 0 .61-.22l2-3.46c.12-.22.07-.49-.12-.64l-2.11-1.65zM12 15.5c-1.93 0-3.5-1.57-3.5-3.5s1.57-3.5 3.5-3.5 3.5 1.57 3.5 3.5-1.57 3.5-3.5 3.5z"></path></g>
      <!-- This SVG is a copy from iron-icons https://github.com/PolymerElements/iron-icons/blob/master/iron-icons.js -->
      <g id="create"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"></path></g>
      <!-- This SVG is a copy from iron-icons https://github.com/PolymerElements/iron-icons/blob/master/iron-icons.js -->
      <g id="star"><path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"></path></g>
      <!-- This SVG is a copy from iron-icons https://github.com/PolymerElements/iron-icons/blob/master/iron-icons.js -->
      <g id="star-border"><path d="M22 9.24l-7.19-.62L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21 12 17.27 18.18 21l-1.63-7.03L22 9.24zM12 15.4l-3.76 2.27 1-4.28-3.32-2.88 4.38-.38L12 6.1l1.71 4.04 4.38.38-3.32 2.88 1 4.28L12 15.4z"></path></g>
      <!-- This SVG is a copy from iron-icons https://github.com/PolymerElements/iron-icons/blob/master/iron-icons.js -->
      <g id="close"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"></path></g>
      <!-- This SVG is a copy from iron-icons https://github.com/PolymerElements/iron-icons/blob/master/iron-icons.js -->
      <g id="chevron-left"><path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z"></path></g>
      <!-- This SVG is a copy from iron-icons https://github.com/PolymerElements/iron-icons/blob/master/iron-icons.js -->
      <g id="chevron-right"><path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z"></path></g>
      <!-- This SVG is a copy from iron-icons https://github.com/PolymerElements/iron-icons/blob/master/iron-icons.js -->
      <g id="more-vert"><path d="M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"></path></g>
      <!-- This SVG is a copy from iron-icons https://github.com/PolymerElements/iron-icons/blob/master/iron-icons.js -->
      <g id="deleteEdit"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"></path></g>
      <!-- This SVG is a copy from iron-icons https://github.com/PolymerElements/iron-icons/blob/master/editor-icons.html -->
      <g id="publishEdit"><path d="M5 4v2h14V4H5zm0 10h4v6h6v-6h4l-7-7-7 7z"></path></g>
      <!-- This SVG is a copy from iron-icons https://github.com/PolymerElements/iron-icons/blob/master/editor-icons.html -->
      <g id="delete"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"></path></g>
      <!-- This SVG is a copy from iron-icons https://github.com/PolymerElements/iron-icons/blob/master/iron-icons.js -->
      <g id="help"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 17h-2v-2h2v2zm2.07-7.75l-.9.92C13.45 12.9 13 13.5 13 15h-2v-.5c0-1.1.45-2.1 1.17-2.83l1.24-1.26c.37-.36.59-.86.59-1.41 0-1.1-.9-2-2-2s-2 .9-2 2H8c0-2.21 1.79-4 4-4s4 1.79 4 4c0 .88-.36 1.68-.93 2.25z"></path></g>
      <!-- This SVG is a copy from iron-icons https://github.com/PolymerElements/iron-icons/blob/master/iron-icons.js -->
      <g id="info"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"></path></g>
      <!-- This SVG is a copy from iron-icons https://github.com/PolymerElements/iron-icons/blob/master/iron-icons.js -->
      <g id="info-outline"><path d="M11 17h2v-6h-2v6zm1-15C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zM11 9h2V7h-2v2z"></path></g>
      <!-- This SVG is a copy from material.io https://material.io/icons/#ic_hourglass_full-->
      <g id="hourglass"><path d="M6 2v6h.01L6 8.01 10 12l-4 4 .01.01H6V22h12v-5.99h-.01L18 16l-4-4 4-3.99-.01-.01H18V2H6z"></path><path d="M0 0h24v24H0V0z" fill="none"></path></g>
      <!-- This SVG is a copy from material.io https://material.io/icons/#mode_comment-->
      <g id="comment"><path d="M21.99 4c0-1.1-.89-2-1.99-2H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h14l4 4-.01-18z"></path><path d="M0 0h24v24H0z" fill="none"></path></g>
      <!-- This SVG is a copy from material.io https://material.io/icons/#calendar_today-->
      <g id="calendar"><path d="M20 3h-1V1h-2v2H7V1H5v2H4c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 18H4V8h16v13z"></path><path d="M0 0h24v24H0z" fill="none"></path></g>
      <!-- This SVG is a copy from iron-icons https://github.com/PolymerElements/iron-icons/blob/master/iron-icons.js -->
      <g id="error"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"></path></g>
      <!-- This SVG is a copy from iron-icons https://github.com/PolymerElements/iron-icons/blob/master/iron-icons.js -->
      <g id="lightbulb-outline"><path d="M9 21c0 .55.45 1 1 1h4c.55 0 1-.45 1-1v-1H9v1zm3-19C8.14 2 5 5.14 5 9c0 2.38 1.19 4.47 3 5.74V17c0 .55.45 1 1 1h6c.55 0 1-.45 1-1v-2.26c1.81-1.27 3-3.36 3-5.74 0-3.86-3.14-7-7-7zm2.85 11.1l-.85.6V16h-4v-2.3l-.85-.6C7.8 12.16 7 10.63 7 9c0-2.76 2.24-5 5-5s5 2.24 5 5c0 1.63-.8 3.16-2.15 4.1z"></path></g>
      <!-- This is a custom PolyGerrit SVG -->
      <g id="side-by-side"><path d="M17.1578947,10.8888889 L2.84210526,10.8888889 C2.37894737,10.8888889 2,11.2888889 2,11.7777778 L2,17.1111111 C2,17.6 2.37894737,18 2.84210526,18 L17.1578947,18 C17.6210526,18 18,17.6 18,17.1111111 L18,11.7777778 C18,11.2888889 17.6210526,10.8888889 17.1578947,10.8888889 Z M17.1578947,2 L2.84210526,2 C2.37894737,2 2,2.4 2,2.88888889 L2,8.22222222 C2,8.71111111 2.37894737,9.11111111 2.84210526,9.11111111 L17.1578947,9.11111111 C17.6210526,9.11111111 18,8.71111111 18,8.22222222 L18,2.88888889 C18,2.4 17.6210526,2 17.1578947,2 Z M16.1973628,2 L2.78874238,2 C2.35493407,2 2,2.4 2,2.88888889 L2,8.22222222 C2,8.71111111 2.35493407,9.11111111 2.78874238,9.11111111 L16.1973628,9.11111111 C16.6311711,9.11111111 16.9861052,8.71111111 16.9861052,8.22222222 L16.9861052,2.88888889 C16.9861052,2.4 16.6311711,2 16.1973628,2 Z" id="Shape" transform="scale(1.2) translate(10.000000, 10.000000) rotate(-90.000000) translate(-10.000000, -10.000000)"></path></g>
      <!-- This is a custom PolyGerrit SVG -->
      <g id="unified"><path d="M4,2 L17,2 C18.1045695,2 19,2.8954305 19,4 L19,16 C19,17.1045695 18.1045695,18 17,18 L4,18 C2.8954305,18 2,17.1045695 2,16 L2,4 L2,4 C2,2.8954305 2.8954305,2 4,2 L4,2 Z M4,7 L4,9 L17,9 L17,7 L4,7 Z M4,11 L4,13 L17,13 L17,11 L4,11 Z" id="Combined-Shape" transform="scale(1.12, 1.2)"></path></g>
      <!-- This SVG is a copy from iron-icons https://github.com/PolymerElements/iron-icons/blob/master/iron-icons.js -->
      <g id="content-copy"><path d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"></path></g>
      <!-- This is a custom PolyGerrit SVG -->
      <g id="check"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"></path></g>
      <!-- This is a custom PolyGerrit SVG -->
      <g id="robot"><path d="M4.137453,5.61015591 L4.54835569,1.5340419 C4.5717665,1.30180904 4.76724872,1.12504213 5.00065859,1.12504213 C5.23327176,1.12504213 5.42730868,1.30282046 5.44761309,1.53454578 L5.76084628,5.10933916 C6.16304484,5.03749412 6.57714381,5 7,5 L17,5 C20.8659932,5 24,8.13400675 24,12 L24,15.1250421 C24,18.9910354 20.8659932,22.1250421 17,22.1250421 L7,22.1250421 C3.13400675,22.1250421 2.19029351e-15,18.9910354 0,15.1250421 L0,12 C-3.48556243e-16,9.15382228 1.69864167,6.70438358 4.137453,5.61015591 Z M5.77553049,6.12504213 C3.04904264,6.69038358 1,9.10590202 1,12 L1,15.1250421 C1,18.4387506 3.6862915,21.1250421 7,21.1250421 L17,21.1250421 C20.3137085,21.1250421 23,18.4387506 23,15.1250421 L23,12 C23,8.6862915 20.3137085,6 17,6 L7,6 C6.60617231,6 6.2212068,6.03794347 5.84855971,6.11037415 L5.84984496,6.12504213 L5.77553049,6.12504213 Z M6.93003717,6.95027711 L17.1232083,6.95027711 C19.8638332,6.95027711 22.0855486,9.17199258 22.0855486,11.9126175 C22.0855486,14.6532424 19.8638332,16.8749579 17.1232083,16.8749579 L6.93003717,16.8749579 C4.18941226,16.8749579 1.9676968,14.6532424 1.9676968,11.9126175 C1.9676968,9.17199258 4.18941226,6.95027711 6.93003717,6.95027711 Z M7.60124392,14.0779303 C9.03787127,14.0779303 10.2024878,12.9691885 10.2024878,11.6014862 C10.2024878,10.2337839 9.03787127,9.12504213 7.60124392,9.12504213 C6.16461657,9.12504213 5,10.2337839 5,11.6014862 C5,12.9691885 6.16461657,14.0779303 7.60124392,14.0779303 Z M16.617997,14.1098288 C18.0638768,14.1098288 19.2359939,12.9939463 19.2359939,11.6174355 C19.2359939,10.2409246 18.0638768,9.12504213 16.617997,9.12504213 C15.1721172,9.12504213 14,10.2409246 14,11.6174355 C14,12.9939463 15.1721172,14.1098288 16.617997,14.1098288 Z M9.79751216,18.1250421 L15,18.1250421 L15,19.1250421 C15,19.6773269 14.5522847,20.1250421 14,20.1250421 L10.7975122,20.1250421 C10.2452274,20.1250421 9.79751216,19.6773269 9.79751216,19.1250421 L9.79751216,18.1250421 Z"></path></g>
      <!-- This is a custom PolyGerrit SVG -->
      <g id="abandon"><path d="M17.65675,17.65725 C14.77275,20.54125 10.23775,20.75625 7.09875,18.31525 L18.31475,7.09925 C20.75575,10.23825 20.54075,14.77325 17.65675,17.65725 M6.34275,6.34325 C9.22675,3.45925 13.76275,3.24425 16.90075,5.68525 L5.68475,16.90125 C3.24375,13.76325 3.45875,9.22725 6.34275,6.34325 M19.07075,4.92925 C15.16575,1.02425 8.83375,1.02425 4.92875,4.92925 C1.02375,8.83425 1.02375,15.16625 4.92875,19.07125 C8.83375,22.97625 15.16575,22.97625 19.07075,19.07125 C22.97575,15.16625 22.97575,8.83425 19.07075,4.92925"></path></g>
      <!-- This is a custom PolyGerrit SVG -->
      <g id="edit"><path d="M3,17.2525 L3,21.0025 L6.75,21.0025 L17.81,9.9425 L14.06,6.1925 L3,17.2525 L3,17.2525 Z M20.71,7.0425 C21.1,6.6525 21.1,6.0225 20.71,5.6325 L18.37,3.2925 C17.98,2.9025 17.35,2.9025 16.96,3.2925 L15.13,5.1225 L18.88,8.8725 L20.71,7.0425 L20.71,7.0425 Z"></path></g>
      <!-- This is a custom PolyGerrit SVG -->
      <g id="rebase"><path d="M15.5759,19.4241 L14.5861,20.4146 L11.7574,23.2426 L10.3434,21.8286 L12.171569,20 L7.82933006,20 C7.41754308,21.1652555 6.30635522,22 5,22 C3.343,22 2,20.657 2,19 C2,17.6936448 2.83474451,16.5824569 4,16.1706699 L4,7.82933006 C2.83474451,7.41754308 2,6.30635522 2,5 C2,3.343 3.343,2 5,2 C6.30635522,2 7.41754308,2.83474451 7.82933006,4 L12.1715,4 L10.3431,2.1716 L11.7571,0.7576 L15.36365,4.3633 L16.0000001,4.99920039 C16.0004321,3.34256796 17.3432665,2 19,2 C20.657,2 22,3.343 22,5 C22,6.30635522 21.1652555,7.41754308 20,7.82933006 L20,16.1706699 C21.1652555,16.5824569 22,17.6936448 22,19 C22,20.657 20.657,22 19,22 C17.343,22 16,20.657 16,19 L15.5759,19.4241 Z M12.1715,18 L10.3431,16.1716 L11.7571,14.7576 L15.36365,18.3633 L16.0000001,18.9992004 C16.0003407,17.6931914 16.8349823,16.5823729 18,16.1706699 L18,7.82933006 C16.8347445,7.41754308 16,6.30635522 16,5 L15.5759,5.4241 L14.5861,6.4146 L11.7574,9.2426 L10.3434,7.8286 L12.171569,6 L7.82933006,6 C7.52807271,6.85248394 6.85248394,7.52807271 6,7.82933006 L6,16.1706699 C6.85248394,16.4719273 7.52807271,17.1475161 7.82933006,18 L12.1715,18 Z"></path></g>
      <!-- This is a custom PolyGerrit SVG -->
      <g id="rebaseEdit"><path d="M15.5759,19.4241 L14.5861,20.4146 L11.7574,23.2426 L10.3434,21.8286 L12.171569,20 L7.82933006,20 C7.41754308,21.1652555 6.30635522,22 5,22 C3.343,22 2,20.657 2,19 C2,17.6936448 2.83474451,16.5824569 4,16.1706699 L4,7.82933006 C2.83474451,7.41754308 2,6.30635522 2,5 C2,3.343 3.343,2 5,2 C6.30635522,2 7.41754308,2.83474451 7.82933006,4 L12.1715,4 L10.3431,2.1716 L11.7571,0.7576 L15.36365,4.3633 L16.0000001,4.99920039 C16.0004321,3.34256796 17.3432665,2 19,2 C20.657,2 22,3.343 22,5 C22,6.30635522 21.1652555,7.41754308 20,7.82933006 L20,16.1706699 C21.1652555,16.5824569 22,17.6936448 22,19 C22,20.657 20.657,22 19,22 C17.343,22 16,20.657 16,19 L15.5759,19.4241 Z M12.1715,18 L10.3431,16.1716 L11.7571,14.7576 L15.36365,18.3633 L16.0000001,18.9992004 C16.0003407,17.6931914 16.8349823,16.5823729 18,16.1706699 L18,7.82933006 C16.8347445,7.41754308 16,6.30635522 16,5 L15.5759,5.4241 L14.5861,6.4146 L11.7574,9.2426 L10.3434,7.8286 L12.171569,6 L7.82933006,6 C7.52807271,6.85248394 6.85248394,7.52807271 6,7.82933006 L6,16.1706699 C6.85248394,16.4719273 7.52807271,17.1475161 7.82933006,18 L12.1715,18 Z"></path></g>
      <!-- This is a custom PolyGerrit SVG -->
      <g id="restore"><path d="M12,8 L12,13 L16.28,15.54 L17,14.33 L13.5,12.25 L13.5,8 L12,8 Z M13,3 C8.03,3 4,7.03 4,12 L1,12 L4.89,15.89 L4.96,16.03 L9,12 L6,12 C6,8.13 9.13,5 13,5 C16.87,5 20,8.13 20,12 C20,15.87 16.87,19 13,19 C11.07,19 9.32,18.21 8.06,16.94 L6.64,18.36 C8.27,19.99 10.51,21 13,21 C17.97,21 22,16.97 22,12 C22,7.03 17.97,3 13,3 Z"></path></g>
      <!-- This is a custom PolyGerrit SVG -->
      <g id="revert"><path d="M12.3,8.5 C9.64999995,8.5 7.24999995,9.49 5.39999995,11.1 L1.79999995,7.5 L1.79999995,16.5 L10.8,16.5 L7.17999995,12.88 C8.56999995,11.72 10.34,11 12.3,11 C15.84,11 18.85,13.31 19.9,16.5 L22.27,15.72 C20.88,11.53 16.95,8.5 12.3,8.5"></path></g>
      <g id="revert_submission"><path d="M12.3,8.5 C9.64999995,8.5 7.24999995,9.49 5.39999995,11.1 L1.79999995,7.5 L1.79999995,16.5 L10.8,16.5 L7.17999995,12.88 C8.56999995,11.72 10.34,11 12.3,11 C15.84,11 18.85,13.31 19.9,16.5 L22.27,15.72 C20.88,11.53 16.95,8.5 12.3,8.5"></path></g>
      <!-- This is a custom PolyGerrit SVG -->
      <g id="stopEdit"><path d="M4 4 20 4 20 20 4 20z"></path></g>
      <!-- This is a custom PolyGerrit SVG -->
      <g id="submit"><path d="M22.23,5 L11.65,15.58 L7.47000001,11.41 L6.06000001,12.82 L11.65,18.41 L23.649,6.41 L22.23,5 Z M16.58,5 L10.239,11.34 L11.65,12.75 L17.989,6.41 L16.58,5 Z M0.400000006,12.82 L5.99000001,18.41 L7.40000001,17 L1.82000001,11.41 L0.400000006,12.82 Z"></path></g>
      <!-- This is a custom PolyGerrit SVG -->
      <g id="review"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"></path></g>
      <!-- This is a custom PolyGerrit SVG -->
      <g id="zeroState"><path d="M22 9V7h-2V5c0-1.1-.9-2-2-2H4c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2v-2h2v-2h-2v-2h2v-2h-2V9h2zm-4 10H4V5h14v14zM6 13h5v4H6zm6-6h4v3h-4zM6 7h5v5H6zm6 4h4v6h-4z"></path></g>
      <!-- This SVG is an adaptation of material.io https://material.io/icons/#label_important-->
      <g id="attention"><path d="M1 23 l13 0 c.67 0 1.27 -.33 1.63 -.84 l7.37 -10.16 l-7.37 -10.16 c-.36 -.51 -.96 -.84 -1.63 -.84 L1 1 L7 12 z"></path></g>
      <!-- This SVG is a copy from material.io https://material.io/icons/#pets-->
      <g id="pets"><circle cx="4.5" cy="9.5" r="2.5"/><circle cx="9" cy="5.5" r="2.5"/><circle cx="15" cy="5.5" r="2.5"/><circle cx="19.5" cy="9.5" r="2.5"/><path d="M17.34 14.86c-.87-1.02-1.6-1.89-2.48-2.91-.46-.54-1.05-1.08-1.75-1.32-.11-.04-.22-.07-.33-.09-.25-.04-.52-.04-.78-.04s-.53 0-.79.05c-.11.02-.22.05-.33.09-.7.24-1.28.78-1.75 1.32-.87 1.02-1.6 1.89-2.48 2.91-1.31 1.31-2.92 2.76-2.62 4.79.29 1.02 1.02 2.03 2.33 2.32.73.15 3.06-.44 5.54-.44h.18c2.48 0 4.81.58 5.54.44 1.31-.29 2.04-1.31 2.33-2.32.31-2.04-1.3-3.49-2.61-4.8z"/><path d="M0 0h24v24H0z" fill="none"/></g>
      <!-- This SVG is a copy from material.io https://material.io/icons/#visibility-->
      <g id="ready"><path d="M0 0h24v24H0z" fill="none"/><path d="M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z"/></g>
      <!-- This SVG is a copy from iron-icons https://github.com/PolymerElements/iron-icons -->
      <g id="schedule"><path d="M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z"></path></g>
      <!-- This SVG is a copy from material.io https://material.io/icons/#bug_report-->
      <g id="bug"><path d="M0 0h24v24H0z" fill="none"/><path d="M20 8h-2.81c-.45-.78-1.07-1.45-1.82-1.96L17 4.41 15.59 3l-2.17 2.17C12.96 5.06 12.49 5 12 5c-.49 0-.96.06-1.41.17L8.41 3 7 4.41l1.62 1.63C7.88 6.55 7.26 7.22 6.81 8H4v2h2.09c-.05.33-.09.66-.09 1v1H4v2h2v1c0 .34.04.67.09 1H4v2h2.81c1.04 1.79 2.97 3 5.19 3s4.15-1.21 5.19-3H20v-2h-2.09c.05-.33.09-.66.09-1v-1h2v-2h-2v-1c0-.34-.04-.67-.09-1H20V8zm-6 8h-4v-2h4v2zm0-4h-4v-2h4v2z"/></g>
    </defs>
  </svg>
</iron-iconset-svg>`;

document.head.appendChild($_documentContainer.content);

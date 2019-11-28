// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

const process = require("process");
const fs = require("fs");
const path = require("path");
//fs.symlinkSync(path.join(process.cwd(), "external/ui_npm"), "polygerrit-ui/app/node_modules");
console.log(process.argv);
console.log(process.cwd());
console.log(require("fs").readdirSync("./"));
console.log(require("fs").readdirSync("external"));
process.chdir("polygerrit-ui/app");
console.log(require("fs").readdirSync("./"));

console.log(require.resolve("web-component-tester/runner"));
//process.exit(1);
// console.log(process.env);
// console.log(process.argv);
// process.exit(1);

// process.on('exit', function (){
//   console.log('Goodbye!');
//   process.exit(1);
// });

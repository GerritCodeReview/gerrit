# Gerrit Polymer Frontend

Follow the
[setup instructions for Gerrit backend developers](https://gerrit-review.googlesource.com/Documentation/dev-readme.html)
where applicable, the most important command is:

```sh
git clone --recurse-submodules https://gerrit.googlesource.com/gerrit
```

The --recurse-submodules option is needed on git clone to ensure that the core plugins, which are included as git submodules, are also cloned.

Then make sure to install the commit-hook that will set up the `ChangeId` for
each push to gerrit-reviews.

```sh
cd gerrit && (
  cd .git/hooks
  ln -s ../../resources/com/google/gerrit/server/tools/root/hooks/commit-msg
)
```

## Installing [Bazel](https://bazel.build/)

Follow the instructions
[here](https://gerrit-review.googlesource.com/Documentation/dev-bazel.html#_installation)
to get and install Bazel.

## Installing [Node.js](https://nodejs.org/en/download/) and npm packages

**Note**: Switch between an old branch with bower_components and a new branch with ui-npm
packages (or vice versa) can lead to some build errors. To avoid such errors clean up the build
repository:
```sh
rm -rf node_modules/ \
    polygerrit-ui/node_modules/ \
    polygerrit-ui/app/node_modules \
    tools/node_tools/node_modules

bazel clean
```

If it doesn't help also try to run
```sh
bazel clean --expunge
```

The minimum nodejs version supported is 8.x+

```sh
# Debian experimental
sudo apt-get install nodejs
sudo apt-get install npm

# OS X with Homebrew
brew install node
brew install npm
```

All other platforms:
[download from nodejs.org](https://nodejs.org/en/download/).

or use [nvm - Node Version Manager](https://github.com/nvm-sh/nvm).

### Additional packages

We have several bazel commands to install packages we may need for FE development.

For first time users to get the local server up, `npm start` should be enough and will take care of all of them for you.

```sh
# Install packages from root-level packages.json
bazel fetch @npm//:node_modules

# Install packages from polygerrit-ui/app/packages.json
bazel fetch @ui_npm//:node_modules

# Install packages from polygerrit-ui/packages.json
bazel fetch @ui_dev_npm//:node_modules

# Install packages from tools/node_tools/packages.json
bazel fetch @tools_npm//:node_modules
```

More information for installing and using nodejs rules can be found here https://bazelbuild.github.io/rules_nodejs/install.html

### Upgrade to @bazel-scoped packages

It might be necessary to run this command to upgrade to major `rules_nodejs` release:

```sh
yarn remove @bazel/...
```

## Setup typescript support in the IDE

Modern IDE should automatically handle typescript settings from the 
`pollygerrit-ui/app/tsconfig.json` files. IDE places compiled files in the
`.ts-out/pg` directory at the root of gerrit workspace and you can configure IDE
to exclude the whole .ts-out directory. To do it in the IntelliJ IDEA click on
this directory and select "Mark Directory As > Excluded" in the context menu.

However, if you receive some errors from IDE, you can try to configure IDE
manually. For example, if IntelliJ IDEA shows
`Cannot find parent 'tsconfig.json'` error, you can try to setup typescript
options `--project polygerrit-ui/app/tsconfig.json` in the IDE settings.


## Serving files locally

#### Go server

To test the local Polymer frontend against production data or a local test site execute:

```sh
./polygerrit-ui/run-server.sh

// or
npm run start
```

These commands start the [simple hand-written Go webserver](https://gerrit.googlesource.com/gerrit/+/master/polygerrit-ui/server.go).
Mostly it just switches between serving files locally and proxying the real
server based on the file name. It also does some basic response rewriting, e.g.
it patches the `config/server/info` response with plugin information provided on
the command line:

```sh
./polygerrit-ui/run-server.sh --plugins=plugins/my_plugin/static/my_plugin.js
```

If any issues occured, please refer to the Troubleshooting section at the bottom or contact the team!

## Running locally against production data

### Local website

Start [Go server](#go-server) and then visit http://localhost:8081

The biggest draw back of this method is that you cannot log in, so cannot test
scenarios that require it.

#### Chrome extension: Gerrit FE Dev Helper

To be able to bypass the auth and also help improve the productivity of Gerrit FE developers,
we created this chrome extension: [Gerrit FE Dev Helper](https://chrome.google.com/webstore/detail/gerrit-fe-dev-helper/jimgomcnodkialnpmienbomamgomglkd).

It basically works as a proxy that will block / redirect requests from current sites to any given url base on certain rules.

The source code is in [Gerrit - gerrit-fe-dev-helper](https://gerrit-review.googlesource.com/q/project:gerrit-fe-dev-helper), contributions are welcomed!

To use this extension, just follow its [readme here](https://gerrit.googlesource.com/gerrit-fe-dev-helper/+/master/README.md).

## Running locally against a Gerrit test site

Set up a local test site once:

1. [Build Gerrit](https://gerrit-review.googlesource.com/Documentation/dev-bazel.html#_gerrit_development_war_file)
2. [Set up a local test site](https://gerrit-review.googlesource.com/Documentation/dev-readme.html#init).
3. Optionally [populate](https://gerrit.googlesource.com/gerrit/+/master/contrib/populate-fixture-data.py) your test site with some test data.

For running a locally built Gerrit war against your test instance use
[this command](https://gerrit-review.googlesource.com/Documentation/dev-readme.html#run_daemon).

If you want to serve the Polymer frontend directly from the sources in `polygerrit_ui/app/` instead of from the war:
1. Start [Go server](#go-server)
2. Add the `--dev-cdn` option:

```sh
$(bazel info output_base)/external/local_jdk/bin/java \
    -DsourceRoot=$(bazel info workspace) \
    -jar bazel-bin/gerrit.war daemon \
    -d $GERRIT_SITE \
    --console-log \
    --dev-cdn http://localhost:8081
```

*NOTE* You can use any other cdn here, for example: https://cdn.googlesource.com/polygerrit_ui/678.0

## Running Tests

For daily development you typically only want to run and debug individual tests.
There are several ways to run tests.

* Run all tests in headless mode (exactly like CI does):
```sh
npm run test
```
This command uses bazel rules for running frontend tests. Bazel fetches
all nessecary dependencies and runs all required rules.

* Run all tests in debug mode (the command opens Chrome browser with
the default Karma page; you should click the "Debug" button to start testing):
```sh
# The following command doesn't compile code before tests
npm run test:debug
```

* Run a single test file:
```
# Headless mode (doesn't compile code before run)
npm run test:single async-foreach-behavior_test.js

# Debug mode (doesn't compile code before run)
npm run test:debug async-foreach-behavior_test.js
```

When converting a test file to typescript, the command for running tests is
still using the .js suffix and not the new .ts suffix.

Commands `test:debug` and `test:single` assumes that compiled code is located
in the `./ts-out/polygerrit-ui/app` directory. It's up to you how to achieve it.
For example, the following options are possible:
* You can configure IDE for recompiling source code on changes
* You can use `compile:local` command for running compiler once and
`compile:watch` for running compiler in watch mode (`compile:...` places
compile code exactly in the `./ts-out/polygerrit-ui/app` directory)

```sh
# Compile frontend once and run tests from a file:
npm run compile:local && npm run test:single async-foreach-behavior_test.js

# Watch mode:
## Terminal 1:
npm run compile:watch
## Terminal 2:
npm run test:debug async-foreach-behavior_test.js
```

* You can run tests in IDE. :
  - [IntelliJ: running unit tests on Karma](https://www.jetbrains.com/help/idea/running-unit-tests-on-karma.html#ws_karma_running)
  - You should configure IDE to compile typescript before running tests.

**NOTE**: Bazel plugin for IntelliJ has a bug - it recompiles typescript
project only if .ts and/or .d.ts files have been changed. If only .js files
were changed, the plugin doesn't run compiler. As a workaround, setup
"Run npm script 'compile:local" action instead of the "Compile Typescript" in
the "Before launch" section for IntelliJ. This is a temporary problem until
typescript migration is complete.

## Running Templates Test
The templates test validates polymer templates. The test convert polymer
templates into a plain typescript code and then run TS compiler. The test fails
if TS compiler reports errors; in this case you will see TS errors in
the log/output. Gerrit-CI automatically runs templates test.

**Note**: Files defined in `ignore_templates_list` (`polygerrit-ui/app/BUILD`)
are excluded from code generation and checking. If you don't know how to fix
a problem, you can add a problematic template in the list.

* To run test locally, use npm command:
``` sh
npm run polytest
```

* Often, the output from the previous command is not clear (cryptic TS errors).
In this case, run the command
```sh
npm run polytest:dev
```
This command (re)creates the `polygerrit-ui/app/tmpl_out` directory and put
generated files into it. For each polygerrit .ts file there is a generated file
in the `tmpl_out` directory. If an original file doesn't contain a polymer
template, the generated file is empty.

You can open a problematic file in IDE and fix the problem. Ensure, that IDE
uses `polygerrit-ui/app/tsconfig.json` as a project (usually, this is default).

### Generated file overview

A generated file starts with imports followed by a static content with
different type definitions. You can skip this part - it doesn't contains
anything usefule.

After the static content there is a class definition. Example:
```typescript
export class GrCreateGroupDialogCheck extends GrCreateGroupDialog {
  templateCheck() {
    // Converted template
    // Each HTML element from the template is wrapped into own block.
  }
}
```

The converted template usually quite straightforward, but in some cases
additional functions are added. For example, `<element x=[[y.a]]>` converts into
`el.x = y!.a` if y is a simple type. However, if y has a union type, like - `y:A|B`,
then the generated code looks like `el.x=__f(y)!.a` (`y!.a` may result in a TS error
if `a` is defined only in one type of a union). 

## Style guide

We follow the [Google JavaScript Style Guide](https://google.github.io/styleguide/javascriptguide.xml)
with a few exceptions. When in doubt, remain consistent with the code around you.

In addition, we encourage the use of [ESLint](http://eslint.org/).
It is available as a command line utility, as well as a plugin for most editors
and IDEs.

`eslint-config-google` is a port of the Google JS Style Guide to an ESLint
config module, and `eslint-plugin-html` allows ESLint to lint scripts inside
HTML.
We have an .eslintrc.json config file in the polygerrit-ui/ directory configured
to enforce the preferred style of the PolyGerrit project.
After installing, you can use `eslint` on any new file you create.
In addition, you can supply the `--fix` flag to apply some suggested fixes for
simple style issues.
If you modify JS inside of `<script>` tags, like for test suites, you may have
to supply the `--ext .html` flag.

Some useful commands:

* To run ESLint on the whole app, less some dependency code:

```sh
npm run eslint
```

* To run ESLint on just the subdirectory you modified:

```sh
node_modules/eslint/bin/eslint.js --ext .html,.js polygerrit-ui/app/$YOUR_DIR_HERE
```

* To run the linter on all of your local changes:

```sh
git diff --name-only HEAD | xargs node_modules/eslint/bin/eslint.js --ext .html,.js
```

We also use the `polylint` tool to lint use of Polymer. To install polylint,
execute the following command.

To run polylint, execute the following command.

```sh
bazel test //polygerrit-ui/app:polylint_test
```

or

```sh
npm run polylint
```

## Migrating tests to Typescript

You can use the following steps for migrating tests to Typescript:

1. Rename the `_test.js` file to `_test.ts`
2. Remove `.js` extensions from all imports:
   ```
   // Before:
   import ... from 'x/y/z.js`
 
   // After
   import .. from 'x/y/z'
   ```
3. Fix typescript and eslint errors.

Common errors and fixes are:

* An object in the test doesn't have all required properties. You can use
existing helpers to create an object with all required properties:
```
// Before:
sinon.stub(element.restApiService, 'getPreferences').returns(
    Promise.resolve({default_diff_view: 'UNIFIED'}));

// After:
Promise.resolve({
  ...createPreferences(),
  default_diff_view: DiffViewMode.UNIFIED,
})
```

Some helpers receive parameters:
```
// Before
element._change = {
  change_id: 'Iad9dc96274af6946f3632be53b106ef80f7ba6ca',
  revisions: {
    rev1: {_number: 1, commit: {parents: []}},
    rev2: {_number: 2, commit: {parents: []}},
  },
  current_revision: 'rev1',
  status: ChangeStatus.MERGED,
  labels: {},
  actions: {},
};

// After
element._change = {
  ...createChange(),
  // The change_id is set by createChange.
  // The exact change_id is not important in the test, so it was removed.
  revisions: {
    rev1: createRevision(1), // _number is a parameter here
    rev2: createRevision(2), // _number is a parameter here
  },
  current_revision: 'rev1' as CommitId,
  status: ChangeStatus.MERGED,
  labels: {},
  actions: {},
};
```
* Typescript reports some weird messages about `window` property - sometimes an
IDE adds wrong import. Just remove it.
```
// The wrong import added by IDE, must be removed
import window = Mocha.reporters.Base.window;
```

* `TS2531: Object is possibly 'null'`. To fix use either non-null assertion
operator `!` or nullish coalescing operator `?.`:
```
// Before:
const rows = element
  .shadowRoot.querySelector('table')
  .querySelectorAll('tbody tr');
...
// The _robotCommentThreads declared as _robotCommentThreads?: CommentThread
assert.equal(element._robotCommentThreads.length, 2);
  
// Fix with non-null assertion operator:
const rows = element
  .shadowRoot!.querySelector('table')! // '!' after shadowRoot and querySelector
  .querySelectorAll('tbody tr');

assert.equal(element._robotCommentThreads!.length, 2); 

// Fix with nullish coalescing operator:
 assert.equal(element._robotCommentThreads?.length, 2); 
```
Usually the fix with `!` is preferable, because it gives more clear error
when an intermediate property is `null/undefined`. If the _robotComments is
`undefined` in the example above, the `element._robotCommentThreads!.length`
crashes with the error `Cannot read property 'length' of undefined`. At the
same time the fix with
`?.` doesn't distinct between 2 cases: _robotCommentThreads is `undefined`
and `length` is `undefined`.

* `TS2339: Property '...' does not exist on type 'Element'.` for elements
returned by `querySelector/querySelectorAll`. To fix it, use generic versions
of those methods:
```
// Before:
const radios = parentTable
  .querySelectorAll('input[type=radio]');
const radio = parentRow
  .querySelector('input[type=radio]');

// After:
const radios = parentTable
  .querySelectorAll<HTMLInputElement>('input[type=radio]');
const radio = parentRow
  .querySelector<HTMLInputElement>('input[type=radio]');
```

* Sinon: `TS2339: Property 'lastCall' does not exist on type '...` (the same
for other sinon properties). Store stub/spy in a variable and then use the
variable:
```
// Before:
sinon.stub(GerritNav, 'getUrlForChange')
...
assert.equal(GerritNav.getUrlForChange.lastCall.args[4], '#message-a12345');

// After:
const getUrlStub = sinon.stub(GerritNav, 'getUrlForChange');
...
assert.equal(getUrlStub.lastCall.args[4], '#message-a12345');
```

If you need to define a type for such variable, you can use one of the following
options:
```
suite('my suite', () => {
    // Non static members, option 1
    let updateHeightSpy: SinonSpyMember<typeof element._updateRelatedChangeMaxHeight>;
    // Non static members, option 2
    let updateHeightSpy_prototype: SinonSpyMember<typeof GrChangeView.prototype._updateRelatedChangeMaxHeight>;
    // Static members
    let navigateToChangeStub: SinonStubbedMember<typeof GerritNav.navigateToChange>;
    // For interfaces
    let getMergeableStub: SinonStubbedMember<RestApiService['getMergeable']>;
});
```

* Typescript reports errors when stubbing/faking methods:
```
// The JS code:
const reloadStub = sinon
    .stub(element, '_reload')
    .callsFake(() => Promise.resolve());

stubRestApi('getDiffComments').returns(Promise.resolve({}));
stubRestApi('getDiffRobotComments').returns(Promise.resolve({}));
stubRestApi('getDiffDrafts').returns(Promise.resolve({}));
stubRestApi('_fetchSharedCacheURL').returns(Promise.resolve({}));
```

In such cases, validate the input and output of a stub/fake method. Quite often
tests return null instead of undefined or `[]` instead of `{}`, etc...
Fix types if they are not correct:
```
const reloadStub = sinon
  .stub(element, '_reload')
  // GrChangeView._reload method returns an array
  .callsFake(() => Promise.resolve([])); // return [] here

  ...
  // Fix return type:
  stubRestApi('_fetchSharedCacheURL').returns(Promise.resolve({} as ParsedJSON));
});
```

* If a test requires a `@types/...` library, install the required library
in the `polygerrit_ui/node_modules` and update the `typeRoots` in the
`polygerrit-ui/app/tsconfig_bazel_test.json` file.

The same update should be done if a test requires a .d.ts file from a library
that already exists in `polygerrit_ui/node_modules`.

**Note:** Types from a library located in `polygerrit_ui/app/node_modules` are
handle automatically.

* If a test imports a library from `polygerrit_ui/node_modules` - update
`paths` in `polygerrit-ui/app/tsconfig_bazel_test.json`.
 
## Contributing

Our users report bugs / feature requests related to the UI through [Monorail Issues - PolyGerrit](https://bugs.chromium.org/p/gerrit/issues/list?q=component%3APolyGerrit).

If you want to help, feel free to grab one from those `New` issues without
assignees and send us a change.

If you don't know who to assign to review your code change, you can use
this special account: `gerrit-fe-reviewers@api-project-164060093628.iam.gserviceaccount.com`
and just assign to that account, it will automatically pick two volunteers
from the queue we have for FE reviewers.

If you are willing to join the queue and help the community review changes,
you can create an issue through Monorail and request to join the queue!
We will review your request and start from there.

## Troubleshotting & Frequently asked questions

1. Local host is blank page and console shows missing files from `polymer-bridges`

Its likely you missed the `polymer-bridges` submodule when you clone the `gerrit` repo.

To fix that, run:
```
// fetch the submodule
git submodule update --init --recursive

// reset the workspace (please save your local changes before running this command)
npm run clean

// install all dependencies and start the server
npm start
```

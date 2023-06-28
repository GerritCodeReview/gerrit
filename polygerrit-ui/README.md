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
to get and install Bazel. The `npm install -g @bazel/bazelisk` method is
probably easiest since you will have npm as part of Nodejs.

## Installing [Node.js](https://nodejs.org/en/download/) and npm packages

The minimum nodejs version supported is 10.x+. We recommend at least the latest
LTS (v16 as of October 2022).

```sh
# Debian experimental
sudo apt-get install nodejs
sudo apt-get install npm

# OS X with Homebrew
brew install node@16
brew install npm
```

All other platforms:
[download from nodejs.org](https://nodejs.org/en/download/).

or use [nvm - Node Version Manager](https://github.com/nvm-sh/nvm).

### Additional packages

We have several bazel commands to install packages we may need for FE development.

For first time users to get the local server up, `bazel build gerrit` should be enough and will take care of all of them for you.

```sh
# Install yarn package manager
npm install -g yarn

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

Modern IDEs should automatically handle typescript settings from the
`polygerrit-ui/app/tsconfig.json` files. The `tsc` compiler places compiled
files in the `.ts-out/pg` directory at the root of gerrit workspace and you can
configure the IDE to exclude the whole .ts-out directory. To do it in the
IntelliJ IDEA click on this directory and select "Mark Directory As > Excluded"
in the context menu.

However, if you receive some errors from IDE, you can try to configure IDE
manually. For example, if IntelliJ IDEA shows
`Cannot find parent 'tsconfig.json'` error, you can try to setup typescript
options `--project polygerrit-ui/app/tsconfig.json` in the IDE settings.


## Developing locally

The preferred method for development is to serve the web files locally using the
Web Dev Server and then view a running gerrit instance (local or otherwise) to
replace its web client with the local one using the Gerrit FE Dev Helper
extension.

### Web Dev Server

The [Web Dev Server](https://modern-web.dev/docs/dev-server/overview/) serves
the compiled web files and dependencies unbundled over localhost. Start it using
this command:

```sh
yarn start
```

To inject plugins or other files, we use the [Gerrit FE Dev Helper](https://chrome.google.com/webstore/detail/gerrit-fe-dev-helper/jimgomcnodkialnpmienbomamgomglkd) Chrome extension.

If any issues occured, please refer to the Troubleshooting section at the bottom or contact the team!


### Chrome extension: Gerrit FE Dev Helper

To be able to bypass the auth and also help improve the productivity of Gerrit FE developers,
we created this chrome extension: [Gerrit FE Dev Helper](https://chrome.google.com/webstore/detail/gerrit-fe-dev-helper/jimgomcnodkialnpmienbomamgomglkd).

It basically works as a proxy that will block / redirect requests from current sites to any given url base on certain rules.

The source code is in [Gerrit - gerrit-fe-dev-helper](https://gerrit-review.googlesource.com/q/project:gerrit-fe-dev-helper), contributions are welcomed!

To use this extension, just follow its [readme here](https://gerrit.googlesource.com/gerrit-fe-dev-helper/+/master/README.md).

### Running locally against a Gerrit test site

Set up a local test site once:

1. [Build Gerrit](https://gerrit-review.googlesource.com/Documentation/dev-bazel.html#_gerrit_development_war_file)
2. [Set up a local test site](https://gerrit-review.googlesource.com/Documentation/dev-readme.html#init).
3. Optionally [populate](https://gerrit.googlesource.com/gerrit/+/master/contrib/populate-fixture-data.py) your test site with some test data.

For running a locally built Gerrit war against your test instance use
[this command](https://gerrit-review.googlesource.com/Documentation/dev-readme.html#run_daemon).

If you want to serve the Polymer frontend directly from the sources in `polygerrit_ui/app/` instead of from the war:
1. Start [Web Dev Server](#web-dev-server)
2. Add the `--dev-cdn` option:

```sh
$(bazel info output_base)/external/local_jdk/bin/java \
    -DsourceRoot=$(bazel info workspace) \
    -jar bazel-bin/gerrit.war daemon \
    -d $GERRIT_SITE \
    --console-log \
    --dev-cdn http://localhost:8081
```

The Web Dev Server is currently not serving fonts or other static assets. Follow
[Issue 16341](https://bugs.chromium.org/p/gerrit/issues/detail?id=16341) for
fixing this issue.

*NOTE* You can use any other cdn here, for example: https://cdn.googlesource.com/polygerrit_ui/678.0

## Running Tests

For daily development you typically only want to run and debug individual tests.
Our tests run using the
[Web Test Runner](https://modern-web.dev/docs/test-runner/overview/). There are
several ways to trigger tests:

* Run all tests once:
```sh
yarn test
```

* Run all tests and then watches for changes. Change a file will trigger all
tests affected by the changes.
```sh
yarn test:watch
```

* Run all tests once under bazel:
```sh
./polygerrit-ui/app/run_test.sh
```

* Run a single test file and rerun on any changes affecting it:
```
yarn test:single "**/gr-comment_test.ts"
```

Compiling code:
```sh
# Compile frontend once to check for type errors:
yarn compile

# Watch mode:
yarn compile:watch
```

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
yarn eslint
```

* To run ESLint on just the subdirectory you modified:

```sh
node_modules/eslint/bin/eslint.js --ext .html,.js polygerrit-ui/app/$YOUR_DIR_HERE
```

* To run the linter on all of your local changes:

```sh
git diff --name-only HEAD | xargs node_modules/eslint/bin/eslint.js --ext .html,.js
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

Our users report bugs / feature requests related to the UI through the Gerrit
Tracker on the [WebFrontend](https://issues.gerritcodereview.com/issues?q=componentid:1369968)
component.

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
yarn clean

// install all dependencies and start the server
npm start
```

# Gemini Project Profile: gerrit

## Project Overview

Gerrit is a web-based code review system for Git. Backend is Java 21, frontend is TypeScript/Lit components, built with Bazel 7.6.1.

## Sub-projects

- **`polygerrit-ui`**: The frontend web application. See `polygerrit-ui/GEMINI.md` for details on the frontend development environment.

## Build Commands

```bash
# Development WAR (output: bazel-bin/gerrit.war)
bazel build gerrit

# Release WAR with UI, core plugins, docs (output: bazel-bin/release.war)
bazel build release

# Build specific plugin
bazel build plugins/<name>
```

## Running Tests

### Java Tests

```bash
# All tests
bazel test --build_tests_only //...

# Specific test target
bazel test //javatests/com/google/gerrit/acceptance/rest/account:rest_account

# Single test method
bazel test --test_output=streamed \
  --test_filter=com.google.gerrit.acceptance.api.change.ChangeIT.getAmbiguous \
  //javatests/com/google/gerrit/acceptance/api/change:ChangeIT

# Tests by tag (api, git, rest, server, ssh, notedb, edit, pgm, annotation)
bazel test --test_tag_filters=api,git //...

# Exclude flaky tests
bazel test --test_tag_filters=-flaky //...

# Force re-run (ignore cache)
bazel test --cache_test_results=NO //...

# Debug tests (attach debugger to port 5005)
bazel test --java_debug //javatests/...
```

## Linting & Formatting

```bash
# Java - Google Java Format (required)
./tools/gjf.sh setup   # First time setup
./tools/gjf.sh run     # Format changed files
npm run gjf            # Alternative
```

## Local Development

```bash
# Set Java path (Java 21)
# Example for macOS ARM64 - check $(bazel info output_base)/external/ for your platform's directory
export JAVA_HOME=$(bazel info output_base)/external/rules_java~~toolchains~remotejdk21_macos_aarch64
export GERRIT_SITE=~/gerrit_testsite

# Initialize test site (first time only)
$JAVA_HOME/bin/java -jar bazel-bin/gerrit.war init --batch --dev -d $GERRIT_SITE

# Run daemon (localhost:8080)
$JAVA_HOME/bin/java \
    -DsourceRoot=$(bazel info workspace) \
    -jar bazel-bin/gerrit.war daemon -d $GERRIT_SITE --console-log

# Run daemon with dev frontend (use frontend from localhost:8081)
$JAVA_HOME/bin/java \
    -DsourceRoot=$(bazel info workspace) \
    -jar bazel-bin/gerrit.war daemon -d $GERRIT_SITE --console-log --dev-cdn http://localhost:8081
```

## Code Architecture

### Backend (java/com/google/gerrit/)

- **server/**: Core server logic, change handling, review workflows
- **httpd/**: REST API endpoints (servlet-based)
- **git/**: JGit wrapper for repository operations
- **index/**: Search backends (Lucene)
- **entities/**: Core domain models (Change, PatchSet, Account)
- **extensions/**: Plugin extension APIs
- **pgm/**: CLI programs and commands
- **acceptance/**: Test acceptance framework

### Plugins (plugins/)

Core plugins as Git submodules: replication, hooks, delete-project, gitiles, webhooks, etc.
Plugins extend via extension APIs in java/com/google/gerrit/extensions/.

## Code Style

- Java: Google Java Style Guide, use `./tools/gjf.sh run` before committing
- Commit messages: max 72 chars/line, present tense, include Change-Id (added by git hook)
- **Release-Notes footer required**: Every commit must have `Release-Notes:` footer. Use `Release-Notes: skip` for small fixes/refactorings, or add a summary for notable changes

## Key Patterns

- Dependency injection via Guice
- REST endpoints return Java objects serialized to JSON
- Changes stored in NoteDb (Git-based storage)
- Event-driven architecture for plugins

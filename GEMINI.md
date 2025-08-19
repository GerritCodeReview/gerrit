# Gemini Project Profile: gerrit

This document provides a summary of the development environment for the gerrit project.

## Overview

Gerrit is a web-based code review tool, which integrates with Git and allows developers to review, approve, and merge code changes.

- **Primary Language (Backend)**: Java
- **Primary Language (Frontend)**: TypeScript (in `polygerrit-ui`)
- **Build Tool**: Bazel
- **Package Manager**: `yarn` (for frontend dependencies)

## Sub-projects

This repository contains multiple sub-projects. For more detailed information, please refer to the `GEMINI.md` file within each sub-project directory.

- **`polygerrit-ui`**: The frontend web application. See `polygerrit-ui/GEMINI.md` for details on the frontend development environment.

## Backend (Java)

The core backend logic is written in Java and is located in the `java/` directory. The main package is `com.google.gerrit`. Key sub-packages include:

- `acceptance`: Acceptance tests
- `server`: Core server logic, including servlets, REST API endpoints, and change processing.
- `git`: Git-related operations and management.
- `sshd`: SSH server implementation for Git operations and administration.
- `index`: Indexing and search functionality.
- `auth`: Authentication and authorization logic.

## Testing (Java)

Java tests are located in the `javatests/` directory, mirroring the structure of the `java/` directory. Key sub-packages include:

- `acceptance`: Acceptance tests
- `integration`: Integration tests
- `server`: Tests for the core server logic.
- `git`: Tests for Git-related operations.

## Documentation

The `Documentation/` directory contains a wealth of information about Gerrit, including:

- **User Guide**: `user-*.txt` files explain how to use Gerrit.
- **Administrator Guide**: `config-*.txt` and `install-*.txt` files provide information for administrators.
- **Developer Guide**: `dev-*.txt` files contain information for developers working on Gerrit itself.
- **REST API**: `rest-api-*.txt` files document the REST API endpoints.
- **Commands**: `cmd-*.txt` files document the available command-line tools.

## Git

Every commit message must contain a `Release-Notes:` footer. Small changes, fixes or refactorings can just have
`Release-Notes: skip`. If the change is relevant for being called out in release notes, then append a short
summary to the `Release-Notes:` footer.

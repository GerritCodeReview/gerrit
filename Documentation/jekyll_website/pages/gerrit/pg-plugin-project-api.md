---
title: " Gerrit Code Review - Project admin customization API"
sidebar: gerritdoc_sidebar
permalink: pg-plugin-project-api.html
---
This API is provided by
[plugin.project()](pg-plugin-dev.html#plugin-project) and provides
customization to admin page.

## createCommand

`projectApi.createCommand(title, checkVisibleCallback)`

Create a project command in the admin panel.

  - **title** String title.

  - **checkVisibleCallback** function to configure command visibility.

<!-- end list -->

  - GrProjectApi for chainging.

`checkVisibleCallback(projectName, projectConfig)`

  - **projectName** String project name.

  - **projectConfig** Object REST API response for project config.

<!-- end list -->

  - `false` to hide the command for the specific project.

## onTap

`projectApi.onTap(tapCalback)`

Add a command tap callback.

  - **tapCallback** function that’s excuted on command tap.

<!-- end list -->

  - Nothing


// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.scheduler;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicMap.Entry;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;
import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

/**
 * Scheduler for scheduling recurring tasks
 */
@Singleton
public class Scheduler implements LifecycleListener {

  public static class Module extends LifecycleModule {

    @Override
    protected void configure() {
      listener().to(Scheduler.class);
    }
  }

  private static final Logger log = LoggerFactory.getLogger(Scheduler.class);

  private org.quartz.Scheduler scheduler;

  private Injector injector;
  private DynamicMap<Class<? extends Job>> jobs;
  private Config config;

  @Inject
  public Scheduler(Injector injector, DynamicMap<Class<? extends Job>> jobs,
      @GerritServerConfig Config config) {
    this.injector = injector;
    this.jobs = jobs;
    this.config = config;
  }

  @Override
  public void start() {
    try {
      scheduler = StdSchedulerFactory.getDefaultScheduler();
      scheduler.setJobFactory(new QuartzJobFactory(injector));
      scheduler.start();
      scheduleJobs();
    } catch (SchedulerException e) {
      log.error("Error occurred during scheduler initialization", e);
    }
  }

  @Override
  public void stop() {
    try {
      scheduler.shutdown();
    } catch (SchedulerException e) {
      log.error("Error occurred during scheduler shutdown", e);
    }
  }

  private void scheduleJobs() {
    Iterator<Entry<Class<? extends Job>>> it = jobs.iterator();
    while (it.hasNext()) {
      Entry<Class<? extends Job>> jobClassEntry = it.next();
      scheduleCronJob(jobClassEntry.getExportName(), jobClassEntry
          .getProvider().get());
    }
  }

  /**
   * Schedules the given job class if a cron expression is configured in
   * gerrit.config under section "job", subsection &lt;jobName&gt;, key "schedule".
   * For details about supported cron expressions see the <a
   * href="http://quartz-scheduler.org/documentation
   * /quartz-2.2.x/tutorials/crontrigger">Quartz crontrigger tutorial</a>
   *
   * <p>Example configuration to schedule job named "gc" to run each day at 4:00 am:
   * <pre>
   * [job "gc"]
   * schedule = 0 0 4 * * ?
   * </pre>
   *
   * @param jobName job name
   * @param jobClass {@code class} implementing the job to be scheduled
   */
 private void scheduleCronJob(String jobName, Class<? extends Job> jobClass) {
    JobDetail jobDetail =
        JobBuilder.newJob(jobClass).withIdentity(jobName).build();
    String schedule = config.getString("job", jobName, "schedule").trim();
    if (schedule != null && schedule != "") {
      if (CronExpression.isValidExpression(schedule)) {
        CronTrigger trigger =
            TriggerBuilder.newTrigger().withIdentity(jobName)
                .withSchedule(CronScheduleBuilder.cronSchedule(schedule))
                .build();
        try {
          scheduler.scheduleJob(jobDetail, trigger);
        } catch (SchedulerException e) {
          log.error("Scheduling job ''{0}'' for job class ''{1}'' failed",
              jobName, jobClass, e);
        }
      } else {
        log.error("Ignoring invalid cron expression ''{0}'' for job ''{1}''",
            schedule);
      }
    }
  }
}

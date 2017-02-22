/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.yarn.server.applicationhistoryservice.metrics.timeline;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.metrics2.sink.timeline.TimelineMetric;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.hadoop.yarn.server.applicationhistoryservice.metrics.timeline.TimelineMetricConfiguration.TIMELINE_METRICS_WHITELIST_FILE;
import static org.apache.hadoop.yarn.server.applicationhistoryservice.metrics.timeline.TimelineMetricConfiguration.TIMELINE_METRICS_APPS_BLACKLIST;

public class TimelineMetricsFilter {

  private static Set<String> whitelistedMetrics;
  private static Set<Pattern> whitelistedMetricPatterns;
  private static Set<String> blacklistedApps;
  private static String patternPrefix = "._p_";
  private static Set<String> amshbaseWhitelist;

  private static final Log LOG = LogFactory.getLog(TimelineMetricsFilter.class);

  public static void initializeMetricFilter(TimelineMetricConfiguration configuration) {

    Configuration metricsConf = null;
    try {
      metricsConf = configuration.getMetricsConf();
    } catch (Exception e) {
      LOG.error("Error fetching metrics configuration for getting whitelisting information");
      return;
    }

    whitelistedMetrics = new HashSet<String>();
    whitelistedMetricPatterns = new HashSet<Pattern>();
    blacklistedApps = new HashSet<>();
    amshbaseWhitelist = new HashSet<>();

    String whitelistFile = metricsConf.get(TIMELINE_METRICS_WHITELIST_FILE, "");
    if (!StringUtils.isEmpty(whitelistFile)) {
      readMetricWhitelistFromFile(whitelistFile);
    }

    String appsBlacklist = metricsConf.get(TIMELINE_METRICS_APPS_BLACKLIST, "");
    if (!StringUtils.isEmpty(appsBlacklist)) {
      for (String app : appsBlacklist.split(",")) {
        blacklistedApps.add(app);
      }
      LOG.info("Blacklisted apps : " + blacklistedApps.toString());
    }

    amshbaseWhitelist = configuration.getAmshbaseWhitelist();
    if (CollectionUtils.isNotEmpty(amshbaseWhitelist)) {
      LOG.info("Whitelisting " + amshbaseWhitelist.size() + " ams-hbase metrics");
    }
  }

  private static void readMetricWhitelistFromFile(String whitelistFile) {

    BufferedReader br = null;
    String strLine;

    try(FileInputStream fstream = new FileInputStream(whitelistFile)) {
      br = new BufferedReader(new InputStreamReader(fstream));

      while ((strLine = br.readLine()) != null)   {
        strLine = strLine.trim();
        if (StringUtils.isEmpty(strLine)) {
          continue;
        }
        if (strLine.startsWith(patternPrefix)) {
          whitelistedMetricPatterns.add(Pattern.compile(strLine.substring(patternPrefix.length())));
        } else {
          whitelistedMetrics.add(strLine);
        }
      }
    } catch (IOException ioEx) {
      LOG.error("Unable to parse metric whitelist file", ioEx);
    }

    LOG.info("Whitelisting " + whitelistedMetrics.size() + " metrics");
    LOG.debug("Whitelisted metrics : " + Arrays.toString(whitelistedMetrics.toArray()));
  }

  public static boolean acceptMetric(String metricName, String appId) {
    TimelineMetric timelineMetric = new TimelineMetric();
    timelineMetric.setAppId(appId);
    timelineMetric.setMetricName(metricName);
    return acceptMetric(timelineMetric);
  }

  public static boolean acceptMetric(TimelineMetric metric) {

    // App takes precedence.
    if (CollectionUtils.isNotEmpty(blacklistedApps) && blacklistedApps.contains(metric.getAppId())) {
      return false;
    }

    //Special Case appId = ams-hbase whitelisting.
    if ("ams-hbase".equals(metric.getAppId()) && CollectionUtils.isNotEmpty(amshbaseWhitelist)) {
      return amshbaseWhitelist.contains(metric.getMetricName());
    }

    if (CollectionUtils.isEmpty(whitelistedMetrics) && CollectionUtils.isEmpty(whitelistedMetricPatterns)) {
      return true;
    }

    if (whitelistedMetrics.contains(metric.getMetricName())) {
      return true;
    }

    for (Pattern p : whitelistedMetricPatterns) {
      Matcher m = p.matcher(metric.getMetricName());
      if (m.find()) {
        whitelistedMetrics.add(metric.getMetricName());
        return true;
      }
    }

    return false;
  }

  public static void addToWhitelist(String metricName) {

    if (StringUtils.isNotEmpty(metricName)) {
      whitelistedMetrics.add(metricName);
    }
  }

}

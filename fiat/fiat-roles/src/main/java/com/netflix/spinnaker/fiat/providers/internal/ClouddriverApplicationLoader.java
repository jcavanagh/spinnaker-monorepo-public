/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.fiat.providers.internal;

import com.netflix.spinnaker.fiat.config.ResourceProviderConfig.ApplicationProviderConfig;
import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.providers.ProviderHealthTracker;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
public class ClouddriverApplicationLoader extends ClouddriverDataLoader<Application> {
  ApplicationProviderConfig applicationProviderConfig;

  public ClouddriverApplicationLoader(
      ProviderHealthTracker healthTracker,
      ClouddriverApi clouddriverApi,
      ApplicationProviderConfig applicationProviderConfig) {
    super(healthTracker, () -> Retrofit2SyncCall.execute(clouddriverApi.getApplications()));
    this.applicationProviderConfig = applicationProviderConfig;
  }

  @Override
  @Scheduled(
      fixedDelayString =
          "${resource.provider.application.clouddriver.cacheRefreshIntervalMs:30000}")
  protected void refreshCache() {
    super.refreshCache();
  }
}

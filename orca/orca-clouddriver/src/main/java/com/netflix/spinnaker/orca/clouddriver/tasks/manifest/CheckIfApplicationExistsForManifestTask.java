/*
 * Copyright 2022 Salesforce.com, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.config.TaskConfigurationProperties;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCheckIfApplicationExistsTask;
import com.netflix.spinnaker.orca.clouddriver.utils.MonikerHelper;
import com.netflix.spinnaker.orca.front50.Front50Service;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * This checks if the application name provided for any server group related tasks actually exists
 * in front50 and/or clouddriver
 */
@Slf4j
@Component
public class CheckIfApplicationExistsForManifestTask extends AbstractCheckIfApplicationExistsTask {

  public CheckIfApplicationExistsForManifestTask(
      @Nullable Front50Service front50Service,
      OortService oortService,
      ObjectMapper objectMapper,
      RetrySupport retrySupport,
      TaskConfigurationProperties configurationProperties) {
    super(front50Service, oortService, objectMapper, retrySupport, configurationProperties);
  }

  /**
   * get the application name from the provided stage context.
   *
   * @param stage the stage execution context
   * @return the application name
   */
  @Override
  public String getApplicationName(@Nonnull StageExecution stage) {
    String applicationName = (String) stage.getContext().get("application");
    if (applicationName == null) {
      Moniker moniker = MonikerHelper.monikerFromStage(stage);
      if (moniker != null && moniker.getApp() != null) {
        applicationName = moniker.getApp();
      }
    }
    return applicationName;
  }
}
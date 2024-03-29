/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.appengine

import com.netflix.frigga.Names
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.clouddriver.model.Cluster
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware

import com.netflix.spinnaker.orca.retrofit.exceptions.SpinnakerServerExceptionHandler
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
abstract class AbstractWaitForAppEngineServerGroupStopStartTask implements CloudProviderAware, RetryableTask {
  long backoffPeriod = 10000
  long timeout = 1800000

  @Autowired
  CloudDriverService cloudDriverService

  abstract boolean isStart()

  @Override
  TaskResult execute(StageExecution stage) {
    String cloudProvider = getCloudProvider(stage)
    String account = getCredentials(stage)
    String serverGroupName = (stage.context.serverGroupName ?: stage.context.asgName) as String
    Names names = Names.parseName(serverGroupName)
    String appName = stage.context.moniker?.app ?: names.app
    String clusterName = stage.context.moniker?.cluster ?: names.cluster
    try {
      Cluster cluster = cloudDriverService.getCluster(appName, account, clusterName, cloudProvider)

      ServerGroup serverGroup = cluster.serverGroups.find { it.getName() == serverGroupName }
      if (!serverGroup) {
        log.info("${serverGroupName}: not found.")
        return TaskResult.ofStatus(ExecutionStatus.TERMINAL)
      }

      def desiredServingStatus = start ? "SERVING" : "STOPPED"
      if (serverGroup.servingStatus == desiredServingStatus) {
        return TaskResult.ofStatus(ExecutionStatus.SUCCEEDED)
      } else {
        log.info("${serverGroupName}: not yet ${start ? "started" : "stopped"}.")
        return TaskResult.ofStatus(ExecutionStatus.RUNNING)
      }
    } catch (SpinnakerHttpException e) {
      def spinnakerHttpExceptionResponse = new SpinnakerServerExceptionHandler().handle(stage.name, e)
      if (e.getResponseCode() == 404) {
        return TaskResult.ofStatus(ExecutionStatus.TERMINAL)
      } else if (e.getResponseCode() >= 500) {
        log.error("Unexpected http error (${spinnakerHttpExceptionResponse})")
        return TaskResult.builder(ExecutionStatus.RUNNING).context([lastSpinnakerException: spinnakerHttpExceptionResponse]).build()
      }

      throw e
    }
  }
}

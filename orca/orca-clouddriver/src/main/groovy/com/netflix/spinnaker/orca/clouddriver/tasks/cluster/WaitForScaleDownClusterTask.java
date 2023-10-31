/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.cluster;

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class WaitForScaleDownClusterTask extends AbstractWaitForClusterWideClouddriverTask {
  @Override
  public boolean isServerGroupOperationInProgress(
      StageExecution stage,
      List<Map> interestingHealthProviderNames,
      Optional<ServerGroup> currentServerGroup) {
    return !currentServerGroup.map(ServerGroup::getInstances).map(List::isEmpty).orElse(true);
  }
}

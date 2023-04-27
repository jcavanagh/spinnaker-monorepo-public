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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import org.springframework.stereotype.Component

@Component
class NoStrategy implements Strategy {

  final String name = "none"

  @Override
  List<StageExecution> composeBeforeStages(StageExecution parent) {
    return []
  }

  @Override
  List<StageExecution> composeAfterStages(StageExecution parent) {
    return []
  }
}

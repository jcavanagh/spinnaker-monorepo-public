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
package com.netflix.spinnaker.kork.plugins.update

import java.util.function.Supplier

/**
 * Contract to resolve the server group name of the running service.
 *
 * If the running service is clouddriver, the resulting value may be `clouddriver-main-v123`. Null will be returned
 * if no server group name can be resolved.
 */
interface ServerGroupNameResolver : Supplier<String?>

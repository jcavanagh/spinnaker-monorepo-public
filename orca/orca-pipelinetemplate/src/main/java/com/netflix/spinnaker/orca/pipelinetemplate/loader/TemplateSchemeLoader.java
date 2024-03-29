/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipelinetemplate.loader;

import java.net.URI;
import java.util.Map;

public interface TemplateSchemeLoader {
  boolean supports(URI uri);

  Map<String, Object> load(URI uri);

  default boolean isJson(URI uri) {
    String uriName = uri.toString().toLowerCase();
    return uriName.endsWith(".json");
  }

  default boolean isYaml(URI uri) {
    String uriName = uri.toString().toLowerCase();
    return uriName.endsWith(".yaml") || uriName.endsWith(".yml");
  }
}

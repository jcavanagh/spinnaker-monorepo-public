/*
 * Copyright 2024 Salesforce, Inc.
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

CREATE USER orca_service with PASSWORD '0rcaPassw0rd';
CREATE USER orca_migrate with PASSWORD '0rcaPassw0rd';

grant create on schema public to orca_service;
grant create on schema public to orca_migrate;

GRANT pg_read_all_data TO orca_service;
GRANT pg_write_all_data TO orca_service;

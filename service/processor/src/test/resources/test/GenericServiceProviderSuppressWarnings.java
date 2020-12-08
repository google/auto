/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package test;

import com.google.auto.service.AutoService;

/**
 * An implementation of a service with a type parameter, which will not produce a warning even if
 * compiled with {@code -Averify=true}, because of the {@code @SuppressWarnings}.
 */
@AutoService(GenericService.class)
@SuppressWarnings("rawtypes")
public class GenericServiceProviderSuppressWarnings<T> implements GenericService<T> {}

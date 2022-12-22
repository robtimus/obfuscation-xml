/*
 * module-info.java
 * Copyright 2020 Rob Spoor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

module com.github.robtimus.obfuscation.xml {
    requires transitive com.github.robtimus.obfuscation;
    requires transitive java.xml;
    requires com.ctc.wstx;
    requires org.slf4j;

    exports com.github.robtimus.obfuscation.xml;
}

/*
 * Copyright 2026 Alejandro Hernández <https://github.com/alejandrohdezma>
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

package com.alejandrohdezma.sbt.changesets

class VersionBumpSuite extends munit.FunSuite {

  // --- VersionBump.apply (version bumping) ---

  test("VersionBump.apply - patch bumps the patch component") {
    assertEquals(VersionBump.Patch("1.2.3"), "1.2.4")
  }

  test("VersionBump.apply - minor bumps minor and resets patch") {
    assertEquals(VersionBump.Minor("1.2.3"), "1.3.0")
  }

  test("VersionBump.apply - major bumps major and resets minor and patch") {
    assertEquals(VersionBump.Major("1.2.3"), "2.0.0")
  }

  test("VersionBump.apply - patch from zero version") {
    assertEquals(VersionBump.Patch("0.1.0"), "0.1.1")
  }

  test("VersionBump.apply - minor from zero version") {
    assertEquals(VersionBump.Minor("0.1.0"), "0.2.0")
  }

  test("VersionBump.apply - major from zero version") {
    assertEquals(VersionBump.Major("0.1.0"), "1.0.0")
  }

  // --- VersionBump.cascadeBump ---

  test("VersionBump.cascadeBump - 0.x dep + patch bump → Patch") {
    assertEquals(VersionBump.Patch.cascadeBump("0.3.0", "0.1.0"), VersionBump.Patch)
    assertEquals(VersionBump.Patch.cascadeBump("0.3.0", "1.0.0"), VersionBump.Patch)
  }

  test("VersionBump.cascadeBump - 0.x dep + minor bump → Minor for 0.x dependent") {
    assertEquals(VersionBump.Minor.cascadeBump("0.3.0", "0.1.0"), VersionBump.Minor)
  }

  test("VersionBump.cascadeBump - 0.x dep + minor bump → Major for 1.x+ dependent") {
    assertEquals(VersionBump.Minor.cascadeBump("0.3.0", "1.0.0"), VersionBump.Major)
  }

  test("VersionBump.cascadeBump - 0.x dep + major bump → Minor for 0.x dependent") {
    assertEquals(VersionBump.Major.cascadeBump("0.3.0", "0.1.0"), VersionBump.Minor)
  }

  test("VersionBump.cascadeBump - 0.x dep + major bump → Major for 1.x+ dependent") {
    assertEquals(VersionBump.Major.cascadeBump("0.3.0", "1.0.0"), VersionBump.Major)
  }

  test("VersionBump.cascadeBump - 1.x dep + patch bump → Patch") {
    assertEquals(VersionBump.Patch.cascadeBump("1.0.0", "0.1.0"), VersionBump.Patch)
    assertEquals(VersionBump.Patch.cascadeBump("1.0.0", "1.0.0"), VersionBump.Patch)
  }

  test("VersionBump.cascadeBump - 1.x dep + minor bump → Patch (non-breaking)") {
    assertEquals(VersionBump.Minor.cascadeBump("1.0.0", "0.1.0"), VersionBump.Patch)
    assertEquals(VersionBump.Minor.cascadeBump("1.0.0", "1.0.0"), VersionBump.Patch)
  }

  test("VersionBump.cascadeBump - 1.x dep + major bump → Minor for 0.x dependent") {
    assertEquals(VersionBump.Major.cascadeBump("1.0.0", "0.1.0"), VersionBump.Minor)
  }

  test("VersionBump.cascadeBump - 1.x dep + major bump → Major for 1.x+ dependent") {
    assertEquals(VersionBump.Major.cascadeBump("1.0.0", "1.0.0"), VersionBump.Major)
  }

  // --- VersionBump.max ---

  test("VersionBump.max - same returns same") {
    assertEquals(VersionBump.Patch.max(VersionBump.Patch), VersionBump.Patch)
    assertEquals(VersionBump.Minor.max(VersionBump.Minor), VersionBump.Minor)
    assertEquals(VersionBump.Major.max(VersionBump.Major), VersionBump.Major)
  }

  test("VersionBump.max - Patch vs Minor") {
    assertEquals(VersionBump.Patch.max(VersionBump.Minor), VersionBump.Minor)
    assertEquals(VersionBump.Minor.max(VersionBump.Patch), VersionBump.Minor)
  }

  test("VersionBump.max - Patch vs Major") {
    assertEquals(VersionBump.Patch.max(VersionBump.Major), VersionBump.Major)
    assertEquals(VersionBump.Major.max(VersionBump.Patch), VersionBump.Major)
  }

  test("VersionBump.max - Minor vs Major") {
    assertEquals(VersionBump.Minor.max(VersionBump.Major), VersionBump.Major)
    assertEquals(VersionBump.Major.max(VersionBump.Minor), VersionBump.Major)
  }

  // --- VersionBump.unapply ---

  test("VersionBump.unapply - major") {
    assertEquals(VersionBump.unapply("major"), Some(VersionBump.Major))
  }

  test("VersionBump.unapply - minor") {
    assertEquals(VersionBump.unapply("minor"), Some(VersionBump.Minor))
  }

  test("VersionBump.unapply - patch") {
    assertEquals(VersionBump.unapply("patch"), Some(VersionBump.Patch))
  }

  test("VersionBump.unapply - unknown string returns None") {
    assertEquals(VersionBump.unapply("unknown"), None)
    assertEquals(VersionBump.unapply("Major"), None)
    assertEquals(VersionBump.unapply(""), None)
  }

}

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

import java.io.File
import java.nio.file.Files

import com.alejandrohdezma.sbt.modules.ModuleMetadata

class ChangesetsSuite extends munit.FunSuite {

  // --- Changesets.parseFrom ---

  test("Changesets.parseFrom - non-existent directory returns Left") {
    val dir = new File("/tmp/non-existent-changeset-dir-" + System.nanoTime())

    assertEquals(Changesets.parseFrom(dir), Left(List(s"Changeset directory does not exist: ${dir.getAbsolutePath}")))
  }

  withChangesetDir().test("Changesets.parseFrom - file instead of directory returns Left") { dir =>
    val file = new File(dir, "afile.txt")
    sbt.IO.write(file, "content")

    assertEquals(Changesets.parseFrom(file), Left(List(s"Not a directory: ${file.getAbsolutePath}")))
  }

  withChangesetDir().test("Changesets.parseFrom - empty directory returns Right with empty map") { dir =>
    assertEquals(Changesets.parseFrom(dir), Right(Changesets(Map.empty)))
  }

  withChangesetDir {
    "change1.md" ->
      """---
        |"my-module": patch
        |---
        |
        |Fixed a bug.
        |""".stripMargin
  }.test("Changesets.parseFrom - single changeset file with one entry") { dir =>
    val expected = Right(Changesets(Map("my-module" -> Changesets.Entry(VersionBump.Patch, "Fixed a bug."))))

    assertEquals(Changesets.parseFrom(dir), expected)
  }

  withChangesetDir {
    "change1.md" ->
      """---
        |"module-a": minor
        |"module-b": patch
        |---
        |
        |Shared description.
        |""".stripMargin
  }.test("Changesets.parseFrom - single changeset file with multiple modules shares description") { dir =>
    val expected = Right(
      Changesets(
        Map(
          "module-a" -> Changesets.Entry(VersionBump.Minor, "Shared description."),
          "module-b" -> Changesets.Entry(VersionBump.Patch, "Shared description.")
        )
      )
    )

    assertEquals(Changesets.parseFrom(dir), expected)
  }

  withChangesetDir {
    "bad.md" ->
      """---
        |not valid at all
        |---
        |
        |Description.
        |""".stripMargin
  }.test("Changesets.parseFrom - malformed frontmatter line returns Left with error") { dir =>
    val expected = Left(List("Malformed frontmatter line in `bad.md`: 'not valid at all'"))

    assertEquals(Changesets.parseFrom(dir), expected)
  }

  withChangesetDir(
    "bad1.md" ->
      """---
        |invalid line one
        |---
        |""".stripMargin,
    "bad2.md" ->
      """---
        |another bad line
        |---
        |""".stripMargin
  ).test("Changesets.parseFrom - multiple errors accumulated across files") { dir =>
    val expected = Set(
      "Malformed frontmatter line in `bad1.md`: 'invalid line one'",
      "Malformed frontmatter line in `bad2.md`: 'another bad line'"
    )

    assertEquals(Changesets.parseFrom(dir).left.map(_.toSet), Left(expected))
  }

  withChangesetDir {
    "README.md" ->
      """---
        |"should-not-parse": major
        |---
        |
        |This should be ignored.
        |""".stripMargin
  }.test("Changesets.parseFrom - README.md is ignored") { dir =>
    assertEquals(Changesets.parseFrom(dir), Right(Changesets(Map.empty)))
  }

  // --- Changesets.validate ---

  test("Changesets.validate - all modules known returns Right") {
    val changesets = Changesets(
      Map(
        "module-a" -> Changesets.Entry(VersionBump.Patch, "fix"),
        "module-b" -> Changesets.Entry(VersionBump.Minor, "feat")
      )
    )

    assertEquals(changesets.validate(Set("module-a", "module-b", "module-c")), Right(changesets))
  }

  test("Changesets.validate - unknown modules returns Left with sorted errors") {
    val changesets = Changesets(
      Map(
        "unknown-z" -> Changesets.Entry(VersionBump.Patch, "fix"),
        "unknown-a" -> Changesets.Entry(VersionBump.Minor, "feat")
      )
    )

    val expected = Left(
      List(
        "Unknown module in changeset: 'unknown-a'",
        "Unknown module in changeset: 'unknown-z'"
      )
    )

    assertEquals(changesets.validate(Set("module-a")), expected)
  }

  test("Changesets.validate - empty changesets always valid") {
    assertEquals(Changesets(Map.empty).validate(Set("module-a")), Right(Changesets(Map.empty)))
    assertEquals(Changesets(Map.empty).validate(Set.empty), Right(Changesets(Map.empty)))
  }

  // --- Changesets.extractChangelogEntry ---

  withChangesetDir().test("Changesets.extractChangelogEntry - non-existent file returns empty string") { dir =>
    assertEquals(Changesets.extractChangelogEntry(new File(dir, "CHANGELOG.md"), "1.0.0"), "")
  }

  withChangesetDir {
    "CHANGELOG.md" ->
      """|## 1.1.0
         |
         |Added new feature.
         |
         |## 1.0.0
         |
         |Initial release.
         |""".stripMargin
  }.test("Changesets.extractChangelogEntry - extracts content between version headers") { dir =>
    assertEquals(Changesets.extractChangelogEntry(new File(dir, "CHANGELOG.md"), "1.1.0"), "Added new feature.")
  }

  withChangesetDir {
    "CHANGELOG.md" ->
      """|## 1.1.0
         |
         |Added new feature.
         |
         |## 1.0.0
         |
         |Initial release.
         |""".stripMargin
  }.test("Changesets.extractChangelogEntry - version not found returns empty string") { dir =>
    assertEquals(Changesets.extractChangelogEntry(new File(dir, "CHANGELOG.md"), "2.0.0"), "")
  }

  withChangesetDir {
    "CHANGELOG.md" ->
      """|## 1.0.0
         |
         |Initial release.
         |
         |Some extra content.
         |""".stripMargin
  }.test("Changesets.extractChangelogEntry - last entry returns all remaining content") { dir =>
    assertEquals(
      Changesets.extractChangelogEntry(new File(dir, "CHANGELOG.md"), "1.0.0"),
      "Initial release.\n\nSome extra content."
    )
  }

  // --- Changesets.cascadeExpand ---

  private def module(
      version: String,
      dependencies: Set[String] = Set.empty,
      dependents: Set[String] = Set.empty
  ): ModuleMetadata =
    ModuleMetadata(
      version = version, dependencies = dependencies, transitiveDependencies = Set.empty, dependents = dependents,
      transitiveDependents = Set.empty
    )

  test("Changesets.cascadeExpand - no dependents returns unchanged") {
    val modules    = Map("A" -> module("1.0.0"))
    val changesets = Changesets(Map("A" -> Changesets.Entry(VersionBump.Patch, "fix")))

    assertEquals(changesets.cascadeExpand(modules), changesets)
  }

  test("Changesets.cascadeExpand - linear chain A→B→C cascades") {
    val modules = Map(
      "A" -> module("1.0.0", dependents = Set("B")),
      "B" -> module("1.0.0", dependencies = Set("A"), dependents = Set("C")),
      "C" -> module("1.0.0", dependencies = Set("B"))
    )

    val changesets = Changesets(Map("A" -> Changesets.Entry(VersionBump.Patch, "fix A")))
    val expanded   = changesets.cascadeExpand(modules)
    val expected   = Changesets(
      Map(
        "A" -> Changesets.Entry(VersionBump.Patch, "fix A"),
        "B" -> expanded.get("B").get,
        "C" -> expanded.get("C").get
      )
    )

    assertEquals(expanded, expected)
    assertEquals(expanded.get("B").map(_.bump), Some(VersionBump.Patch))
    assertEquals(expanded.get("C").map(_.bump), Some(VersionBump.Patch))
  }

  test("Changesets.cascadeExpand - breaking cascade: 0.x dep minor bump → Minor for 0.x dependent, Major for 1.x+") {
    val modules = Map(
      "dep"      -> module("0.3.0", dependents = Set("zero-dep", "one-dep")),
      "zero-dep" -> module("0.1.0", dependencies = Set("dep")),
      "one-dep"  -> module("1.0.0", dependencies = Set("dep"))
    )

    val changesets = Changesets(Map("dep" -> Changesets.Entry(VersionBump.Minor, "breaking change")))
    val expanded   = changesets.cascadeExpand(modules)

    assertEquals(expanded.get("zero-dep").map(_.bump), Some(VersionBump.Minor))
    assertEquals(expanded.get("one-dep").map(_.bump), Some(VersionBump.Major))
  }

  test("Changesets.cascadeExpand - non-breaking cascade: 1.x dep patch bump → Patch") {
    val modules = Map(
      "dep"       -> module("1.0.0", dependents = Set("dependent")),
      "dependent" -> module("1.0.0", dependencies = Set("dep"))
    )

    val changesets = Changesets(Map("dep" -> Changesets.Entry(VersionBump.Patch, "fix")))
    val expanded   = changesets.cascadeExpand(modules)

    assertEquals(expanded.get("dependent").map(_.bump), Some(VersionBump.Patch))
  }

  test("Changesets.cascadeExpand - multiple sources: shared dependent gets max bump") {
    val modules = Map(
      "A" -> module("1.0.0", dependents = Set("C")),
      "B" -> module("1.0.0", dependents = Set("C")),
      "C" -> module("1.0.0", dependencies = Set("A", "B"))
    )

    val changesets = Changesets(
      Map(
        "A" -> Changesets.Entry(VersionBump.Patch, "fix A"),
        "B" -> Changesets.Entry(VersionBump.Major, "break B")
      )
    )
    val expanded = changesets.cascadeExpand(modules)

    assertEquals(expanded.get("C").map(_.bump), Some(VersionBump.Major))
  }

  test("Changesets.cascadeExpand - explicit changeset on dependent preserves description") {
    val modules = Map(
      "A" -> module("1.0.0", dependents = Set("B")),
      "B" -> module("1.0.0", dependencies = Set("A"))
    )

    val changesets = Changesets(
      Map(
        "A" -> Changesets.Entry(VersionBump.Patch, "fix A"),
        "B" -> Changesets.Entry(VersionBump.Minor, "explicit B change")
      )
    )
    val expanded = changesets.cascadeExpand(modules)

    assertEquals(expanded.get("B"), Some(Changesets.Entry(VersionBump.Minor, "explicit B change")))
  }

  test("Changesets.cascadeExpand - cascade descriptions contain updated dependency info") {
    val modules = Map(
      "A" -> module("1.0.0", dependents = Set("B")),
      "B" -> module("1.0.0", dependencies = Set("A"))
    )

    val changesets = Changesets(Map("A" -> Changesets.Entry(VersionBump.Patch, "fix A")))
    val expanded   = changesets.cascadeExpand(modules)
    val expected   = Changesets.Entry(VersionBump.Patch, "- Updated dependency: A (1.0.0 → 1.0.1)")

    assertEquals(expanded.get("B"), Some(expected))
  }

  // --- Changesets.validateDescriptions ---

  test("Changesets.validateDescriptions - no template descriptions returns Right") {
    val changesets = Changesets(
      Map(
        "module-a" -> Changesets.Entry(VersionBump.Patch, "Fixed a real bug."),
        "module-b" -> Changesets.Entry(VersionBump.Minor, "Added a feature.")
      )
    )

    assertEquals(changesets.validateDescriptions, Right(changesets))
  }

  test("Changesets.validateDescriptions - template descriptions returns Left with sorted errors") {
    val changesets = Changesets(
      Map(
        "module-z" -> Changesets.Entry(VersionBump.Patch, Changesets.TemplateDescription),
        "module-a" -> Changesets.Entry(VersionBump.Minor, Changesets.TemplateDescription),
        "module-m" -> Changesets.Entry(VersionBump.Patch, "Real description.")
      )
    )

    val expected = Left(
      List(
        "Changeset for 'module-a' still has the template description. Please update it.",
        "Changeset for 'module-z' still has the template description. Please update it."
      )
    )

    assertEquals(changesets.validateDescriptions, expected)
  }

  test("Changesets.validateDescriptions - empty changesets returns Right") {
    assertEquals(Changesets(Map.empty).validateDescriptions, Right(Changesets(Map.empty)))
  }

  withChangesetDir {
    "fix-auth.md" ->
      s"""---
         |"my-module": patch
         |---
         |
         |${Changesets.TemplateDescription}
         |""".stripMargin
  }.test("Changesets.parseFrom - parses file with template description") { dir =>
    val expected =
      Right(Changesets(Map("my-module" -> Changesets.Entry(VersionBump.Patch, Changesets.TemplateDescription))))

    assertEquals(Changesets.parseFrom(dir), expected)
  }

  // --- Changesets.clean ---

  withChangesetDir(
    "change1.md" -> "content1",
    "change2.md" -> "content2",
    "README.md"  -> "readme content"
  ).test("Changesets.clean - removes .md files but not README.md") { dir =>
    Changesets.clean(dir)

    val remaining = dir.listFiles().map(_.getName).toSet
    assertEquals(remaining, Set("README.md"))
  }

  // --- Fixture helper for temp directories with changeset files ---

  private def withChangesetDir(files: (String, String)*): FunFixture[File] =
    FunFixture[File](
      setup = { _ =>
        val dir = Files.createTempDirectory("changesets-test").toFile
        files.foreach { case (name, content) =>
          val f = new File(dir, name)
          sbt.IO.write(f, content)
        }
        dir
      },
      teardown = { dir =>
        sbt.IO.delete(dir)
      }
    )

}

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

object Colors {

  def module(s: String): String = s"${Console.CYAN}$s${Console.RESET}"

  def version(s: String): String = s"${Console.GREEN}$s${Console.RESET}"

  def bump(s: String): String = s"${Console.YELLOW}$s${Console.RESET}"

  def path(f: java.io.File): String = s"${Console.BOLD}$f${Console.RESET}"

  def error(s: String): String = s"${Console.RED}$s${Console.RESET}"

}

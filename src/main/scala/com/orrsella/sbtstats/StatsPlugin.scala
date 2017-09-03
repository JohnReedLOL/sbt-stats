/**
 * Copyright (c) 2013 Orr Sella
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

package com.orrsella.sbtstats

import java.io.File
import sbt._
import sbt.Keys._

object StatsPlugin extends AutoPlugin {

  object autoImport {
    lazy val statsAnalyzers = settingKey[Seq[Analyzer]]("stats-analyzers")
    lazy val statsProject = taskKey[Unit]("Prints code statistics for a single project, the current one")
    lazy val statsProjectNoPrint = taskKey[Seq[AnalyzerResult]]("Returns code statistics for a project, without printing it (shouldn't be used directly)")
    lazy val statsEncoding = settingKey[String]("stats-encoding")
  }

  import autoImport._

  lazy val statsSettings = Seq(
    commands += statsCommand,
    statsAnalyzers := Seq(new FilesAnalyzer(), new LinesAnalyzer(), new CharsAnalyzer()),
    statsProject := statsProjectTask(statsProjectNoPrint.value, name.value, state.value.log),
    statsProjectNoPrint := statsProjectNoPrintTask(statsAnalyzers.value, (sources in Compile).value, (packageBin in Compile).value, statsEncoding.value, state.value.log),
    statsEncoding := "UTF-8",
    aggregate in statsProject := false,
    aggregate in statsProjectNoPrint := false
  )

  override def trigger = allRequirements
  override lazy val projectSettings = statsSettings ++ Seq(Keys.commands ++= Seq(statsCommand))

  lazy val statsCommand = Command.command("stats") { state => doCommand(state) }

  private def doCommand(state: State): State = {
    val log = state.log
    val extracted: Extracted = Project.extract(state)
    val structure = extracted.structure
    val projectRefs = structure.allProjectRefs

    val results: Seq[AnalyzerResult] = projectRefs.flatMap {
      projectRef => EvaluateTask(structure, statsProjectNoPrint, state, projectRef) match {
        case Some((state, Value(seq))) => seq
        case _ => Seq()
      }
    }

    val distinctTitles = results.map(_.title).distinct
    val summedResults = distinctTitles.map(t => results.filter(r => r.title == t).reduceLeft(_ + _))

    log.info("")
    log.info("Code Statistics for project:")
    log.info("")

    summedResults.foreach(res => {
      log.info(res.toString)
      log.info("")
    })

    // return unchanged state
    state
  }

  private def statsProjectTask(results: Seq[AnalyzerResult], name: String, log: Logger) {
    log.info("")
    log.info("Code Statistics for project '" + name + "':")
    log.info("")

    results.foreach(res => {
      log.info(res.toString)
      log.info("")
    })
  }

  private def statsProjectNoPrintTask(analyzers: Seq[Analyzer], sources: Seq[File], packageBin: File, encoding: String, log: Logger) = {
    for (a <- analyzers) yield a.analyze(sources, packageBin, encoding)
  }
}

package com.atomist.project;

import com.atomist.project.edit.ProjectEditor
import com.atomist.project.generate.ProjectGenerator
import com.atomist.source.{ArtifactSource, StringFileArtifact}
import org.springframework.stereotype.Component

@Component
class ProvenanceInfoWriter {

  val PROVENANCE_FILE: String = ".provenance.txt"

  def write(projectSource: ArtifactSource, po: ProjectOperation, poa: ProjectOperationArguments): ArtifactSource = {
  
    val content = write(po, poa)
    
    val provenanceFile = projectSource.findFile(PROVENANCE_FILE)
    val updatedContent = provenanceFile match {
      case Some(existingContent) => existingContent.content + content
      case None => content
    }

    projectSource.+(StringFileArtifact.apply(PROVENANCE_FILE, updatedContent))
  }
  
  def write(po: ProjectOperation, poa: ProjectOperationArguments): String = {
    val content: StringBuilder = new StringBuilder()

    po match {
      case g: ProjectGenerator =>
        content.append("# generator info\n")
      case e: ProjectEditor =>
        content.append("####\n\n")
        content.append("# editor info\n")
      case _ =>
    }

    po match {
      case p: ProvenanceInfo =>
        content.append(s"name: ${p.name()}\n")
        content.append(s"group: ${p.group.getOrElse("n/a")}\n")
        content.append(s"artifact: ${p.artifact.getOrElse("n/a")}\n")
        content.append(s"version: ${p.version.getOrElse("n/a")}\n")

        content.append("\n# backing git repo\n")
        content.append(s"repo: ${p.repo().getOrElse("n/a")}\n")
        content.append(s"branch: ${p.branch().getOrElse("n/a")}\n")
        content.append(s"sha: ${p.sha().getOrElse("n/a")}\n")
      case _ =>
    }

    content.append("\n# parameter values\n")
    poa.parameterValues.foreach(p => content.append(String.format("%s: %s\n", p.getName, p.getValue)))
    content.append("\n")
    content.toString()
  }

}

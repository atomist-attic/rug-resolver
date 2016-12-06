package com.atomist.project;

import com.atomist.project.edit.ProjectEditor
import com.atomist.project.generate.ProjectGenerator
import com.atomist.source.{ArtifactSource, StringFileArtifact}
import org.springframework.stereotype.Component
import org.apache.commons.lang3.text.WordUtils
import org.apache.commons.lang3.StringUtils

@Component
class ProvenanceInfoWriter {

  val ProvenanceFile: String = ".provenance.yml"
  val SecretKeys: Seq[String] = Seq("password", "key", "secret", "token", "user")

  def write(projectSource: ArtifactSource, po: ProjectOperation, poa: ProjectOperationArguments): ArtifactSource = {
  
    val content = write(po, poa)
    
    val provenanceFile = projectSource.findFile(ProvenanceFile)
    val updatedContent = provenanceFile match {
      case Some(existingContent) => existingContent.content + content
      case None => content
    }

    projectSource.+(StringFileArtifact.apply(ProvenanceFile, updatedContent))
  }
  
  def write(po: ProjectOperation, poa: ProjectOperationArguments): String = {
    val content = new StringBuilder()
    
    content.append("---\n");
    
    po match {
      case g: ProjectGenerator =>
        content.append("generator:\n")
      case e: ProjectEditor =>
        content.append("editor:\n")
      case _ =>
    }

    po match {
      case p: ProvenanceInfo =>
        content.append(s"""  name: "${p.name()}"\n""")
        content.append(s"""  group: "${p.group.getOrElse("n/a")}"\n""")
        content.append(s"""  artifact: "${p.artifact.getOrElse("n/a")}"\n""")
        content.append(s"""  version: "${p.version.getOrElse("n/a")}"\n""")

        content.append(s"""  origin:\n""")
        content.append(s"""    repo: "${p.repo().getOrElse("n/a")}"\n""")
        content.append(s"""    branch: "${p.branch().getOrElse("n/a")}"\n""")
        content.append(s"""    sha: "${p.sha().getOrElse("n/a")}"\n""")
      case _ =>
    }
    
    def sanitizeValue(key: String, value: Any): Any = {
      value match {
        case s: String => 
          if (SecretKeys.filter { sec => key.toLowerCase().contains(sec.toLowerCase()) }.isEmpty) {
            s
          }
          else {
            s.charAt(0) + ("*" * (s.length() - 2)) + s.last 
          }
        case _ => value
      }
      
    }

    content.append(s"""  parameters:\n""")
    poa.parameterValues.foreach(p => content.append(s"""    - "${p.getName}": "${sanitizeValue(p.getName, p.getValue)}"\n"""))
    content.append("\n")
    content.toString()
  }

}

package com.atomist.rug.resolver.project

import com.atomist.param.ParameterValues
import com.atomist.project.edit.ProjectEditor
import com.atomist.project.generate.ProjectGenerator
import com.atomist.project.ProjectOperation
import com.atomist.source.{ ArtifactSource, StringFileArtifact }
import org.springframework.stereotype.Component

@Component
class ProvenanceInfoWriter {

  val ProvenanceFile: String = ".atomist.yml"
  val SecretKeys: Seq[String] = Seq("password", "key", "secret", "token", "user")

  def write(projectSource: ArtifactSource, po: ProjectOperation, poa: ParameterValues, client: String): ArtifactSource = {

    val content = write(po, poa, client)
    
    // Make sure to delete the .atomist.yml in case of a generator before creating the new file
    // because we are not interested in the history of the rug archive to show up in the generated project
    val source = po match {
      case g: ProjectGenerator =>
        projectSource.delete(ProvenanceFile)
      case _ => projectSource
    }
    
    val provenanceFile = source.findFile(ProvenanceFile)
    val updatedContent = provenanceFile match {
      case Some(existingContent) => existingContent.content + content
      case None => content
    }

    source.+(StringFileArtifact.apply(ProvenanceFile, updatedContent))
  }

  // how do we add that a Handler initiated the editor/generator?
  def write(po: ProjectOperation, poa: ParameterValues, client: String): String = {
    val content = new StringBuilder()

    content.append("---\n");
    content.append(s"""kind: "operation"\n""");
    content.append(s"""client: "$client"\n""");

    po match {
      case g: ProjectGenerator =>
        content.append("generator:\n")
      case e: ProjectEditor =>
        content.append("editor:\n")
      case _ =>
    }

    po match {
      case p: ProvenanceInfo =>
        content.append(s"""  name: "${p.name}"\n""")
        content.append(s"""  group: "${extract(p.group)}"\n""")
        content.append(s"""  artifact: "${extract(p.artifact)}"\n""")
        content.append(s"""  version: "${extract(p.version)}"\n""")

        content.append(s"""  origin:\n""")
        content.append(s"""    repo: "${extract(p.repo())}"\n""")
        content.append(s"""    branch: "${extract(p.branch())}"\n""")
        content.append(s"""    sha: "${extract(p.sha())}"\n""")
      case _ =>
    }

    def sanitizeValue(key: String, value: Any): Any = {
      value match {
        case s: String =>
          if (!SecretKeys.exists { sec => key.toLowerCase().contains(sec.toLowerCase()) }) {
            s
          } else {
            s.charAt(0) + ("*" * (s.length() - 2)) + s.last
          }
        case _ => value
      }

    }

    if (poa.parameterValues.nonEmpty) {
      content.append(s"""  parameters:\n""")
      poa.parameterValues.foreach(p => content.append(s"""    - "${p.getName}": "${sanitizeValue(p.getName, p.getValue)}"\n"""))
    }
    content.append("\n")
    content.toString()
  }

  private def extract(someVal: String): String = {
    if (someVal == null) {
      "n/a"
    } else {
      someVal
    }
  }
}

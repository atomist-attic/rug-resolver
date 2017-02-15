package com.atomist.rug.resolver.project

import com.atomist.param.ParameterValues
import com.atomist.project.edit.ProjectEditor
import com.atomist.project.generate.ProjectGenerator
import com.atomist.project.ProjectOperation
import com.atomist.source.{ArtifactSource, StringFileArtifact}
import org.springframework.stereotype.Component

@Component
class ProvenanceInfoWriter {

  val ProvenanceFile: String = ".atomist.yml"
  val SecretKeys: Seq[String] = Seq("password", "key", "secret", "token", "user")

  def write(projectSource: ArtifactSource, po: ProjectOperation, poa: ParameterValues, client: String): ArtifactSource = {

    val content = write(po, poa, client)

    val provenanceFile = projectSource.findFile(ProvenanceFile)
    val updatedContent = provenanceFile match {
      case Some(existingContent) => existingContent.content + content
      case None => content
    }

    projectSource.+(StringFileArtifact.apply(ProvenanceFile, updatedContent))
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
        content.append(s"""  group: "${extract(p.group)}""")
        content.append(s"""  artifact: "${extract(p.artifact)}"""")
        content.append(s"""  version: "${extract(p.version)}""")

        content.append(s"""  origin:\n""")
        content.append(s"""    repo: "${extract(p.repo())}""")
        content.append(s"""    branch: "${extract(p.branch())}""")
        content.append(s"""    sha: "${extract(p.sha())}""")
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
    if(someVal == null){
      "n/a\n"
    }else{
      someVal
    }
  }
}

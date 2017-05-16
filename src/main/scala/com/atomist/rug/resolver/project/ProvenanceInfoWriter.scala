package com.atomist.rug.resolver.project

import com.atomist.param.ParameterValues
import com.atomist.project.edit.ProjectEditor
import com.atomist.project.generate.ProjectGenerator
import com.atomist.project.ProjectOperation
import com.atomist.source.{ ArtifactSource, StringFileArtifact }
import org.springframework.stereotype.Component
import com.atomist.project.archive.RugResolver
import java.util.Optional

@Component
class ProvenanceInfoWriter {

  val ProvenanceFile: String = ".atomist.yml"
  val GitAttributesFile: String = ".gitattributes"
  val GitAttributesContent: String = s"$ProvenanceFile linguist-generated=true\n"
  val SecretKeys: Seq[String] = Seq("password", "key", "secret", "token", "user")

  def write(projectSource: ArtifactSource, po: ProjectOperation, poa: ParameterValues, client: String, resolver: RugResolver): ArtifactSource = {
    
    val content = write(po, poa, client, resolver)

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

    val attributesFile = source.findFile(GitAttributesFile)
    val attributesContent = attributesFile match {
      case Some(existingContent) => if (existingContent.content.contains(GitAttributesContent)) {
          existingContent.content
        }
        else {
          existingContent.content + GitAttributesContent
        }
      case None => GitAttributesContent
    }
    
    source.
      +(StringFileArtifact.apply(ProvenanceFile, updatedContent)).
      +(StringFileArtifact.apply(GitAttributesFile, attributesContent))
  }

  // how do we add that a Handler initiated the editor/generator?
  def write(po: ProjectOperation, poa: ParameterValues, client: String, resolver: RugResolver): String = {
    val gitInfo = ProvenanceInfoArtifactSourceReader.read(resolver.resolvedDependencies.source)
    val content = new StringBuilder()

    content.append("---\n");
    content.append(s"""kind: "operation"\n""");
    if (po != null) {
      content.append(s"""client: "${po.getClass.getPackage.getImplementationTitle}"\n""");
      content.append(s"""version: "${po.getClass.getPackage.getImplementationVersion}"\n""");
    }

    po match {
      case g: ProjectGenerator =>
        content.append("generator:\n")
      case e: ProjectEditor =>
        content.append("editor:\n")
      case _ =>
    }

    content.append(s"""  name: "${po.name}"\n""")

    resolver.findResolvedDependency(po) match {
      case Some(rd) =>
        rd.address match {
          case Some(a) =>
            content.append(s"""  group: "${extract(a.group)}"\n""")
            content.append(s"""  artifact: "${extract(a.artifact)}"\n""")
            content.append(s"""  version: "${extract(a.version)}"\n""")
          case _ =>
        }
      case _ =>
    }
    
    if (gitInfo.isPresent()) {
      val p = gitInfo.get;
      content.append(s"""  origin:\n""")
      content.append(s"""    repo: "${extract(p.repo())}"\n""")
      content.append(s"""    branch: "${extract(p.branch())}"\n""")
      content.append(s"""    sha: "${extract(p.sha())}"\n""")  
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
      poa.parameterValues.filter(p => !p.getName.startsWith("__") && !p.getName.equals("handle")).
        foreach(p => content.append(s"""    - "${p.getName}": "${sanitizeValue(p.getName, p.getValue)}"\n"""))
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

import {Project, File} from '@atomist/rug/model/Core'
import {ParametersSupport, Parameters} from '@atomist/rug/operations/Parameters'
import {ProjectEditor} from '@atomist/rug/operations/ProjectEditor'
import {PathExpression, PathExpressionEngine, Match} from '@atomist/rug/tree/PathExpression'
import {Result,Status} from '@atomist/rug/operations/Result'

import {parameter, inject, parameters, tag, editor} from '@atomist/rug/support/Metadata'

abstract class ContentInfo extends ParametersSupport {

  @parameter({description: "Content", displayName: "content", pattern: "$ContentPattern", maxLength: 100})
  content: string = null

}

@editor("A nice little editor")
@tag("java")
@tag("maven")
class SimpleEditor implements ProjectEditor<ContentInfo> {

    edit(project: Project, @parameters("ContentInfo") p: ContentInfo): Result {
      project.addFile("src/from/typescript", p.content);
      return new Result(Status.Success,
      `Edited Project now containing $${project.fileCount()} files: \n`);
    }
  }

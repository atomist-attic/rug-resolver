import {Project, File} from '@atomist/rug/model/Core'
import {ParametersSupport, Parameters} from '@atomist/rug/operations/Parameters'
import {ProjectEditor} from '@atomist/rug/operations/ProjectEditor'
import {PathExpression, PathExpressionEngine, Match} from '@atomist/rug/tree/PathExpression'
import {Result,Status} from '@atomist/rug/operations/Result'
 
import {parameter, inject, parameters, tag, editor} from '@atomist/rug/support/Metadata'
 
abstract class JavaInfo extends ParametersSupport {

  @parameter({description: "The Java package name", displayName: "Java Package", pattern: ".*", maxLength: 100})
  packageName: string = null

}

@editor("A nice little editor")
@tag("java")
@tag("maven")
class ConstructedEditor implements ProjectEditor<Parameters> {

    private eng: PathExpressionEngine;

    constructor(@inject("PathExpressionEngine") _eng: PathExpressionEngine ){
      this.eng = _eng;
    }
    edit(project: Project, @parameters("JavaInfo") ji: JavaInfo) {

      let pe = new PathExpression<Project,File>(`/*:file[name='pom.xml']`)
      //console.log(pe.expression);
      let m: Match<Project,File> = this.eng.evaluate(project, pe)

      ji["whatever"] = "thing"

      var t: string = `param=${ji.packageName},filecount=${m.root().fileCount()}`
      for (let n of m.matches())
        t += `Matched file=${n.path()}`;

        var s: string = ""

        project.addFile("src/from/typescript", "Anders Hjelsberg is God");
        for (let f of project.files())
            s = s + `File [${f.path()}] containing [${f.content()}]\n`
        return new Result(Status.Success,
        `${t}\n\nEdited Project containing ${project.fileCount()} files: \n${s}`)
    }
  }

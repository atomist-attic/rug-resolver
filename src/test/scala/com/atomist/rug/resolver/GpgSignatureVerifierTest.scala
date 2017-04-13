package com.atomist.rug.resolver

import java.io.File

import org.scalatest.{DiagrammedAssertions, FunSpec, OneInstancePerTest}

class GpgSignatureVerifierTest extends FunSpec with DiagrammedAssertions with OneInstancePerTest{

  val verifier = new GpgSignatureVerifier(Thread.currentThread().getContextClassLoader.getResourceAsStream("atomist_pub.gpg"))

  it("should verify files signed by atomist"){
    assert(verifier.verify(
      new File("src/test/resources/rug-functions-github-0.14.2-SNAPSHOT.jar"),
      new File("src/test/resources/rug-functions-github-0.14.2-SNAPSHOT.jar.asc")
    ))
  }
  it("should fail on files signed by atomist with corrupted signature"){
    assert(!verifier.verify(
      new File("src/test/resources/rug-functions-github-0.14.2-SNAPSHOT.jar"),
      new File("src/test/resources/rug-functions-github-0.14.2-SNAPSHOT.jar.asc.corrupt")
    ))
  }
  it("should fail if the jar as been modified") {
    assert(!verifier.verify(
      new File("src/test/resources/rug-functions-github-0.14.2-SNAPSHOT-corrupt.jar"),
      new File("src/test/resources/rug-functions-github-0.14.2-SNAPSHOT.jar.asc")
    ))
  }
  it("should work on poms too") {
    assert(verifier.verify(
      new File("src/test/resources/rug-functions-github-0.14.2-20170413150702.pom"),
      new File("src/test/resources/rug-functions-github-0.14.2-20170413150702.pom.asc")
    ))
  }

  it("should fail if the jar and signature is fine, but the cert is not the signing one") {
    val verifier = new GpgSignatureVerifier(Thread.currentThread().getContextClassLoader.getResourceAsStream("other_pub.gpg"))
    assert(!verifier.verify(
      new File("src/test/resources/rug-functions-github-0.14.2-SNAPSHOT.jar"),
      new File("src/test/resources/rug-functions-github-0.14.2-SNAPSHOT.jar.asc")
    ))
  }
}


package com.nparry.smircd.protocol

import org.specs.Specification

class NickNameSpec extends Specification {

  var f = { e: IllegalArgumentException => e.getMessage() }

  "NickName" should {

    "acceptValidNames" in {
      NickName.single("blah", f) mustEqual Right(NickName("blah"))
    }

    "acceptValidNamesInLists" in {
      NickName.list("blah,fred", f).right.get.size mustEqual 2
    }

    "rejectInvalidNames" in {
      NickName.single("with spaces", f).left.get mustEqual "with spaces"
      NickName.single("", f).left.get mustEqual ""
    }

    "handlesMixedValidity" in {
      NickName.listOfValid("ok,no good", { x => x}).size mustEqual 2
      NickName.listOfValid("ok,no good", { x => x})(0) mustEqual Right(NickName("ok"))
      NickName.listOfValid("ok,no good", { x => x})(1) mustEqual Left("no good")
    }

    "normalizeNames" in {
      NickName("Bob").normalized mustEqual NickName("bob").normalized
    }
  }

}


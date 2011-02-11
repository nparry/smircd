package com.nparry.smircd.daemon

trait ConnectionComponent {
  type Connection
  val sendMsg: (Connection, Any) => Unit
}


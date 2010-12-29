package com.nparry.smircd.netty

import java.nio.charset.Charset
import java.security._

import javax.net.ssl._

import sun.misc.BASE64Decoder

// Based on ...
// 1. http://docs.jboss.org/netty/3.2/xref/org/jboss/netty/example/securechat/SecureChatSslContextFactory.html
// 2. http://docs.jboss.org/netty/3.2/xref/org/jboss/netty/example/securechat/SecureChatKeyStore.html
class SslHelper {

  // Dummy base 64 encoded ssl cert
  // (to encode on command line - openssl enc -base64 -in cert.jks -out cert.base64)
  val dummyBase64EncodedSslCert = """
    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
    mMGpUNRAGVySXzzM
    """.replaceAll(" ", "")

  val keyStorePassword = "secret".toCharArray
  val certificatePassword = "secret".toCharArray

  def certificateAsInputStream = {
    new java.io.ByteArrayInputStream(new BASE64Decoder().decodeBuffer(dummyBase64EncodedSslCert))
  }

  def createSslContext(): SSLContext = {
    val algorithm = Option(Security.getProperty("ssl.KeyManagerFactory.algorithm"))
      .getOrElse("SunX509")

    val ks = KeyStore.getInstance("JKS");
    ks.load(certificateAsInputStream, keyStorePassword)

    // Set up key manager factory to use our key store
    val kmf = KeyManagerFactory.getInstance(algorithm);
    kmf.init(ks, certificatePassword)

    // Initialize the SSLContext to work with our key managers.
    val context = SSLContext.getInstance("TLS");
    context.init(kmf.getKeyManagers(), null, null);
    context
  }

}


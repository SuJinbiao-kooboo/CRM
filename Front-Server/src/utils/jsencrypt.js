import JSEncrypt from 'jsencrypt'

// 密钥对生成 http://web.chacuo.net/netrsakeypair

const publicKey = 'MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAKoR8mX0rGKLqzcWmOzbfj64K8ZIgOdH\n' +
  'nzkXSOVOZbFu6TdIhxoD5Jc8ClhfLETxmayfnEsapmPmyNDDzIbVBwIDAQAB'

const privateKey = 'MIIBVAIBADANBgkqhkiG9w0BAQEFAASCAT4wggE6AgEAAkEAqhHyZfSsYourNxaY\n' +
  '7Nt+PrgrxkiA50efORdI5U5lsW7pN0iHGgPklzwSWF8sRPGZrJ+cSxqmY+bI0MPM\n' +
  'htUHAgMBAAECQDYwJL2BtmLwbkNmSYzTiaxwFsMPbgpaafGm9zeMXiHCD1MJMrdo\n' +
  'IuKj6FdqzUVQ0841XiVOFiuQjsH+EwM1AiECIQDWm4yrD2ykRK3F9ag3C1kzgHX2\n' +
  'qHhHNuJTrgiJBwIhAMrBjdpHtdeBuxiMXCjEVLJVHhNtKtrj1And9c4VuYjxAiAT\n' +
  'b1faQjMX9d0EpFjVBoo4N3ImnkCG6slWBbuHAoGAFpFxWtGDx4fzijBqakBdnVtm\n' +
  'RLQCDnVtEkxB5+CJzQIgM5m+vP8uVRZs4L5B6rBnVtmRLQCDnVtEkxB5+CJzQI='

// 加密
export function encrypt(txt) {
  const encryptor = new JSEncrypt()
  encryptor.setPublicKey(publicKey) // 设置公钥
  return encryptor.encrypt(txt) // 对数据进行加密
}

// 解密
export function decrypt(txt) {
  const encryptor = new JSEncrypt()
  encryptor.setPrivateKey(privateKey) // 设置私钥
  return encryptor.decrypt(txt) // 对数据进行解密
}
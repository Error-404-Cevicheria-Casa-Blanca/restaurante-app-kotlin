package com.lacasablanca.mesero.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Cifra el token con AES/GCM usando una clave del Android Keystore.
 * El Keystore nunca expone la clave: solo la usa para cifrar/descifrar.
 * Si el material de clave quedo invalido (p. ej. restauracion de backup),
 * se devuelve null y el llamador borra la sesion.
 */
class CryptoManager(private val alias: String = ALIAS_POR_DEFECTO) {

    fun cifrar(texto: String): String {
        val cifrador = Cipher.getInstance(TRANSFORMACION)
        cifrador.init(Cipher.ENCRYPT_MODE, obtenerClave())
        val iv = cifrador.iv
        val cifrado = cifrador.doFinal(texto.toByteArray(Charsets.UTF_8))
        val codificador = Base64.getEncoder()
        return codificador.encodeToString(iv) + SEPARADOR + codificador.encodeToString(cifrado)
    }

    fun descifrar(paquete: String): String? {
        return try {
            val partes = paquete.split(SEPARADOR)
            if (partes.size != 2) return null
            val decodificador = Base64.getDecoder()
            val iv = decodificador.decode(partes[0])
            val cifrado = decodificador.decode(partes[1])
            val cifrador = Cipher.getInstance(TRANSFORMACION)
            cifrador.init(Cipher.DECRYPT_MODE, obtenerClave(), javax.crypto.spec.GCMParameterSpec(TAMANO_ETIQUETA_BITS, iv))
            String(cifrador.doFinal(cifrado), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun obtenerClave(): SecretKey {
        val almacen = KeyStore.getInstance(PROVEEDOR).apply { load(null) }
        val entrada = almacen.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        if (entrada != null) return entrada.secretKey

        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVEEDOR)
        generador.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generador.generateKey()
    }

    private companion object {
        const val ALIAS_POR_DEFECTO = "lacasablanca_sesion_mesero"
        const val PROVEEDOR = "AndroidKeyStore"
        const val TRANSFORMACION = "AES/GCM/NoPadding"
        const val SEPARADOR = ":"
        const val TAMANO_ETIQUETA_BITS = 128
    }
}

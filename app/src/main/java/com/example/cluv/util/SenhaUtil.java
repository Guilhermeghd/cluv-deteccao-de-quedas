package com.example.cluv.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;


public class SenhaUtil {

    private static final int ITERACOES = 100000;
    private static final int TAMANHO_CHAVE_BITS = 256;

    public static String gerarSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public static String gerarHash(String senha, String salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    senha.toCharArray(), Base64.getDecoder().decode(salt), ITERACOES, TAMANHO_CHAVE_BITS);
            SecretKeyFactory fabrica = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return Base64.getEncoder().encodeToString(fabrica.generateSecret(spec).getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("Nao foi possivel gerar o hash da senha", e);
        }
    }

    public static boolean conferir(String senhaDigitada, String salt, String hashSalvo) {
        byte[] calculado = gerarHash(senhaDigitada, salt).getBytes(StandardCharsets.UTF_8);
        byte[] salvo = hashSalvo.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(calculado, salvo);
    }
}

package com.example.cluv.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Guarda a senha de forma segura: nunca o texto original, e sim um "hash".
 * Cada usuario tem um "salt" aleatorio proprio, entao duas pessoas com a mesma senha
 * ficam com hashes diferentes. Usa PBKDF2 (padrao recomendado, ja vem no Android).
 */
public class SenhaUtil {

    private static final int ITERACOES = 100000;
    private static final int TAMANHO_CHAVE_BITS = 256;

    /** Gera um salt aleatorio de 16 bytes (em texto Base64). */
    public static String gerarSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /** Transforma a senha em hash usando o salt. Sempre devolve o mesmo hash para a mesma senha + salt. */
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

    /** Confere se a senha digitada bate com o hash guardado (comparacao em tempo constante). */
    public static boolean conferir(String senhaDigitada, String salt, String hashSalvo) {
        byte[] calculado = gerarHash(senhaDigitada, salt).getBytes(StandardCharsets.UTF_8);
        byte[] salvo = hashSalvo.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(calculado, salvo);
    }
}

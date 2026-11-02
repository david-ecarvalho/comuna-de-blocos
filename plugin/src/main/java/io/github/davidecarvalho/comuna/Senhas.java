package io.github.davidecarvalho.comuna;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Senha do login próprio, sem nada do servidor. Guarda só o hash (PBKDF2 com SHA-256 e sal aleatório), nunca a
 * senha. Formato: "pbkdf2$iterações$sal$hash", em Base64.
 */
public final class Senhas {

    private Senhas() { }

    static final int ITERACOES = 210_000;
    private static final SecureRandom ACASO = new SecureRandom();

    /** Por que a senha não serve, ou vazio se serve. */
    public static Optional<String> problema(String senha, String nick) {
        if (senha.length() < 6) {
            return Optional.of("A senha precisa de pelo menos 6 caracteres.");
        }
        if (senha.length() > 64) {
            return Optional.of("A senha pode ter no máximo 64 caracteres.");
        }
        if (senha.equalsIgnoreCase(nick)) {
            return Optional.of("A senha não pode ser o seu nick.");
        }
        return Optional.empty();
    }

    public static String hash(String senha) {
        byte[] sal = new byte[16];
        ACASO.nextBytes(sal);
        return "pbkdf2$" + ITERACOES + "$" + b64(sal) + "$" + b64(derivar(senha, sal, ITERACOES));
    }

    public static boolean confere(String senha, String guardado) {
        String[] p = guardado.split("\\$");
        if (p.length != 4 || !p[0].equals("pbkdf2")) {
            return false;
        }
        byte[] sal = Base64.getDecoder().decode(p[2]);
        byte[] esperado = Base64.getDecoder().decode(p[3]);
        return MessageDigest.isEqual(esperado, derivar(senha, sal, Integer.parseInt(p[1])));
    }

    private static byte[] derivar(String senha, byte[] sal, int iteracoes) {
        try {
            PBEKeySpec spec = new PBEKeySpec(senha.toCharArray(), sal, iteracoes, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String b64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }
}

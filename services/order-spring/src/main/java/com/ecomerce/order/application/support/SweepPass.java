package com.ecomerce.order.application.support;

/**
 * La señal del DRENAJE de un barrido (DSL 2.26, incremento 8): una pasada que reclamó su lote lleno puede dejar
 * candidatos, y el tick del cronómetro la repite.
 *
 * <p>Un tick en modo clúster lo ejecuta UNA réplica, así que, si cada tick hiciera una sola pasada, el barrido rendiría lo
 * de una réplica y no lo de todas. Quien reclama sabe si el lote salió lleno: lo avisan los reclamos que genera build, y
 * TÚ, si el handler reclama su lote a mano, con {@code SweepPass.claimed(reclamadas, lote)}. Sin aviso, el tick hace una
 * pasada, que es correcto pero más lento con atrasos grandes.
 *
 * <p>Fuera de un tick (una prueba, un despacho por otra puerta) no hace nada.
 */
public final class SweepPass {

    private static final ThreadLocal<boolean[]> PASS = new ThreadLocal<>();

    private SweepPass() {
    }

    /** Lo llama quien reclama: {@code claimed} de una cota de {@code batchSize}. Lleno = puede que queden. */
    public static void claimed(int claimed, int batchSize) {
        boolean[] pass = PASS.get();
        if (pass != null && batchSize > 0 && claimed >= batchSize) {
            pass[0] = true;
        }
    }

    /** El tick abre una pasada. */
    public static void begin() {
        PASS.set(new boolean[1]);
    }

    /** El tick la cierra: ¿algún reclamo de la pasada salió lleno? */
    public static boolean end() {
        boolean[] pass = PASS.get();
        PASS.remove();
        return pass != null && pass[0];
    }
}

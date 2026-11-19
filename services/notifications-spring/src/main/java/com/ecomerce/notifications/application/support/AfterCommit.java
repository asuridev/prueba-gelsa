package com.ecomerce.notifications.application.support;

/**
 * Programa un efecto que sale del proceso (llamar a un proveedor ajeno, borrar un binario) para
 * DESPUÉS de que confirme la transacción del caso de uso.
 *
 * <p>Si la transacción revierte —o pierde una carrera de concurrencia—, el efecto no ocurre nunca;
 * sin transacción abierta, ocurre ya. Un fallo del efecto se registra y no deshace lo confirmado,
 * porque la transacción ya no existe.
 *
 * <p>Es la herramienta de una regla del diseño que dice «después de confirmar»: dentro de la
 * transacción, la copia que pierde una carrera ya habría llamado al proveedor.
 */
public interface AfterCommit {

    void run(Runnable action);
}

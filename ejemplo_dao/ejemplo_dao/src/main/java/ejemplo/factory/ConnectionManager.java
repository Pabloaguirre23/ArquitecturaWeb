package ejemplo.factory;

import java.sql.Connection;

/**
 * CONTRATO que debe cumplir el gestor de conexiones de CADA motor.
 *
 * Antes esta clase era la conexión concreta a MySQL (driver, URL y password hardcodeados),
 * lo cual dejaba código específico de un motor dentro del paquete de las abstracciones.
 * Ahora es solo la interfaz: las implementaciones viven junto a su familia de DAOs,
 * en ejemplo.repository.mysql y ejemplo.repository.derby.
 *
 * Si mañana agregás Postgres, esta interfaz te dice exactamente qué tenés que escribir.
 *
 * Nota sobre el Singleton: el método getInstance() NO puede estar acá.
 * En Java los miembros estáticos no se heredan de forma polimórfica, así que cada
 * implementación mantiene su propio campo 'instance' y su propio getInstance().
 * La interfaz define el comportamiento de la INSTANCIA, no cómo se obtiene.
 */
public interface ConnectionManager {

    /** Devuelve la conexión abierta contra el motor correspondiente. */
    Connection getConnection();

    /**
     * Cierra la conexión y libera los recursos del motor.
     * Es parte del contrato porque no todos los motores se cierran igual:
     * MySQL alcanza con cerrar la Connection, mientras que Derby embebido
     * exige además un shutdown explícito del engine.
     */
    void shutdown();
}

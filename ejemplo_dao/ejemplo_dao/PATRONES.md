# Patrones de diseño en el ejemplo DAO

Este proyecto implementa la misma aplicación sobre **dos motores de base de datos distintos** —MySQL y Apache Derby— sin que el código cliente sepa cuál está usando. Para lograrlo combina cuatro patrones de diseño.

Todas las referencias apuntan a archivos y líneas reales del proyecto.

---

## Mapa general

```
ejemplo/
├── Main.java                        ← Cliente (punto de switcheo)
├── utils/
│   ├── BorrarDatos.java             ← Cliente
│   └── CargarDatosIniciales.java    ← Cliente
│
├── dao/                             ← PRODUCTOS ABSTRACTOS (interfaces)
│   ├── UsuarioDAO.java
│   ├── ProductoDAO.java
│   ├── PedidoDAO.java
│   └── DetallePedidoDAO.java
│
├── factory/                         ← ABSTRACCIONES (cero SQL específico)
│   ├── DAOFactory.java              ← Fábrica abstracta
│   ├── ConnectionManager.java       ← Contrato del gestor de conexiones
│   └── DBType.java                  ← Enum de motores disponibles
│
└── repository/                      ← PRODUCTOS CONCRETOS
    ├── mysql/                       ← Familia MySQL (6 clases)
    │   ├── MySQLDAOFactory.java
    │   ├── MySQLConnectionManager.java
    │   └── MySQL*DAO.java  (×4)
    └── derby/                       ← Familia Derby (6 clases)
        ├── DerbyDAOFactory.java
        ├── DerbyConnectionManager.java
        └── Derby*DAO.java  (×4)
```

Los cuatro patrones que conviven:

| Patrón | Dónde vive | Qué resuelve |
|---|---|---|
| **Abstract Factory** | `DAOFactory` + las dos fábricas concretas | Crear familias completas y coherentes de objetos |
| **Factory Method** | Cada `createXxxDAO()` y `getConnection()` | Diferir a la subclase qué clase concreta instanciar |
| **Singleton** | `DAOFactory`, `MySQLConnectionManager`, `DerbyConnectionManager` | Que exista una sola instancia por proceso |
| **Template Method** | `DAOFactory.shutdown()` | Fijar el esqueleto de un algoritmo y variar un paso |

---

## 1. Abstract Factory

> Proveer una interfaz para crear **familias de objetos relacionados** sin especificar sus clases concretas.

### Los cuatro roles

| Rol (GoF) | En este proyecto | Archivo |
|---|---|---|
| **AbstractFactory** | `DAOFactory` | `factory/DAOFactory.java:13` |
| **ConcreteFactory** | `MySQLDAOFactory` | `repository/mysql/MySQLDAOFactory.java:11` |
| | `DerbyDAOFactory` | `repository/derby/DerbyDAOFactory.java:18` |
| **AbstractProduct** | `ProductoDAO`, `FacturaProductoDAO`, `FacturaDAO`, `ClienteDAO` | `dao/*.java` |
| | `ConnectionManager` | `factory/ConnectionManager.java:20` |
| **ConcreteProduct** | `MySQLProductoDAO` … `DerbyClienteDAO` + los 2 managers | `repository/**` (10 clases) |
| **Client** | `Main`, `BorrarDatos`, `CargarDatosIniciales` | `Main.java`, `utils/` |

Son **cinco productos por familia**: los cuatro DAO más el gestor de conexión.

### La fábrica abstracta

`DAOFactory` declara *qué* se puede crear, sin decir *cómo*:

```java
public abstract UsuarioDAO createUsuarioDAO();              // :94
public abstract ProductoDAO createProductoDAO();            // :95
public abstract PedidoDAO createPedidoDAO();                // :96
public abstract DetallePedidoDAO createDetallePedidoDAO();  // :97
protected abstract Connection getConnection();              // :111
```

`MySQLDAOFactory` y `DerbyDAOFactory` son estructuralmente **gemelas**: mismos métodos, mismo orden, distinta familia devuelta.

### Dónde se elige la familia

`DAOFactory.java:42-47` — el único `switch` sobre el motor en todo el proyecto:

```java
case MYSQL:
    instance = new MySQLDAOFactory();
    break;
case DERBY:
    instance = new DerbyDAOFactory();
    break;
```

Agregar PostgreSQL sería: escribir `PostgresDAOFactory` + sus cinco productos + descomentar el `case` de la línea 50. **Cero cambios en el código cliente.**

### La propiedad que realmente importa: coherencia de familia

Esto es lo que distingue al Abstract Factory de un simple Factory Method.

Un `MySQLProductoDAO` construido con una `Connection` de Derby **compilaría perfecto** —ambas son `java.sql.Connection`— y explotaría en tiempo de ejecución al ejecutar `AUTO_INCREMENT`, que en Derby no existe. Es un error que el compilador no puede atrapar.

El patrón lo hace **imposible por construcción**: como los cinco productos salen de la misma instancia de fábrica, y cada fábrica cablea sus DAO con su propia conexión vía `getConnection()`, no hay ninguna ruta en el código por la que se puedan mezclar motores. No es una convención que haya que recordar: es una imposibilidad estructural.

### La prueba del desacoplamiento

El código cliente **no menciona ni una sola clase concreta**. Buscando `MySQL`, `Derby` o `repository` en `Main.java`, `BorrarDatos.java` y `CargarDatosIniciales.java`, la única coincidencia está dentro de un comentario (`Main.java:85`). En código ejecutable: cero.

La demostración empírica son dos corridas del **mismo `Main` sin recompilar**:

```
=== Motor de base de datos: DERBY ===     === Motor de base de datos: MYSQL ===
Usuarios cargados OK.                     Usuarios cargados OK.
findById: Usuario(id=1, Ada Lovelace…)    findById: Usuario(id=1, Ada Lovelace…)
Derby se apagó correctamente.             Conexión con MySQL cerrada.
```

Debajo de esa salida idéntica corrió SQL genuinamente distinto (ver §5).

---

## 2. Factory Method

> Definir una interfaz para crear un objeto, pero dejar que las **subclases decidan** qué clase instanciar.

### Los cinco Factory Methods

| Método | Declarado en `DAOFactory` | MySQL | Derby |
|---|---|---|---|
| `createUsuarioDAO()` | `:94` | `:30` | `:36` |
| `createProductoDAO()` | `:95` | `:37` | `:41` |
| `createPedidoDAO()` | `:96` | `:43` | `:46` |
| `createDetallePedidoDAO()` | `:97` | `:48` | `:51` |
| `getConnection()` | `:111` | `:19` | `:25` |

### Los cuatro roles

| Rol (GoF) | En este proyecto |
|---|---|
| **Product** | `ProductoDAO` (la interfaz) |
| **ConcreteProduct** | `MySQLProductoDAO`, `DerbyProductoDAO` |
| **Creator** | `DAOFactory` (declara el método, no lo implementa) |
| **ConcreteCreator** | `MySQLDAOFactory`, `DerbyDAOFactory` |

### La firma promete menos de lo que entrega

Todo el patrón vive en la asimetría entre el tipo de retorno y el `new`:

```java
// MySQLDAOFactory.java:30-33
public UsuarioDAO createUsuarioDAO() {            // ← promete la INTERFAZ
    return new MySQLUsuarioDAO(getConnection());  // ← entrega la CLASE CONCRETA
}
```

`DerbyDAOFactory.java:36` tiene la firma **idéntica** y devuelve `new DerbyUsuarioDAO(...)`. Misma promesa, distinta entrega.

### Qué pasaría sin el patrón

`Main` tendría que escribir:

```java
UsuarioDAO usuarioDAO = new MySQLUsuarioDAO(unaConexionQueTengoQueConseguir);
```

Quedaría atado a dos cosas: a la palabra `MySQLProductoDAO` y a saber conseguir una `Connection`. Para pasar a Derby habría que editar `Main` y todas las clases que hicieran lo mismo.

El Factory Method **mueve ese `new` a un lugar donde se puede cambiar sin tocar a nadie más**.

### La secuencia completa

```
Main pide un UsuarioDAO  (Main.java:54)
      ↓
f es un DAOFactory  ──→  ¿qué método se ejecuta realmente?
      ↓                   Depende del objeto que haya adentro de f.
      ↓                   Lo elige Java por POLIMORFISMO, no un if.
      ↓
Se ejecuta el createUsuarioDAO() de la fábrica que corresponda
      ↓
Ese método hace el new de SU clase concreta
      ↓
Main recibe un UsuarioDAO y nunca se entera de cuál era
```

Como no hay ningún condicional decidiendo, agregar un motor nuevo **no obliga a modificar código existente**: alcanza con escribir una subclase más.

### Hace más que un `new`: encapsula el cableado

El método no solo elige la clase, también **inyecta la dependencia**:

```java
return new MySQLUsuarioDAO(getConnection());
```

Sin esto, el cliente tendría que conseguir una `Connection` por su cuenta y pasársela al constructor, quedando acoplado a JDBC y al motor. Con el patrón, `Main` nunca ve una `Connection` en toda su ejecución.

### El caso especial de `getConnection()`

Es el único `protected` de los cinco, y eso es deliberado. Es un Factory Method **para uso interno del Creator**, no para el cliente:

```java
protected abstract Connection getConnection();   // DAOFactory.java:111
```

Los otros cuatro Factory Methods lo llaman. Ése es el uso canónico del GoF: el Creator tiene métodos que operan sobre productos que él mismo no sabe crear, y delega esa creación en el Factory Method.

Al ser `protected`, el cliente **no puede invocarlo aunque quisiera**. El acoplamiento a JDBC queda encerrado dentro de la jerarquía de fábricas.

### Dónde se consumen

Doce llamadas en tres clases: `Main.java:54-57`, `BorrarDatos.java:18-21`, `CargarDatosIniciales.java:31-34`.

Todas siguen la misma forma, y lo importante es el **tipo declarado a la izquierda**:

```java
DetallePedidoDAO clienteDAO = f.createDetallePedidoDAO();
```

> ⚠️ Si alguien escribiera `MySQLDetallePedidoDAO clienteDAO = ...`, el patrón se rompería —no compilaría contra Derby— y todo el trabajo se perdería. **El patrón se puede sabotear desde el lado del cliente.**

### Tres cosas que parecen Factory Method y no lo son

**1. `DAOFactory.getInstance(DBType)` (`:37`) no es un Factory Method.**
Es un método `static` con un `switch`. No hay subclase que lo sobrescriba: la decisión no es polimórfica, está en un condicional. Es el idiom *Static Factory Method* (Bloch) o "Simple Factory", que **no es un patrón GoF**.
Diferencia práctica: agregar Postgres obliga a **modificar** ese `switch`, mientras que con un Factory Method real solo se **agrega** una subclase.

**2. `doShutdown()` (`:138`) tampoco lo es.**
Es abstracto y polimórfico, sí, pero **no crea ningún objeto**: cierra recursos. Es el hook variable de un Template Method. Un Factory Method, por definición, es creacional.

**3. `DBType` no es parte del patrón.**
Es un enum de configuración que alimenta al `switch` de la fábrica estática.

### Relación con el Abstract Factory

Son el mismo código leído a dos niveles:

- Mirando **un** `createXxxDAO()` aislado → **Factory Method**.
- Mirando **los cinco juntos**, con la garantía de que salen de la misma fábrica y por lo tanto pertenecen al mismo motor → **Abstract Factory**.

Un Abstract Factory *se implementa como* un conjunto de Factory Methods. Lo que agrega es la coherencia de familia.

---

## 3. Singleton

> Garantizar que una clase tenga **una sola instancia** y proveer un punto de acceso global a ella.

En este proyecto hay **tres** Singletons:

| Clase | Qué garantiza | Línea del campo |
|---|---|---|
| `DAOFactory` | una fábrica → un motor por proceso | `:23` |
| `MySQLConnectionManager` | una conexión a MySQL | `:21` |
| `DerbyConnectionManager` | una conexión a Derby | `:20` |

### Qué problema resuelve

Abrir una conexión es caro: cargar el driver, negociar con el servidor, autenticarse. Si cada uno de los cuatro DAO abriera la suya, tendrías cuatro conexiones haciendo el mismo trabajo.

El Singleton hace que la primera llamada la cree y **todas las demás reciban esa misma**. En la corrida real, el mensaje `"Conexión establecida correctamente con Derby"` aparece **una sola vez**, aunque se construyeron cuatro DAO. Ésa es la prueba de que funcionó.

### Las tres piezas del mecanismo

Tomando `MySQLConnectionManager`:

**1. El campo estático que guarda la única instancia** (`:21`)

```java
private static volatile MySQLConnectionManager instance;
```

Es `static`: pertenece a la clase, no a los objetos. Hay exactamente uno en toda la JVM.

**2. El constructor privado** (`:30`)

```java
private MySQLConnectionManager() { ... }
```

Ésta es la pieza que **hace cumplir** la regla. Al ser `private`, nadie afuera puede hacer `new MySQLConnectionManager()`. Sin ella, el patrón sería una sugerencia amable en vez de una garantía.

**3. La puerta de entrada** (`:49`)

```java
public static MySQLConnectionManager getInstance() { ... }
```

Como el constructor está cerrado, éste es el único camino para obtener el objeto. Y al ser el único camino, puede controlar cuántos se crean.

### El Double-Checked Locking, paso a paso

```java
if (instance == null) {                               // :50  1er chequeo
    synchronized (MySQLConnectionManager.class) {     // :51  cerrojo
        if (instance == null) {                       // :52  2do chequeo
            instance = new MySQLConnectionManager();  // :53
        }
    }
}
return instance;
```

**Por qué el `synchronized`.** Sin él, dos hilos podrían entrar a la vez, ver `instance == null` los dos, y crear **dos** conexiones. El cerrojo obliga a que entre uno por vez.

**Por qué entonces el primer `if`, si el cerrojo ya alcanza.** Porque `synchronized` es caro y solo hace falta **una vez en la vida del programa**. Después de la primera llamada, `instance` nunca vuelve a ser `null`, así que el primer `if` da `false` y se retorna directo, sin tocar el cerrojo. Con el cerrojo solo, pagarías ese costo en las mil llamadas siguientes para nada.

**Por qué el segundo `if`.** Porque entre que el hilo A ve `null` y logra entrar al cerrojo, el hilo B pudo haber entrado antes y ya haberlo creado. Cuando A finalmente entra, tiene que **volver a mirar**. Sin ese segundo chequeo, A pisaría la instancia de B y habría dos.

> El primer chequeo es por **velocidad**; el segundo, por **corrección**. Sacar cualquiera de los dos rompe algo distinto.

### Por qué `volatile`

`new MySQLConnectionManager()` no es una operación única: reserva memoria, corre el constructor, y asigna la referencia. El compilador y el procesador pueden **reordenar** esos pasos y asignar la referencia *antes* de terminar el constructor.

Sin `volatile`, otro hilo podría ver `instance != null` en el primer chequeo, saltarse todo, y devolver un objeto **a medio construir**, con `connection` todavía en `null`. Un `NullPointerException` intermitente e imposible de reproducir.

`volatile` prohíbe ese reordenamiento y fuerza que los cambios se vean entre hilos.

### Una asimetría real entre los tres

**Los dos managers tienen el Singleton garantizado; `DAOFactory` no.**

`MySQLConnectionManager` es `final` y su constructor es `private`. Imposible saltarse el patrón.

`DAOFactory` es abstracta, y `MySQLDAOFactory` **no declara ningún constructor**, así que Java le da uno público por defecto. Nada impide escribir:

```java
DAOFactory otra = new MySQLDAOFactory();   // compila perfecto
```

y saltearse el Singleton por completo. Ahí el patrón es una **convención**, no una garantía.

> 🔧 **Ejercicio:** agregar un constructor `protected` a cada fábrica concreta para cerrar el agujero.

### El ciclo de vida: crear no alcanza, hay que poder cerrar

Un Singleton clásico vive para siempre. Acá no puede, porque envuelve un recurso que hay que liberar. Por eso `shutdown()` termina con:

```java
synchronized (MySQLConnectionManager.class) {
    instance = null;      // :82
}
```

Vuelve a poner el campo en `null` para que el próximo `getInstance()` construya uno nuevo, en vez de devolver un manager con la conexión ya cerrada. Lo mismo hace `DAOFactory.shutdown()` en `:133`.

Sin esa línea, después de cerrar la base la aplicación quedaba envenenada: `getInstance()` seguía devolviendo la instancia muerta y el error aparecía recién al usar un DAO, con un mensaje que no apuntaba a la causa.

### El precio del patrón

El Singleton tiene un costo, y en este proyecto se pagó en efectivo.

Como `DAOFactory.instance` es **uno solo para todo el proceso**, el primer `getInstance()` decide el motor y los siguientes lo ignoran. Por eso, al cambiar a Derby, **no alcanzaba con editar la línea donde `Main` pide la fábrica**: `new BorrarDatos()` corre antes (`Main.java:45`) y ya la había fijado en MySQL. El cambio no habría tenido ningún efecto y la aplicación habría seguido en MySQL en silencio.

La solución fue mover la decisión a la primerísima línea de `main()`. Pero notá lo que eso significa: **el Singleton convirtió un cambio local en un problema de orden global**. Ése es exactamente el motivo por el que muchos lo consideran un antipatrón: introduce estado global y acoplamiento temporal invisible.

La consecuencia de fondo sigue ahí: aunque el Abstract Factory *podría* soportar dos motores vivos a la vez (para migrar datos de MySQL a Derby, por ejemplo), el Singleton lo prohíbe. **Un patrón le pone techo al otro.**

---

## 4. Template Method

> Definir el esqueleto de un algoritmo, delegando algunos pasos a las subclases.

`DAOFactory.java:130-138`:

```java
public final void shutdown() {
    doShutdown();                      // paso VARIABLE: lo define cada motor
    synchronized (DAOFactory.class) {
        instance = null;               // paso FIJO: invalida el Singleton
    }
}

protected abstract void doShutdown();
```

El método público es `final` —nadie puede alterar el esqueleto— y delega el paso que varía. Y varía de verdad:

- `MySQLConnectionManager.shutdown()` cierra la `Connection` y listo; el servidor vive en otro proceso.
- `DerbyConnectionManager.shutdown()` cierra la conexión **y además** apaga el motor embebido con `jdbc:derby:;shutdown=true`, tratando el `SQLState "XJ015"` como éxito.

Misma firma, procedimientos incomparables. El resultado es que `Main.java:86` puede escribir una línea que no sabe nada del motor:

```java
f.shutdown();   // ¿cerró MySQL o Derby? Main no tiene idea
```

---

## 5. Por qué hacen falta dos familias de DAO

Si el SQL fuera portable, bastaría con una sola familia de DAO parametrizada por la conexión, y el Abstract Factory sería innecesario. No lo es:

| Concepto | MySQL | Derby |
|---|---|---|
| Crear tabla si no existe | `CREATE TABLE IF NOT EXISTS` | No existe → hay que descartar `SQLState X0Y32` |
| Autoincremento | `AUTO_INCREMENT` | `GENERATED BY DEFAULT AS IDENTITY` |
| Reiniciar contador | `ALTER TABLE t AUTO_INCREMENT = 1` | `ALTER TABLE t ALTER COLUMN id RESTART WITH 1` |
| Limitar filas | `LIMIT 1` | `FETCH FIRST 1 ROWS ONLY` |
| Tipo para `Float` | `FLOAT` | `REAL` |
| Índice | dentro del `CREATE TABLE` | `CREATE INDEX` aparte |
| `GROUP BY` | permite columnas no agregadas fuera del `GROUP BY` | exige **todas** las no agregadas en el `GROUP BY` |

La consulta `findTopProductByUnits()` concentra tres de estas diferencias a la vez. Compará `MySQLFacturaDAO` con `DerbyFacturaDAO`.

En cambio, los métodos `map(ResultSet)` quedaron **idénticos** entre familias, porque `ResultSet` sí es portable. Ese contraste es la lección: **lo que cambia es el SQL, no la API JDBC.**

---

## 6. Cómo switchear de motor

### Opción A — editar una línea

`Main.java:24`:

```java
private static final DBType MOTOR = DBType.DERBY;   // ← cambiar a MYSQL
```

### Opción B — sin recompilar

```bash
java -Ddb.type=MYSQL -cp target/classes:... ejemplo.Main
```

Ambas funcionan porque `Main.java:42` publica el motor elegido como *system property* **antes de que nadie pida la fábrica**:

```java
System.setProperty("db.type", System.getProperty("db.type", MOTOR.name()));
```

Y a partir de ahí `Main`, `BorrarDatos` y `CargarDatosIniciales` usan todos `DAOFactory.getInstance()` sin parámetro. Una sola fuente de verdad.

> ⚠️ El orden importa. Esa línea **tiene que ser la primera** de `main()`: ver "El precio del patrón" en §3.

### Configuración de cada motor

- **MySQL** — requiere el servidor corriendo. URL, usuario y password en `MySQLConnectionManager.java:25-27`.
- **Derby** — embebido, no requiere servidor. Crea la base como una carpeta `derby_dao_DB/` en el directorio de trabajo (ya está en `.gitignore`).

---

## 7. Limitaciones conocidas y ejercicios

1. **El Singleton de `DAOFactory` no está garantizado.** `new MySQLDAOFactory()` compila. *Ejercicio: constructor `protected` en las fábricas concretas.*

2. **Un solo motor activo por proceso.** El Singleton impide tener MySQL y Derby vivos a la vez. *Ejercicio: reemplazar el Singleton por un `Map<DBType, DAOFactory>`.*

3. **`findTopProductByUnits()` es no determinística.** Ordena solo por `unidades_vendidas DESC`, sin criterio de desempate. En los datos de prueba **tres productos empatan en 2 unidades** (ids 1, 2 y 5), y cada motor devuelve uno distinto. *Ejercicio: agregar `ORDER BY unidades_vendidas DESC, ingreso_total DESC, p.nombre ASC` en ambos DAO.*

4. **La tabla `pedidos` está definida dos veces** en la familia MySQL, con columnas distintas: `MySQLPedidoDAO.java:33` (sin timestamps) y `MySQLDetallePedidoDAO.java:21` (con `created_at`/`updated_at`). Como ambas usan `IF NOT EXISTS`, gana la que corra primero. Funciona por accidente y se rompería al reordenar los métodos de la fábrica. *Ejercicio: unificar la definición.*

5. **Los DAO lanzan `RuntimeException`** envolviendo `SQLException`. Simplifica el ejemplo pero pierde el tipado del error. *Ejercicio: definir una `DAOException` propia.*

---

## Resumen en una frase por patrón

- **Factory Method** — reemplaza un `new` fijo por una llamada a un método que las subclases redefinen, para que el código que necesita el objeto deje de saber qué clase lo construye.
- **Abstract Factory** — agrupa varios Factory Methods en una misma fábrica, garantizando que todos los objetos creados pertenezcan a la misma familia.
- **Singleton** — cierra el constructor y expone un único punto de acceso, para que exista una sola instancia por proceso.
- **Template Method** — fija el esqueleto de un algoritmo en un método `final` y delega en las subclases únicamente los pasos que varían.

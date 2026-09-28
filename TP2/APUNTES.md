# Apunte TP2 — Integrador JPA/JPQL (para el escrito)

> Generado desde el código local en `TP2/src`. Todo sigue sin pushear.

## 1. Qué va en cada lugar

| Carpeta | Contenido | Ejemplo local |
|---|---|---|
| `entity/` | Clases JPA = tablas. `@Entity`, `@Id`, `@Column`, relaciones | `Estudiante` (PK `dni`, `lu` único), `Carrera`, `EstudianteCarrera` (intermedia con datos: inscripción, graduación, antigüedad) |
| `dto/` | Records inmutables para informes agregados | `CarreraInscriptosDTO`, `ReporteCarreraDTO` |
| `repository/` | Acceso a datos: `EntityManager` + JPQL | `EstudianteRepository` (a–g), `CarreraRepository` (reporte 3) |
| `util/JPAUtil` | Singleton de `EntityManagerFactory` (1 por PU) | double-checked locking + `close()` |
| `util/CargadorCSV` | Carga inicial, tolera datos sucios | saltea `[WARN]`, `graduacion 0 → null` |
| `META-INF/persistence.xml` | Unidades de persistencia | `derbyPU` (default), `mysqlPU` |
| `Main` | Orquesta: EM → carga → consultas → cierre | `getEntityManager()` … `em.close()` + `JPAUtil.close()` |

**Regla de oro (examen):** entidad para CRUD, **DTO para `COUNT/GROUP BY`**.
En TP1 nos bajaron puntos por devolver `List<Cliente>` en el informe en vez
de un DTO con el monto agregado.

## 2. Relaciones (cómo leerlas)

- `Estudiante 1—N EstudianteCarrera` : `Estudiante.carreras` con
  `@OneToMany(mappedBy = "estudiante")`.
- `Carrera 1—N EstudianteCarrera` : `Carrera.inscriptos` con
  `@OneToMany(mappedBy = "carrera")`.
- `EstudianteCarrera N—1` a cada una: `@ManyToOne + @JoinColumn`.
- `mappedBy` = "el dueño es el otro lado" (siempre el `@ManyToOne` manda).

## 3. JPQL paso a paso (con el código real)

Estructura: `SELECT ... FROM ... [JOIN ...] [WHERE ...] [GROUP BY ...] [ORDER BY ...]`.
Se navega por **atributos Java**, no por columnas SQL.

### 3.1 Filtro simple (punto e) — `EstudianteRepository.java:55`
```java
SELECT e FROM Estudiante e WHERE e.genero = :genero ORDER BY e.apellido, e.nombre
```
- `e` alias. `:genero` parámetro → siempre `.setParameter("genero", genero)`.
- Punto d igual pero con `libretaUniversitaria = :lu` y
  `getResultStream().findFirst().orElse(null)` para no lanzar `NoResultException`.

### 3.2 JOIN con doble filtro (punto g) — `EstudianteRepository.java:68`
```java
SELECT e FROM Estudiante e JOIN e.carreras ec
WHERE ec.carrera.id = :idCarrera AND e.ciudad = :ciudad
```
Lectura: parto de `Estudiante e` → entro a su colección `e.carreras` (alias `ec`)
→ filtro por el otro extremo (`ec.carrera.id`) y por campo propio (`e.ciudad`).
Variante partiendo de carrera: `FROM Carrera c JOIN c.inscriptos ec ...`.

### 3.3 Agregación con DTO (punto f) — `EstudianteRepository.java:62`
```java
SELECT NEW tp2.dto.CarreraInscriptosDTO(c.nombre, COUNT(ec))
FROM Carrera c JOIN c.inscriptos ec
GROUP BY c.id, c.nombre
ORDER BY COUNT(ec) DESC
```
- `COUNT(ec)` = filas del grupo. `GROUP BY` = cómo agrupo (por carrera).
- `SELECT NEW` mete el resultado en el record. Sin `NEW` vuelve `Object[]` (resta puntos).

### 3.4 Agregación con condición (reporte 3) — `CarreraRepository.java`
```java
SELECT NEW tp2.dto.ReporteCarreraDTO(c.nombre, ec.inscripcion, COUNT(ec),
  SUM(CASE WHEN ec.graduacion IS NOT NULL THEN 1 ELSE 0 END))
FROM EstudianteCarrera ec JOIN ec.carrera c
GROUP BY c.nombre, ec.inscripcion
ORDER BY c.nombre ASC, ec.inscripcion ASC
```
- Mnemotecnia: `COUNT` = cuántos hay; `SUM(CASE WHEN ...)` = cuántos cumplen algo.
- Supuesto documentado: "año" = inscripción; egresados = de esos, graduados no nulos.

## 4. LAZY vs EAGER (pregunta fija)

- `LAZY` (nuestro `@ManyToOne`): trae el asociado solo al accederlo. Listados livianos.
- `EAGER`: siempre con JOIN. Cómodo si siempre lo necesitás, pero puede traer
  todo el grafo (N+1 / memoria).
- Respuesta modelo: en listados usar LAZY + `JOIN FETCH` puntual cuando se sabe
  que se va a navegar la relación.

## 5. Singleton + transacciones (el otro punto del 7)

```java
// Escritura: siempre con rollback
try { em.getTransaction().begin(); ...; em.getTransaction().commit(); }
catch (RuntimeException ex) {
    if (em.getTransaction().isActive()) em.getTransaction().rollback();
    throw ex;
}
// Lectura JPQL: sin transacción alcanza.
```
- `EntityManagerFactory` = caro (metamodelo + pool) → **uno solo** (singleton,
  `volatile` + doble chequeo + constructor privado, igual que
  `MySQLConnectionManager` del TP1).
- `EntityManager` = barato → uno por hilo/operación, se cierra siempre.

## 6. Datos sucios del CSV (defender en el escrito)

- `estudianteCarrera.csv` trae `id_carrera` inexistentes (15, 11, 9…) y
  `graduacion = 0` (= no graduado). Se saltean con `[WARN]` y `0 → null`.
- No se borra ese manejo: es decisión de diseño, no bug.

## 7. Compilación (Maven)

- `mvn` de sistema instalado por brew. **Ojo:** compilar con **JDK 21**:
```bash
export JAVA_HOME=/Users/Franco/Library/Java/JavaVirtualMachines/temurin-21.0.12.1/Contents/Home
cd TP2 && mvn compile
```
- Con Java 26/27 Lombok no genera getters (`cannot find symbol getCarreras()`).
- En IntelliJ ya apunta a 21, por eso el TP1 andaba desde el IDE.

## 8. Checklist de entrega (para el 10)

1. [ ] `mvn compile` en verde con JDK 21.
2. [ ] Consultas a–g + reporte corriendo en `Main` contra Derby.
3. [ ] Informes devuelven DTO (`SELECT NEW`), no `Object[]` ni entidades.
4. [ ] Escrituras con rollback; `EM` y factories cerrados.
5. [ ] Sin `target/`, `derbyDB/`, `.DS_Store` en el commit (ver `.gitignore` raíz).

# TP3 - Ejercicio Integrador

API REST de estudiantes y carreras hecha con Spring Boot + JPA.

Correr en: http://localhost:8080 (con la app levantada: `mvn spring-boot:run` o Run de `tp3.Tp3Application`).

## Endpoints

### Estudiantes

- [(a) POST - Alta de estudiante](http://localhost:8080/api/estudiantes)
- [(b) POST - Matricular estudiante en carrera](http://localhost:8080/api/estudiantes/1/matricular/1)
- [(c) GET - Listar estudiantes](http://localhost:8080/api/estudiantes)
- [(c) GET - Listar estudiantes ordenados por apellido](http://localhost:8080/api/estudiantes?orden=apellido)
- [(c) GET - Listar estudiantes ordenados por libreta](http://localhost:8080/api/estudiantes?orden=libreta)
- [(c) GET - Listar estudiantes ordenados por id](http://localhost:8080/api/estudiantes?orden=id)
- [(d) GET - Estudiante por libreta](http://localhost:8080/api/estudiantes/libreta/1001)
- [(e) GET - Estudiantes por genero](http://localhost:8080/api/estudiantes/genero/FEMENINO)

### Carreras

- [(f) GET - Carreras con inscriptos](http://localhost:8080/api/carreras/inscriptos)
- [(g) GET - Estudiantes de una carrera por ciudad](http://localhost:8080/api/carreras/1/estudiantes?ciudad=Tandil)

### Reportes

- [(h) GET - Reporte de carreras (inscriptos y egresados por anio)](http://localhost:8080/api/reportes/carreras)

## Notas

- Los links de **GET** se abren directo en el navegador y devuelven el JSON.
- Los de **POST** (a y b) necesitan un cuerpo (body); probalos con Postman o `curl`.

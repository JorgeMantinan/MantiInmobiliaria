---
name: testing
description: Use when escribiendo o revisando tests: pirámide, JUnit 6 con Testcontainers, MockMvc, ArchUnit, JaCoCo, Vitest/RTL, Playwright, nomenclatura y comandos de ejecución.
---

# Testing en este proyecto

## Reglas

1. **Pirámide y alcance.**
   - Backend: unidades de servicio (sin red), integración de repositorios/services con PostgreSQL real, tests de API con MockMvc, tests de seguridad, ArchUnit.
   - Frontend: Vitest + React Testing Library (hooks/componentes/validaciones); Playwright sólo para e2e de flujos críticos: login, buscar inmueble, enviar formulario de profesional, swipe.

2. **Backend: JUnit 6 + starters de Spring Boot test** (`spring-boot-starter-test`, `spring-boot-starter-webmvc-test`, etc.). Mockito SOLO para unidades de aislamiento; no mockear lo que no sea externo real (no mockear repositorios internos ni clases del dominio).

3. **Testcontainers PostgreSQL con `@ServiceConnection`** para tests de integración de repositorios y services (la H2 nunca: no es PostgreSQL).

```java
@SpringBootTest
class InmuebleRepositoryIT {
  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:17");

  @Test
  void buscar_porPrecioMax_devuelveSóloInmueblesDebajoDelMaximo() { ... }
}
```

4. **Tests de API con MockMvc** (estado, cuerpo, cabeceras) y **tests de seguridad**: quién puede llamar a qué con `@WithMockUser(roles = "ADMIN")` y verificación de 401/403.

```java
@Test @WithMockUser(roles = "ADMIN")
void crearInmueble_comoAdmin_devuelve201() throws Exception {
  mockMvc.perform(post("/api/v1/inmuebles").contentType(APPLICATION_JSON).content(body))
         .andExpect(status().isCreated()).andExpect(header().exists("Location"));
}

@Test
void listar_inmuebles_sinToken_devuelve401() throws Exception {
  mockMvc.perform(get("/api/v1/inmuebles")).andExpect(status().isUnauthorized());
}
```

5. **ArchUnit como test de arquitectura** (reglas de capa y de módulo, ver skill estructura-spring-boot). Las reglas fallan el build igual que cualquier test.

6. **Coverage: JaCoCo mínimo 80% líneas globales en backend.** Regla dura: código nuevo sin test NO entra en `develop`. No perseguir el % con tests triviales (getters, `equals`) que sólo suben la métrica.

7. **Frontend: Vitest + RTL.** Qué testear: interacciones (click/submit → efecto), estados (loading/error/vacío), validaciones de formularios y lógica de hooks (`useApi`). No snapshot-tests de árboles grandes ni tests de estilos Tailwind.

```tsx
it('muestra error cuando la API devuelve 500', async () => {
  server.use(http.get('/api/v1/inmuebles', () => HttpResponse.json({ title: 'Error' }, { status: 500 })));
  render(<InmueblesList />);
  expect(await screen.findByText(/error/i)).toBeInTheDocument();
});
```

8. **Playwright e2e** sólo para los 4 flujos críticos; usar datos de prueba propios y `webServer` apuntando al entorno de test.

9. **Nomenclatura.** `metodoCondicionResultado` o Given-When-Then en español descriptivo: `buscarInmuebles_porPrecio_devuelveSoloDebajoDelMaximo`, `login_conPasswordInvalida_devuelve401`.

10. **Higiene.** Tests independientes (sin orden), sin datos compartidos, aislados por transacción/rollback o esquema por test; unidades < 5s. Prohibido: `Thread.sleep`, tests contra red real, tests comentados o `@Disabled` sin issue.

11. **Cómo ejecutar.**

```bash
./mvnw test                              # backend, todo
./mvnw test -Dtest=InmuebleServiceIT     # un test/clase
./mvnw verify                             # + JaCoCo
npm test                                  # frontend (Vitest)
npm run test:watch
npx playwright test                       # e2e
npx playwright test --grep "login"        # e2e concreto
```

### Checklist antes de PR

- [ ] Código nuevo con tests (unidad + integración API donde aplique); sin ellos no pasa a develop
- [ ] Testcontainers/`@ServiceConnection` para BD, sin H2 ni red real
- [ ] Tests de seguridad (401/403 con `@WithMockUser`) en endpoints con auth
- [ ] ArchUnit verde
- [ ] JaCoCo global ≥ 80% líneas en backend; sin tests triviales
- [ ] Vitest+RTL para componentes/hooks nuevos; Playwright sólo en flujos críticos
- [ ] Nomenclatura descriptiva; sin `Thread.sleep`, sin tests comentados, <5s por unidad
- [ ] `./mvnw test` y `npm test` en verde localmente

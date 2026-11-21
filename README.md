# Arquitectura de software (monolítica y modular) y consumo energético

**Universidad Pedagógica y Tecnológica de Colombia (UPTC) · Facultad de Ingeniería · Ingeniería de Sistemas y Computación**
Documento elaborado en el marco del curso *Metodología de la Investigación* · Tunja, Colombia · 2026

Este repositorio contiene el documento de investigación (LaTeX, formato NTC 1486 con referencias IEEE) y el material del experimento que lo sustenta: banco de pruebas, scripts de ejecución y análisis, datos medidos y estadísticos.

**Autores:** Barrera Romero Juan Diego · Castro Gómez Lui Miguel · Corredor Silva Santiago · Galeano Tirado Juan Emmanuel

```bash
git clone https://github.com/santiagoCS02/equipo-07-proyecto.git
```

## Resumen de la investigación

**Pregunta:** ¿cómo cambia el consumo energético entre una arquitectura monolítica tradicional y una de monolito modular, bajo condiciones y cargas de trabajo similares?

**Hipótesis:** H0: µT = µM (no hay diferencia) frente a H1: µT ≠ µM.

**Diseño:** experimento factorial 2×2 en bloques completos aleatorizados. Los factores son la arquitectura (tradicional o modular) y la carga (normal, con 1 hilo, o pesada, con 2 hilos). Se hicieron 12 repeticiones por condición: 48 corridas medidas y 12 mediciones del equipo en reposo. Ambas arquitecturas se implementaron en Java dentro de un mismo banco de pruebas (una tienda con catálogo, inventario, clientes, pedidos y pagos), con la misma lógica de negocio, los mismos datos y la misma secuencia de operaciones; una verificación automática comprueba que ambas producen el mismo resultado. La energía del procesador y de la memoria se mide con contadores RAPL, en un único portátil (Intel Celeron N4020, 2 núcleos, 3.4 GiB de RAM, Arch Linux).

### Resultado principal

A trabajo fijo, el monolito modular consumió más energía por operación en ambas cargas. La diferencia se explica por el mayor tiempo de ejecución y no por una mayor potencia.

| Carga | Métrica | Tradicional | Modular | Diferencia | IC 95 % |
|---|---|---|---|---|---|
| Normal (1 hilo) | Energía por operación (µJ/op) | 3.371 | 3.527 | +4.6 % | 4.58 – 4.67 % |
| Normal (1 hilo) | Tiempo (s) | 18.60 | 19.40 | +4.3 % | 4.26 – 4.34 % |
| Normal (1 hilo) | Potencia media (W) | 3.332 | 3.343 | +0.3 % | 0.29 – 0.33 % |
| Pesada (2 hilos) | Energía por operación (µJ/op) | 2.017 | 2.143 | +6.2 % | 5.78 – 6.66 % |
| Pesada (2 hilos) | Tiempo (s) | 19.35 | 20.55 | +6.2 % | 5.75 – 6.64 % |
| Pesada (2 hilos) | Potencia media (W) | 3.835 | 3.836 | +0.02 % | 0.005 – 0.039 % |

Los intervalos de confianza de la energía por operación no incluyen el cero, por lo que se rechaza H0 en ambas cargas. En energía neta (sin el consumo en reposo) la diferencia es de +5.1 % con carga normal y +6.2 % con carga pesada. La energía por operación es menor con dos hilos que con uno en ambas arquitecturas; es un efecto de la carga y no de la arquitectura.

> Los resultados se limitan a este banco de pruebas y a este equipo. No implican que una arquitectura sea mejor que otra. El detalle y las limitaciones están en los capítulos 9 y 10 del documento.

## Estructura del repositorio

```
equipo-07-proyecto/
├── main.tex                  # documento principal: portada, índices, capítulos y bibliografía
├── main.pdf                  # versión compilada (61 páginas)
├── referencias.bib           # bibliografía (35 entradas)
├── texstyles/
│   └── IEEEtran.bst          # estilo bibliográfico IEEE
├── capitulos/                # un archivo .tex por capítulo (ver tabla siguiente)
├── figuras/                  # figuras del capítulo 9
│   ├── fig_energia_por_operacion.png
│   ├── fig_tiempo.png
│   ├── fig_potencia_media.png
│   ├── fig_estabilidad_bloques.png
│   └── fig_diferencia_relativa.png
├── anexos/                   # material complementario del experimento
│   ├── Bench.java            # banco de pruebas con las dos arquitecturas
│   ├── correr.sh             # ejecución por bloques aleatorizados
│   ├── ejecutar.txt          # órdenes usadas para lanzar la corrida de 12 bloques
│   ├── analisis.py           # estadística y generación de gráficas
│   ├── corrida.log           # registro de la corrida real
│   └── resultados/
│       ├── datos.csv               # una fila por medición (48 corridas + 12 en reposo)
│       └── resumen_estadistico.csv # estadísticos por carga y métrica
├── .gitignore
└── README.md
```

### Capítulos del documento

| Archivo (`capitulos/`) | Contenido |
|---|---|
| `resumen.tex` | Resumen y Abstract |
| `introduccion.tex` | Introducción |
| `planteamiento_problema.tex` | 1. Planteamiento del problema: antecedentes (con la búsqueda PRISMA y la tabla de antecedentes), situación problemática, formulación, consecuencias y delimitación |
| `justificacion.tex` | 2. Justificación |
| `objetivos.tex` | 3. Objetivos |
| `alcance_investigacion.tex` | 4. Alcance de la investigación |
| `formulacion_hipotesis.tex` | 5. Formulación de hipótesis |
| `variables.tex` | 6. Variables |
| `marco_referencial.tex` | 7. Marco referencial (teórico, conceptual, estado del arte, histórico, normativo y tecnológico) |
| `diseno_metodologico.tex` | 8. Diseño metodológico: banco de pruebas, operacionalización, instrumentos, procedimiento y análisis estadístico |
| `resultados_discusion.tex` | 9. Resultados y discusión (incluye las limitaciones) |
| `conclusiones_recomendaciones.tex` | 10. Conclusiones y recomendaciones |
| `anexos.tex` | Anexo A (material complementario) y Anexo B (reproducción) |

## Compilación del documento

```bash
pdflatex main.tex
bibtex main
pdflatex main.tex
pdflatex main.tex
```

Se recomienda una instalación completa de TeX Live (u Overleaf) para contar con el paquete de idioma español; si no está disponible, `main.tex` usa un respaldo en inglés con los títulos traducidos, pero la separación de palabras no será la del español.

## Reproducción del experimento

### Requisitos

- Linux con contadores RAPL de Intel (debe existir `/sys/class/powercap/intel-rapl:0`) y permisos de `sudo`.
- JDK 17 o superior (se necesita `javac`; el código usa *records* y *switch* con flechas).
- Python 3 con `numpy`, `pandas`, `scipy` y `matplotlib`.
- `shuf` (incluido en GNU coreutils).

### Ejecución

Desde la carpeta `anexos/`:

```bash
chmod +x correr.sh
sudo -v
REPS=12 systemd-inhibit --what=sleep:idle:handle-lid-switch ./correr.sh 2>&1 | tee corrida.log
python3 analisis.py resultados/datos.csv
```

`correr.sh` realiza, en orden:

1. Compila `Bench.java`.
2. Da permiso de lectura a los contadores RAPL (se pierde al reiniciar).
3. Verifica la equivalencia funcional de ambas arquitecturas.
4. Fija el gobernador de CPU en *performance*, desactiva el turbo y calibra el número de operaciones para que la arquitectura más lenta tarde unos 20 s con un hilo.
5. Mide `REPS` bloques; cada uno contiene una medición en reposo de 15 s y las cuatro condiciones en orden aleatorio, con JVM nueva, calentamiento sin medir y 5 s de pausa entre corridas.

Con 12 bloques la ejecución dura unos 35 minutos en el equipo usado. Las variables `REPS`, `IDLE_SECS`, `COOLDOWN`, `OPS` y `OUT` se pueden ajustar al lanzar el script (`REPS` vale 8 si no se indica).

Los valores absolutos dependen del equipo; en otro hardware se esperan cifras distintas.

### Salidas del análisis

`analisis.py` escribe en la misma carpeta que el CSV: `resumen_estadistico.csv` y las imágenes `fig1_energia_por_operacion.png`, `fig2_tiempo.png`, `fig3_potencia.png`, `fig4_estabilidad.png`, `fig5_diferencia_relativa.png` y `fig_resumen.png` (panel 2×2). Las cinco figuras del documento en `figuras/` corresponden a estas, con otros nombres:

| Genera `analisis.py` | En `figuras/` |
|---|---|
| `fig1_energia_por_operacion.png` | `fig_energia_por_operacion.png` |
| `fig2_tiempo.png` | `fig_tiempo.png` |
| `fig3_potencia.png` | `fig_potencia_media.png` |
| `fig4_estabilidad.png` | `fig_estabilidad_bloques.png` |
| `fig5_diferencia_relativa.png` | `fig_diferencia_relativa.png` |

## Datos

**`datos.csv`** tiene 60 filas: 48 corridas medidas y 12 mediciones en reposo.

| Columna | Descripción |
|---|---|
| `bloque` | Número de bloque (1 a 12) |
| `arquitectura` | `trad` (tradicional), `mod` (modular) o `idle` (reposo) |
| `carga` | `normal` (1 hilo), `pesada` (2 hilos) o `-` en reposo |
| `hilos` | Hilos de ejecución |
| `ops_totales` | Operaciones realizadas en la ventana medida |
| `seg` | Duración de la ventana (s) |
| `pkg_J`, `dram_J` | Energía del paquete CPU y de la DRAM en la ventana (J) |
| `temp_C` | Temperatura del paquete al terminar (°C); se mantuvo entre 33 y 36 °C |
| `checksum` | Estado final del sistema: con 1 hilo es idéntico entre arquitecturas; con 2 hilos varía entre corridas por el orden de los pedidos |

**`resumen_estadistico.csv`** tiene una fila por carga y métrica (`uJ_op`, `uJ_op_neto`, `seg`, `W_medio`) con las medias y desviaciones de cada arquitectura, la diferencia relativa del modular respecto al tradicional (`dif_pct`) y su IC 95 % de Welch, la prueba de Shapiro-Wilk, los *p*-valores (Welch, Mann-Whitney y pareada por bloque) y la *d* de Cohen.
